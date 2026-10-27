-- Who holds which role where: a team, member or service account, at the organization (no
-- namespace), a namespace, or one repository in it.
CREATE TABLE role_bindings (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    id              uuid        NOT NULL,
    subject_kind    text        NOT NULL CHECK (subject_kind IN ('team', 'member', 'service_account')),
    subject_id      uuid        NOT NULL,
    role            text        NOT NULL CHECK (role IN ('reader', 'writer', 'security', 'admin')),
    namespace       text,
    repository      text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, id),
    CHECK (repository IS NULL OR namespace IS NOT NULL)
);
CREATE UNIQUE INDEX role_bindings_unique ON role_bindings
    (organization_id, subject_kind, subject_id, role, coalesce(namespace, ''), coalesce(repository, ''));
ALTER TABLE role_bindings ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_bindings FORCE ROW LEVEL SECURITY;
CREATE POLICY organization ON role_bindings
    USING (organization_id = current_setting('app.organization_id')::uuid)
    WITH CHECK (organization_id = current_setting('app.organization_id')::uuid);
