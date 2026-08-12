CREATE TABLE IF NOT EXISTS duplicate_notice (
    _id        INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid_a     TEXT    NOT NULL DEFAULT '',
    uuid_b     TEXT    NOT NULL DEFAULT '',
    noticed_on INTEGER NOT NULL DEFAULT 0,
    settled    INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS duplicate_notice_pair_idx ON duplicate_notice (uuid_a, uuid_b);
