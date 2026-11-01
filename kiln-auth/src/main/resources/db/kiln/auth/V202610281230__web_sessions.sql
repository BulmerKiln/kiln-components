-- A browser session of one user. Only the SHA-256 of the session ID is kept, so a copy of this
-- table can't be replayed as cookies. expires_at is absolute: 12 hours from sign-in.
CREATE TABLE web_sessions (
    id_hash    bytea       PRIMARY KEY CHECK (length(id_hash) = 32),
    user_id    uuid        NOT NULL REFERENCES users (id),
    auth_time  timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL
);
CREATE INDEX web_sessions_user ON web_sessions (user_id);
CREATE INDEX web_sessions_expires ON web_sessions (expires_at);

-- A session is created and deleted, never changed: it can't be moved to another user or extended.
REVOKE UPDATE ON web_sessions FROM kiln_app;
