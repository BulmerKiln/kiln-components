-- OCI on the registry's tables: a repository is a packages row (format 'oci'), created on first
-- push inside a namespace that claims its prefix; a manifest is a package_versions row whose
-- version is its digest, and its bytes are a blob, recorded as the version's file. These tables
-- hold what only OCI has.

-- Which repositories may read a blob: pushing or mounting it there links it, and only a link lets
-- a pull find it, so one repository's blobs never leak through another's name.
CREATE TABLE oci_repository_blobs (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    package_id      uuid        NOT NULL,
    digest          text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, package_id, digest),
    FOREIGN KEY (organization_id, package_id) REFERENCES packages (organization_id, id),
    FOREIGN KEY (organization_id, digest) REFERENCES blobs (organization_id, digest)
);
CREATE INDEX oci_repository_blobs_by_digest ON oci_repository_blobs (organization_id, digest);

-- A manifest's media types and subject; its bytes are the file of its version.
CREATE TABLE oci_manifests (
    organization_id uuid NOT NULL REFERENCES organizations (id),
    package_id      uuid NOT NULL,
    version_id      uuid NOT NULL,
    media_type      text NOT NULL,
    artifact_type   text,
    subject         text,
    file_id         uuid NOT NULL,
    PRIMARY KEY (organization_id, version_id),
    UNIQUE (organization_id, package_id, version_id),
    UNIQUE (organization_id, file_id),
    -- The manifest's repository is its version's package, so the two can't disagree.
    FOREIGN KEY (organization_id, package_id, version_id)
        REFERENCES package_versions (organization_id, package_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, file_id) REFERENCES package_files (organization_id, id)
);
CREATE INDEX oci_manifests_by_subject ON oci_manifests (organization_id, package_id, subject);

-- What each manifest references: blobs for an image, manifests for an index.
CREATE TABLE oci_manifest_refs (
    organization_id uuid NOT NULL REFERENCES organizations (id),
    version_id      uuid NOT NULL,
    child           text NOT NULL,
    kind            text NOT NULL CHECK (kind IN ('blob', 'manifest')),
    PRIMARY KEY (organization_id, version_id, child),
    FOREIGN KEY (organization_id, version_id) REFERENCES oci_manifests (organization_id, version_id) ON DELETE CASCADE
);
CREATE INDEX oci_manifest_refs_by_child ON oci_manifest_refs (organization_id, child);

-- Tags point at manifests of their own repository, and go with the manifest.
CREATE TABLE oci_tags (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    package_id      uuid        NOT NULL,
    name            text        NOT NULL,
    version_id      uuid        NOT NULL,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, package_id, name),
    FOREIGN KEY (organization_id, package_id, version_id)
        REFERENCES oci_manifests (organization_id, package_id, version_id) ON DELETE CASCADE
);
CREATE INDEX oci_tags_by_version ON oci_tags (organization_id, package_id, version_id);

-- Upload sessions, so a session can only be continued through the repository that started it.
-- The bytes are in the blob store.
CREATE TABLE oci_upload_sessions (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    id              uuid        NOT NULL,
    package_id      uuid        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, id),
    FOREIGN KEY (organization_id, package_id) REFERENCES packages (organization_id, id)
);
CREATE INDEX oci_upload_sessions_by_package ON oci_upload_sessions (organization_id, package_id);

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['oci_repository_blobs', 'oci_manifests', 'oci_manifest_refs', 'oci_tags',
                             'oci_upload_sessions'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY organization ON %I'
                       ' USING (organization_id = current_setting(''app.organization_id'')::uuid)'
                       ' WITH CHECK (organization_id = current_setting(''app.organization_id'')::uuid)', t);
    END LOOP;
END
$$;
