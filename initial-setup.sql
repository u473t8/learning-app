-- -*- mode: sql; sql-product: sqlite; -*-

CREATE TABLE IF NOT EXISTS users
(
    id         INTEGER PRIMARY KEY,
    name       TEXT UNIQUE NOT NULL,
    password   TEXT,
    created_at INTEGER DEFAULT (UNIXEPOCH())
);

CREATE TABLE IF NOT EXISTS user_settings
(
    user_id  INTEGER PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    settings BLOB DEFAULT '{}'
);


CREATE TABLE IF NOT EXISTS sessions
(
    id         INTEGER PRIMARY KEY,
    user_id    INTEGER GENERATED ALWAYS AS (value ->> '$."user-id"') STORED REFERENCES users (id) ON DELETE CASCADE,
    token      TEXT UNIQUE NOT NULL,
    value      BLOB,
    created_at INTEGER DEFAULT (UNIXEPOCH())
);


CREATE TABLE IF NOT EXISTS words
(
    id          INTEGER PRIMARY KEY,
    user_id     INTEGER REFERENCES users (id),
    word        TEXT NOT NULL,
    translation TEXT NOT NULL,
    created_at  INTEGER DEFAULT (UNIXEPOCH()),
    modified_at INTEGER
);


CREATE TABLE IF NOT EXISTS reviews
(
    id          INTEGER PRIMARY KEY,
    word_id     INTEGER REFERENCES words (id),
    retained    INTEGER NOT NULL,
    reviewed_at INTEGER NOT NULL
);
