ALTER TABLE transactions ADD COLUMN uuid TEXT NOT NULL DEFAULT '';
UPDATE transactions SET uuid = lower(hex(randomblob(16))) WHERE uuid = '';
CREATE INDEX IF NOT EXISTS transactions_uuid_idx ON transactions (uuid);

ALTER TABLE account ADD COLUMN uuid TEXT NOT NULL DEFAULT '';
UPDATE account SET uuid = lower(hex(randomblob(16))) WHERE uuid = '';
CREATE INDEX IF NOT EXISTS account_uuid_idx ON account (uuid);

ALTER TABLE category ADD COLUMN uuid TEXT NOT NULL DEFAULT '';
UPDATE category SET uuid = lower(hex(randomblob(16))) WHERE uuid = '';
CREATE INDEX IF NOT EXISTS category_uuid_idx ON category (uuid);

ALTER TABLE payee ADD COLUMN uuid TEXT NOT NULL DEFAULT '';
UPDATE payee SET uuid = lower(hex(randomblob(16))) WHERE uuid = '';
CREATE INDEX IF NOT EXISTS payee_uuid_idx ON payee (uuid);

ALTER TABLE project ADD COLUMN uuid TEXT NOT NULL DEFAULT '';
UPDATE project SET uuid = lower(hex(randomblob(16))) WHERE uuid = '';
CREATE INDEX IF NOT EXISTS project_uuid_idx ON project (uuid);

ALTER TABLE locations ADD COLUMN uuid TEXT NOT NULL DEFAULT '';
UPDATE locations SET uuid = lower(hex(randomblob(16))) WHERE uuid = '';
CREATE INDEX IF NOT EXISTS locations_uuid_idx ON locations (uuid);

ALTER TABLE currency ADD COLUMN uuid TEXT NOT NULL DEFAULT '';
UPDATE currency SET uuid = lower(hex(randomblob(16))) WHERE uuid = '';
CREATE INDEX IF NOT EXISTS currency_uuid_idx ON currency (uuid);

ALTER TABLE budget ADD COLUMN uuid TEXT NOT NULL DEFAULT '';
UPDATE budget SET uuid = lower(hex(randomblob(16))) WHERE uuid = '';
CREATE INDEX IF NOT EXISTS budget_uuid_idx ON budget (uuid);
