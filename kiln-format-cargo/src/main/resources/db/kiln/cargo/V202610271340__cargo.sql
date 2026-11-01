-- Cargo on the registry's tables: each version's index line, the publish metadata as received
-- (description, license, readme and the rest, which the index doesn't carry), and whether it's
-- yanked, the only thing about a version that changes.

CREATE TABLE cargo_versions (
    organization_id  uuid    NOT NULL REFERENCES organizations (id),
    version_id       uuid    NOT NULL,
    index_entry      text    NOT NULL,
    publish_metadata text    NOT NULL,
    yanked           boolean NOT NULL DEFAULT false,
    PRIMARY KEY (organization_id, version_id),
    FOREIGN KEY (organization_id, version_id) REFERENCES package_versions (organization_id, id) ON DELETE CASCADE
);

ALTER TABLE cargo_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE cargo_versions FORCE ROW LEVEL SECURITY;
CREATE POLICY organization ON cargo_versions
    USING (organization_id = current_setting('app.organization_id')::uuid)
    WITH CHECK (organization_id = current_setting('app.organization_id')::uuid);
