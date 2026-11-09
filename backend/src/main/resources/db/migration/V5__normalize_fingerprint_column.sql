-- SHA-256 fingerprints are fixed-length values, but VARCHAR maps cleanly to Hibernate's String type.
ALTER TABLE ledger_transactions
    ALTER COLUMN request_fingerprint TYPE VARCHAR(64)
    USING BTRIM(request_fingerprint)::VARCHAR(64);
