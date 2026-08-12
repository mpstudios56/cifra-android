ALTER TABLE transactions ADD COLUMN created_by TEXT NOT NULL DEFAULT '';
CREATE INDEX IF NOT EXISTS transactions_created_by_idx ON transactions (created_by);
