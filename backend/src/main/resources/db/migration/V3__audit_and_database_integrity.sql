ALTER TABLE ledger_transactions ADD COLUMN result_account_id UUID REFERENCES bank_accounts(id);
ALTER TABLE ledger_transactions ADD COLUMN result_balance_minor BIGINT;

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_username VARCHAR(80) REFERENCES app_users(username) ON DELETE SET NULL,
    action_type VARCHAR(80) NOT NULL,
    resource_type VARCHAR(40),
    resource_id VARCHAR(100),
    outcome VARCHAR(16) NOT NULL,
    amount_minor BIGINT,
    reason VARCHAR(500),
    request_method VARCHAR(10),
    request_path VARCHAR(255),
    details VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_audit_outcome CHECK (outcome IN ('SUCCESS', 'REJECTED'))
);

ALTER TABLE bank_accounts
    ADD CONSTRAINT chk_account_balance_safe_integer
    CHECK (balance_minor <= 9007199254740991);
ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_entry_safe_amount
    CHECK (amount_minor BETWEEN -9007199254740991 AND 9007199254740991);
ALTER TABLE ledger_transactions
    ADD CONSTRAINT chk_result_balance_safe_integer
    CHECK (result_balance_minor IS NULL OR result_balance_minor BETWEEN 0 AND 9007199254740991);

CREATE INDEX idx_audit_events_occurred_at ON audit_events(occurred_at DESC);
CREATE INDEX idx_audit_events_actor ON audit_events(actor_username, occurred_at DESC);

