CREATE TABLE bank_accounts (
    id UUID PRIMARY KEY,
    account_number VARCHAR(24) NOT NULL UNIQUE,
    owner_username VARCHAR(80) NOT NULL REFERENCES app_users(username),
    currency CHAR(3) NOT NULL,
    balance_minor BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_account_balance_nonnegative CHECK (balance_minor >= 0),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT chk_account_currency CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX idx_bank_accounts_owner ON bank_accounts(owner_username, created_at DESC);

CREATE TABLE system_ledger_accounts (
    code VARCHAR(32) PRIMARY KEY,
    description VARCHAR(80) NOT NULL,
    balance_minor BIGINT NOT NULL DEFAULT 0
);
INSERT INTO system_ledger_accounts(code, description) VALUES
    ('CASH_CLEARING', 'External cash clearing counter-account');

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    request_fingerprint CHAR(64) NOT NULL,
    type VARCHAR(16) NOT NULL,
    created_by VARCHAR(80) NOT NULL REFERENCES app_users(username),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ledger_transaction_type CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER'))
);
CREATE INDEX idx_ledger_transactions_created_at ON ledger_transactions(created_at DESC);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES ledger_transactions(id),
    account_id UUID REFERENCES bank_accounts(id),
    system_account_code VARCHAR(32) REFERENCES system_ledger_accounts(code),
    amount_minor BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ledger_entry_nonzero CHECK (amount_minor <> 0),
    CONSTRAINT chk_ledger_entry_exactly_one_account
        CHECK ((account_id IS NULL) <> (system_account_code IS NULL))
);
CREATE INDEX idx_ledger_entries_account_time ON ledger_entries(account_id, created_at DESC);
CREATE INDEX idx_ledger_entries_transaction ON ledger_entries(transaction_id);

-- Ledger history is append-only. Corrections must be represented by compensating entries.
CREATE FUNCTION reject_ledger_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Ledger records are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_transactions_immutable
BEFORE UPDATE OR DELETE ON ledger_transactions
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

CREATE TRIGGER trg_ledger_entries_immutable
BEFORE UPDATE OR DELETE ON ledger_entries
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();
