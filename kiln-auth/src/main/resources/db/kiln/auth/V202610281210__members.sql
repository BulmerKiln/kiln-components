-- A user in one organization. Roles come from role_bindings; a member alone can do nothing.
CREATE TABLE members (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    user_id         uuid        NOT NULL REFERENCES users (id),
    status          text        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    disabled_at     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, user_id)
);
CREATE INDEX members_user ON members (user_id);
ALTER TABLE members ENABLE ROW LEVEL SECURITY;
ALTER TABLE members FORCE ROW LEVEL SECURITY;
CREATE POLICY organization ON members
    USING (organization_id = current_setting('app.organization_id')::uuid)
    WITH CHECK (organization_id = current_setting('app.organization_id')::uuid);

-- A membership can be disabled, never moved to another user or organization.
REVOKE UPDATE ON members FROM kiln_app;
GRANT UPDATE (status, disabled_at) ON members TO kiln_app;
