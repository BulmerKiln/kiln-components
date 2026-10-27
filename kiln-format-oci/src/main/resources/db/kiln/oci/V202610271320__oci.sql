-- OCI repositories, created on first push inside a namespace that owns their prefix.
CREATE TABLE repositories (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    id              uuid        NOT NULL,
    namespace_id    uuid        NOT NULL,
    name            text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, id),
    UNIQUE (organization_id, name),
    FOREIGN KEY (organization_id, namespace_id) REFERENCES namespaces (organization_id, id)
);

-- Which repositories may read a blob: pushing or mounting it there links it, and only a link lets
-- a pull find it, so one repository's blobs never leak through another's name.
CREATE TABLE repository_blobs (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    repository_id   uuid        NOT NULL,
    digest          text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, repository_id, digest),
    FOREIGN KEY (organization_id, repository_id) REFERENCES repositories (organization_id, id),
    FOREIGN KEY (organization_id, digest) REFERENCES blobs (organization_id, digest)
);

-- Manifests keep the exact bytes pushed, since their digest is over those bytes.
CREATE TABLE manifests (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    repository_id   uuid        NOT NULL,
    digest          text        NOT NULL,
    media_type      text        NOT NULL,
    artifact_type   text,
    subject         text,
    content         bytea       NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, repository_id, digest),
    FOREIGN KEY (organization_id, repository_id) REFERENCES repositories (organization_id, id)
);
CREATE INDEX manifests_by_subject ON manifests (organization_id, repository_id, subject);

-- What each manifest references: blobs for an image, manifests for an index.
CREATE TABLE manifest_refs (
    organization_id uuid NOT NULL REFERENCES organizations (id),
    repository_id   uuid NOT NULL,
    digest          text NOT NULL,
    child           text NOT NULL,
    kind            text NOT NULL CHECK (kind IN ('blob', 'manifest')),
    PRIMARY KEY (organization_id, repository_id, digest, child),
    FOREIGN KEY (organization_id, repository_id, digest)
        REFERENCES manifests (organization_id, repository_id, digest) ON DELETE CASCADE
);
CREATE INDEX manifest_refs_by_child ON manifest_refs (organization_id, repository_id, child);

CREATE TABLE tags (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    repository_id   uuid        NOT NULL,
    name            text        NOT NULL,
    digest          text        NOT NULL,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, repository_id, name),
    FOREIGN KEY (organization_id, repository_id, digest)
        REFERENCES manifests (organization_id, repository_id, digest) ON DELETE CASCADE
);

-- Upload sessions, so a session can only be continued through the repository that started it.
-- The bytes are in the blob store.
CREATE TABLE upload_sessions (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    id              uuid        NOT NULL,
    repository_id   uuid        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, id),
    FOREIGN KEY (organization_id, repository_id) REFERENCES repositories (organization_id, id)
);

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['repositories', 'repository_blobs', 'manifests', 'manifest_refs', 'tags',
                             'upload_sessions'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY organization ON %I'
                       ' USING (organization_id = current_setting(''app.organization_id'')::uuid)'
                       ' WITH CHECK (organization_id = current_setting(''app.organization_id'')::uuid)', t);
    END LOOP;
END
$$;
