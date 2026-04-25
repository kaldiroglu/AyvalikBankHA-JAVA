-- Default transfer fee: 1%
INSERT INTO settings (key, value)
VALUES ('TRANSFER_FEE_PERCENT', '1.0')
ON CONFLICT (key) DO NOTHING;

-- Backfill: any pre-existing accounts (created before account types) are CHECKING with no overdraft.
UPDATE accounts
SET type = 'CHECKING', overdraft_limit = 0
WHERE type IS NULL;
