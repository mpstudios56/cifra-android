CREATE TABLE IF NOT EXISTS change_log (
    _id           INTEGER PRIMARY KEY AUTOINCREMENT,
    change_uuid   TEXT    NOT NULL DEFAULT '',
    device        TEXT    NOT NULL DEFAULT '',
    author        TEXT    NOT NULL DEFAULT '',
    made_on       INTEGER NOT NULL DEFAULT 0,
    entity        TEXT    NOT NULL DEFAULT '',
    entity_id     INTEGER NOT NULL DEFAULT 0,
    operation     TEXT    NOT NULL DEFAULT '',
    title         TEXT    NOT NULL DEFAULT '',
    subtitle      TEXT    NOT NULL DEFAULT '',
    payload       TEXT    NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS change_log_made_on_idx ON change_log (made_on);
CREATE UNIQUE INDEX IF NOT EXISTS change_log_uuid_idx ON change_log (change_uuid);
