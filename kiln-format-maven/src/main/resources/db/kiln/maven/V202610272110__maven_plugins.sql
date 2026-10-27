-- Group-level plugin metadata: each Maven plugin's goal prefix, from the metadata its deploy
-- uploads (FMT-MVN-17). One row per plugin package.
CREATE TABLE maven_plugins (
    organization_id uuid NOT NULL REFERENCES organizations (id),
    package_id      uuid NOT NULL,
    group_id        text NOT NULL,
    prefix          text NOT NULL,
    name            text,
    PRIMARY KEY (organization_id, package_id),
    FOREIGN KEY (organization_id, package_id) REFERENCES packages (organization_id, id)
);
CREATE INDEX maven_plugins_group ON maven_plugins (organization_id, group_id);

ALTER TABLE maven_plugins ENABLE ROW LEVEL SECURITY;
ALTER TABLE maven_plugins FORCE ROW LEVEL SECURITY;
CREATE POLICY organization ON maven_plugins
    USING (organization_id = current_setting('app.organization_id')::uuid)
    WITH CHECK (organization_id = current_setting('app.organization_id')::uuid);