CREATE FUNCTION assert_ledger_transaction_balanced() RETURNS trigger AS $$
DECLARE
    target_id UUID;
    posting_count BIGINT;
    posting_sum NUMERIC;
    tx_type VARCHAR(16);
    tx_actor VARCHAR(80);
    customer_count BIGINT;
    system_count BIGINT;
    customer_debits BIGINT;
    customer_credits BIGINT;
    system_debits BIGINT;
    system_credits BIGINT;
    invalid_debits BIGINT;
    invalid_system_accounts BIGINT;
    currency_count BIGINT;
    account_count BIGINT;
    inactive_accounts BIGINT;
    expected_result_account UUID;
    actual_result_balance BIGINT;
    stored_result_account UUID;
    stored_result_balance BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'ledger_transactions' THEN target_id := NEW.id;
    ELSE target_id := NEW.transaction_id; END IF;

    SELECT type, created_by INTO tx_type, tx_actor
      FROM ledger_transactions WHERE id = target_id;
    SELECT COUNT(*), COALESCE(SUM(amount_minor), 0),
           COUNT(*) FILTER (WHERE account_id IS NOT NULL),
           COUNT(*) FILTER (WHERE system_account_code IS NOT NULL),
           COUNT(*) FILTER (WHERE account_id IS NOT NULL AND amount_minor < 0),
           COUNT(*) FILTER (WHERE account_id IS NOT NULL AND amount_minor > 0),
           COUNT(*) FILTER (WHERE system_account_code IS NOT NULL AND amount_minor < 0),
           COUNT(*) FILTER (WHERE system_account_code IS NOT NULL AND amount_minor > 0)
      INTO posting_count, posting_sum, customer_count, system_count,
           customer_debits, customer_credits, system_debits, system_credits
      FROM ledger_entries WHERE transaction_id = target_id;

    IF posting_count <> 2 OR posting_sum <> 0 THEN
        RAISE EXCEPTION 'Ledger transaction % must contain exactly two balanced postings (count %, sum %)',
            target_id, posting_count, posting_sum USING ERRCODE = '23514';
    END IF;

    SELECT COUNT(*) INTO inactive_accounts
      FROM ledger_entries le JOIN bank_accounts ba ON ba.id = le.account_id
     WHERE le.transaction_id = target_id AND ba.status <> 'ACTIVE';
    IF inactive_accounts <> 0 THEN
        RAISE EXCEPTION 'Ledger transaction % references an inactive account', target_id
            USING ERRCODE = '23514';
    END IF;

    SELECT result_account_id, result_balance_minor
      INTO stored_result_account, stored_result_balance
      FROM ledger_transactions WHERE id = target_id;
    SELECT account_id INTO expected_result_account
      FROM ledger_entries
     WHERE transaction_id = target_id AND account_id IS NOT NULL
       AND ((tx_type = 'DEPOSIT' AND amount_minor > 0)
         OR (tx_type IN ('WITHDRAWAL', 'TRANSFER') AND amount_minor < 0));
    IF stored_result_account IS NULL OR stored_result_balance IS NULL
       OR stored_result_account IS DISTINCT FROM expected_result_account THEN
        RAISE EXCEPTION 'Ledger transaction % has an invalid result snapshot', target_id
            USING ERRCODE = '23514';
    END IF;
    SELECT balance_minor INTO actual_result_balance
      FROM bank_accounts WHERE id = stored_result_account;
    IF stored_result_balance IS DISTINCT FROM actual_result_balance THEN
        RAISE EXCEPTION 'Ledger transaction % result balance snapshot does not match its account', target_id
            USING ERRCODE = '23514';
    END IF;

    IF tx_type = 'DEPOSIT' THEN
        IF customer_count <> 1 OR system_count <> 1 OR customer_credits <> 1 OR system_debits <> 1 THEN
            RAISE EXCEPTION 'Deposit % must credit one customer account and debit one system clearing account', target_id
                USING ERRCODE = '23514';
        END IF;
        SELECT COUNT(*) INTO invalid_debits
          FROM ledger_entries le JOIN bank_accounts ba ON ba.id = le.account_id
         WHERE le.transaction_id = target_id AND ba.owner_username <> tx_actor;
        SELECT COUNT(*) INTO invalid_system_accounts
          FROM ledger_entries WHERE transaction_id = target_id
            AND system_account_code IS NOT NULL AND system_account_code <> 'CASH_CLEARING';
        IF invalid_debits + invalid_system_accounts <> 0 THEN
            RAISE EXCEPTION 'Deposit % actor does not own the credited account', target_id USING ERRCODE = '23514';
        END IF;
    ELSIF tx_type = 'WITHDRAWAL' THEN
        IF customer_count <> 1 OR system_count <> 1 OR customer_debits <> 1 OR system_credits <> 1 THEN
            RAISE EXCEPTION 'Withdrawal % must debit one customer account and credit one system clearing account', target_id
                USING ERRCODE = '23514';
        END IF;
        SELECT COUNT(*) INTO invalid_debits
          FROM ledger_entries le JOIN bank_accounts ba ON ba.id = le.account_id
         WHERE le.transaction_id = target_id AND le.amount_minor < 0 AND ba.owner_username <> tx_actor;
        SELECT COUNT(*) INTO invalid_system_accounts
          FROM ledger_entries WHERE transaction_id = target_id
            AND system_account_code IS NOT NULL AND system_account_code <> 'CASH_CLEARING';
        IF invalid_debits + invalid_system_accounts <> 0 THEN
            RAISE EXCEPTION 'Withdrawal % actor does not own the debited account', target_id USING ERRCODE = '23514';
        END IF;
    ELSIF tx_type = 'TRANSFER' THEN
        IF customer_count <> 2 OR system_count <> 0 OR customer_debits <> 1 OR customer_credits <> 1 THEN
            RAISE EXCEPTION 'Transfer % must debit one customer account and credit one customer account', target_id
                USING ERRCODE = '23514';
        END IF;
        SELECT COUNT(DISTINCT ba.currency), COUNT(DISTINCT le.account_id)
          INTO currency_count, account_count
          FROM ledger_entries le JOIN bank_accounts ba ON ba.id = le.account_id
         WHERE le.transaction_id = target_id;
        IF currency_count <> 1 OR account_count <> 2 THEN
            RAISE EXCEPTION 'Transfer % cannot cross currencies', target_id USING ERRCODE = '23514';
        END IF;
        SELECT COUNT(*) INTO invalid_debits
          FROM ledger_entries le JOIN bank_accounts ba ON ba.id = le.account_id
         WHERE le.transaction_id = target_id AND le.amount_minor < 0 AND ba.owner_username <> tx_actor;
        IF invalid_debits <> 0 THEN
            RAISE EXCEPTION 'Transfer % actor does not own the source account', target_id USING ERRCODE = '23514';
        END IF;
    ELSE
        RAISE EXCEPTION 'Unsupported ledger transaction type %', tx_type USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_transaction_must_balance
