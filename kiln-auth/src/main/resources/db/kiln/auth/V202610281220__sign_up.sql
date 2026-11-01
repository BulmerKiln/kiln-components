-- How people who aren't users yet get in. Both are instance tables: they're read at sign-in,
-- before any organization is known.

-- An invite link: only the SHA-256 of its token is kept. The email is the admin's label for who
-- it's for; holding the link is the proof. organization_id, when set, is joined on claim.
CREATE TABLE invitations (
    id              uuid        PRIMARY KEY,
    token_hash      bytea       NOT NULL UNIQUE CHECK (length(token_hash) = 32),
    email           text        NOT NULL,
    organization_id uuid        REFERENCES organizations (id),
    created_by      uuid        NOT NULL REFERENCES users (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL,
    claimed_by      uuid        REFERENCES users (id),
    claimed_at      timestamptz,
    CHECK ((claimed_by IS NULL) = (claimed_at IS NULL))
);

-- A verified email at this domain, signing in through this connector, creates a user who joins
-- the organization. Exact domains only; config rows are reconciled with kiln.auth.allowed-domains.
CREATE TABLE allowed_domains (
    connector_id    text        NOT NULL,
    domain          text        NOT NULL CHECK (domain = lower(domain)),
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    source          text        NOT NULL CHECK (source IN ('config', 'admin')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (connector_id, domain)
);

-- An invitation is fixed when it's created: the runtime role can only record its claim, once.
-- Nothing can turn an organization's invitation into an instance-wide one by clearing
-- organization_id, or extend it, or claim it twice.
REVOKE UPDATE ON invitations FROM kiln_app;
GRANT UPDATE (claimed_by, claimed_at) ON invitations TO kiln_app;

CREATE FUNCTION invitations_claim_once() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.claimed_by IS NOT NULL THEN
        RAISE EXCEPTION 'invitation % is already claimed', OLD.id;
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER invitations_claim_once BEFORE UPDATE ON invitations
    FOR EACH ROW EXECUTE FUNCTION invitations_claim_once();
