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
CREATE INDEX idx_audit_events_occurred_at ON audit_events(occurred_at DESC);
CREATE INDEX idx_audit_events_actor ON audit_events(actor_username, occurred_at DESC);

CREATE FUNCTION assert_ledger_transaction_balanced() RETURNS trigger AS $$
DECLARE target_id UUID; posting_count BIGINT; posting_sum NUMERIC;
BEGIN
    IF TG_TABLE_NAME = 'ledger_transactions' THEN target_id := NEW.id;
    ELSE target_id := NEW.transaction_id; END IF;
    SELECT COUNT(*), COALESCE(SUM(amount_minor), 0) INTO posting_count, posting_sum
      FROM ledger_entries WHERE transaction_id = target_id;
    IF posting_count <> 2 OR posting_sum <> 0 THEN
        RAISE EXCEPTION 'Ledger transaction % must contain exactly two balanced postings (count %, sum %)',
            target_id, posting_count, posting_sum USING ERRCODE = '23514';
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
