-- npm on the registry's tables: each version's document as published, and the dist-tags, which
-- point at versions of their own package and go with the version.

CREATE TABLE npm_versions (
    organization_id uuid NOT NULL REFERENCES organizations (id),
    version_id      uuid NOT NULL,
    document        text NOT NULL,
    PRIMARY KEY (organization_id, version_id),
    FOREIGN KEY (organization_id, version_id) REFERENCES package_versions (organization_id, id) ON DELETE CASCADE
);

CREATE TABLE npm_dist_tags (
    organization_id uuid NOT NULL REFERENCES organizations (id),
    package_id      uuid NOT NULL,
    tag             text NOT NULL,
    version_id      uuid NOT NULL,
    PRIMARY KEY (organization_id, package_id, tag),
    FOREIGN KEY (organization_id, package_id, version_id)
        REFERENCES package_versions (organization_id, package_id, id) ON DELETE CASCADE
);
CREATE INDEX npm_dist_tags_by_version ON npm_dist_tags (organization_id, package_id, version_id);

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['npm_versions', 'npm_dist_tags'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY organization ON %I'
                       ' USING (organization_id = current_setting(''app.organization_id'')::uuid)'
                       ' WITH CHECK (organization_id = current_setting(''app.organization_id'')::uuid)', t);
    END LOOP;
END
$$;
