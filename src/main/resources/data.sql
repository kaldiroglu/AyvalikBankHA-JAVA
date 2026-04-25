-- Default transfer fee: 1%
INSERT INTO settings (key, value)
VALUES ('TRANSFER_FEE_PERCENT', '1.0')
ON CONFLICT (key) DO NOTHING;

-- Schema migration: add NOT NULL columns that Hibernate's ddl-auto=update
-- can't add to populated tables. ADD COLUMN with DEFAULT atomically backfills
-- existing rows and applies the NOT NULL constraint going forward.
-- (No-op if the column already exists.)
ALTER TABLE accounts  ADD COLUMN IF NOT EXISTS type            varchar(16)   NOT NULL DEFAULT 'CHECKING';
ALTER TABLE accounts  ADD COLUMN IF NOT EXISTS overdraft_limit numeric(19,2);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS tier            varchar(16)   NOT NULL DEFAULT 'STANDARD';

-- Belt-and-braces backfill for any rows where the columns existed but were left NULL
-- (e.g. inserted by an older app build before the DEFAULT was in place).
UPDATE accounts  SET type            = 'CHECKING' WHERE type            IS NULL;
UPDATE accounts  SET overdraft_limit = 0          WHERE type = 'CHECKING' AND overdraft_limit IS NULL;
UPDATE customers SET tier            = 'STANDARD' WHERE tier            IS NULL;
