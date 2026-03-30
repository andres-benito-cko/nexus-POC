-- Clear existing entries (POC: no production data to preserve)
TRUNCATE TABLE ledger_entries;

ALTER TABLE ledger_entries
    DROP COLUMN debit_account,
    DROP COLUMN credit_account,
    ADD COLUMN account VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN side    VARCHAR(6)   NOT NULL DEFAULT 'DEBIT';

-- Remove the placeholder defaults now that the columns are added
ALTER TABLE ledger_entries
    ALTER COLUMN account DROP DEFAULT,
    ALTER COLUMN side    DROP DEFAULT;
