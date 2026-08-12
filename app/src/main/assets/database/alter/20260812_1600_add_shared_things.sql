CREATE TABLE IF NOT EXISTS shared_thing (
    kind  TEXT NOT NULL DEFAULT '',
    uuid  TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (kind, uuid)
);
