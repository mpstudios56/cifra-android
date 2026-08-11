CREATE TABLE IF NOT EXISTS trash (
    _id           INTEGER PRIMARY KEY AUTOINCREMENT,
    entity        TEXT    NOT NULL,
    entity_id     INTEGER NOT NULL DEFAULT 0,
    title         TEXT    NOT NULL DEFAULT '',
    subtitle      TEXT    NOT NULL DEFAULT '',
    payload       TEXT    NOT NULL DEFAULT '',
    deleted_on    INTEGER NOT NULL DEFAULT 0,
    author        TEXT    NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS trash_deleted_on_idx ON trash (deleted_on);
