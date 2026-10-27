-- An organization table shaped the way every module's must be, for the tests.
CREATE TABLE widgets (
    organization_id uuid NOT NULL REFERENCES organizations (id),
    id              uuid NOT NULL,
    name            text NOT NULL,
    PRIMARY KEY (organization_id, id),
    UNIQUE (organization_id, name)
);
ALTER TABLE widgets ENABLE ROW LEVEL SECURITY;
ALTER TABLE widgets FORCE ROW LEVEL SECURITY;
CREATE POLICY organization ON widgets
    USING (organization_id = current_setting('app.organization_id')::uuid)
    WITH CHECK (organization_id = current_setting('app.organization_id')::uuid);
