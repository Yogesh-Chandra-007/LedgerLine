-- Hibernate maps String currency codes as VARCHAR(3); normalize the original CHAR(3) column.
ALTER TABLE bank_accounts
    ALTER COLUMN currency TYPE VARCHAR(3)
    USING BTRIM(currency)::VARCHAR(3);
