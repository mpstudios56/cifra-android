-- An identifier that means the same thing on both phones.
--
-- Two phones both create their transaction number 57, and nothing about the
-- number says whether they are one movement or two. So every row that can be
-- exchanged gets an identifier made where it was created and never reused.
--
-- Filled in for the rows already here with randomblob, which is as good a
-- random identifier as SQLite can make on its own. The index is deliberately
-- not unique: every path that inserts a row would otherwise have to be taught
-- to fill this in, and one that forgot would fail at the worst moment. Blanks
-- are filled in before a synchronisation instead.

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