AFTER INSERT ON ledger_transactions DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_ledger_transaction_balanced();
CREATE CONSTRAINT TRIGGER trg_entries_must_balance
AFTER INSERT ON ledger_entries DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_ledger_transaction_balanced();

CREATE FUNCTION assert_bank_account_balance_matches_ledger() RETURNS trigger AS $$
DECLARE target_id UUID; stored_balance BIGINT; ledger_balance NUMERIC;
BEGIN
    IF TG_TABLE_NAME = 'bank_accounts' THEN target_id := NEW.id;
    ELSE target_id := NEW.account_id; END IF;
    IF target_id IS NULL THEN RETURN NULL; END IF;
    SELECT balance_minor INTO stored_balance FROM bank_accounts WHERE id = target_id;
    SELECT COALESCE(SUM(amount_minor), 0) INTO ledger_balance FROM ledger_entries WHERE account_id = target_id;
    IF stored_balance IS DISTINCT FROM ledger_balance THEN
        RAISE EXCEPTION 'Account % balance % does not reconcile with ledger %',
            target_id, stored_balance, ledger_balance USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_account_insert_reconciles
AFTER INSERT ON bank_accounts DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_bank_account_balance_matches_ledger();

CREATE CONSTRAINT TRIGGER trg_account_balance_reconciles
AFTER UPDATE OF balance_minor ON bank_accounts DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_bank_account_balance_matches_ledger();
CREATE CONSTRAINT TRIGGER trg_account_posting_reconciles
AFTER INSERT ON ledger_entries DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_bank_account_balance_matches_ledger();

CREATE FUNCTION assert_system_account_balance_matches_ledger() RETURNS trigger AS $$
DECLARE target_code VARCHAR(32); stored_balance BIGINT; ledger_balance NUMERIC;
BEGIN
    IF TG_TABLE_NAME = 'system_ledger_accounts' THEN target_code := NEW.code;
    ELSE target_code := NEW.system_account_code; END IF;
    IF target_code IS NULL THEN RETURN NULL; END IF;
    SELECT balance_minor INTO stored_balance FROM system_ledger_accounts WHERE code = target_code;
    SELECT COALESCE(SUM(amount_minor), 0) INTO ledger_balance FROM ledger_entries WHERE system_account_code = target_code;
    IF stored_balance IS DISTINCT FROM ledger_balance THEN
        RAISE EXCEPTION 'System account % balance % does not reconcile with ledger %',
            target_code, stored_balance, ledger_balance USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_system_balance_reconciles
AFTER UPDATE OF balance_minor ON system_ledger_accounts DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_system_account_balance_matches_ledger();
CREATE CONSTRAINT TRIGGER trg_system_posting_reconciles
AFTER INSERT ON ledger_entries DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_system_account_balance_matches_ledger();

-- Audit evidence is immutable; corrections are represented by additional events.
CREATE TRIGGER trg_audit_events_immutable
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

-- The application connects with a non-owner role. Only the migrator owns schema objects.
GRANT USAGE ON SCHEMA public TO ledgerline_app;
GRANT SELECT, INSERT ON app_users TO ledgerline_app;
GRANT SELECT, INSERT ON bank_accounts TO ledgerline_app;
GRANT UPDATE (balance_minor) ON bank_accounts TO ledgerline_app;
GRANT SELECT ON system_ledger_accounts TO ledgerline_app;
GRANT UPDATE (balance_minor) ON system_ledger_accounts TO ledgerline_app;
GRANT SELECT, INSERT ON ledger_transactions, ledger_entries, audit_events TO ledgerline_app;
