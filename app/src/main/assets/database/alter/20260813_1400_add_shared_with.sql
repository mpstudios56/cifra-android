CREATE TABLE IF NOT EXISTS shared_with (
    uuid TEXT NOT NULL DEFAULT '',
    mark TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (uuid, mark)
);
