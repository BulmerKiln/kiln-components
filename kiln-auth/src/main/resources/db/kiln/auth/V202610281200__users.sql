-- A person, instance-wide. A user is created only by an invitation, an allowed domain or the
-- bootstrap config, never by a bare sign-in; membership of an organization is in members.
CREATE TABLE users (
    id           uuid        PRIMARY KEY,
    display_name text        NOT NULL,
    status       text        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    disabled_at  timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now()
);

-- One sign-in (issuer and subject, through a connector) belonging to one user, wherever it signs
-- in. claimed_email is the verified email that created the user through an allowed domain or a
-- bootstrap email: each claims one user, ever, and is never matched again afterwards.
CREATE TABLE user_identities (
    id            uuid        PRIMARY KEY,
    user_id       uuid        NOT NULL REFERENCES users (id),
    connector_id  text        NOT NULL,
    issuer        text        NOT NULL,
    subject       text        NOT NULL,
    claimed_email text,
    last_login_at timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (issuer, subject)
);
CREATE INDEX user_identities_user ON user_identities (user_id);
CREATE UNIQUE INDEX user_identities_claimed_email ON user_identities (lower(claimed_email))
    WHERE claimed_email IS NOT NULL;

-- Instance admins: a table of grants rather than a flag on users, so nothing that edits a user
-- can grant it. bootstrap rows follow the config; admin rows were granted by another admin.
CREATE TABLE instance_admins (
    user_id    uuid        PRIMARY KEY REFERENCES users (id),
    source     text        NOT NULL CHECK (source IN ('bootstrap', 'admin')),
    granted_by uuid        REFERENCES users (id),
    granted_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((source = 'admin') = (granted_by IS NOT NULL))
);

-- The runtime role changes only what the application legitimately changes, so even a bug or an
-- injection running as kiln_app can't move an identity to another user or turn a grant into
-- another kind. Inserts and deletes stay with the default privileges.
REVOKE UPDATE ON users, user_identities, instance_admins FROM kiln_app;
GRANT UPDATE (display_name, status, disabled_at) ON users TO kiln_app;
GRANT UPDATE (last_login_at) ON user_identities TO kiln_app;
