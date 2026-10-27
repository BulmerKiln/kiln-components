-- Namespaces: the names an organization owns in each format, and who may read them.
CREATE TABLE namespaces (
    organization_id      uuid        NOT NULL REFERENCES organizations (id),
    id                   uuid        NOT NULL,
    name                 text        NOT NULL,
    visibility           text        NOT NULL DEFAULT 'private' CHECK (visibility IN ('private', 'public')),
    oci_prefix           text,
    npm_scope            text,
    maven_group_prefixes text[]      NOT NULL DEFAULT '{}',
    cargo_prefixes       text[]      NOT NULL DEFAULT '{}',
    created_at           timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, id),
    UNIQUE (organization_id, name),
    UNIQUE (organization_id, oci_prefix),
    UNIQUE (organization_id, npm_scope)
);

-- Every blob an organization holds, whichever format stored it: dedupe and GC work from here.
CREATE TABLE blobs (
    organization_id    uuid        NOT NULL REFERENCES organizations (id),
    digest             text        NOT NULL,
    size               bigint      NOT NULL CHECK (size >= 0),
    created_at         timestamptz NOT NULL DEFAULT now(),
    last_referenced_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, digest)
);

-- Packages of the package formats (npm, Maven, Cargo): versions, and the files of each version.
CREATE TABLE packages (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    id              uuid        NOT NULL,
    namespace_id    uuid        NOT NULL,
    format          text        NOT NULL,
    name            text        NOT NULL,
    normalized_name text        NOT NULL,
    metadata        text        NOT NULL DEFAULT '{}',
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, id),
    UNIQUE (organization_id, format, normalized_name),
    FOREIGN KEY (organization_id, namespace_id) REFERENCES namespaces (organization_id, id)
);

CREATE TABLE package_versions (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    id              uuid        NOT NULL,
    package_id      uuid        NOT NULL,
    version         text        NOT NULL,
    metadata        text        NOT NULL DEFAULT '{}',
    yanked          boolean     NOT NULL DEFAULT false,
    published_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, id),
    UNIQUE (organization_id, package_id, version),
    FOREIGN KEY (organization_id, package_id) REFERENCES packages (organization_id, id)
);

CREATE TABLE package_files (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    version_id      uuid        NOT NULL,
    name            text        NOT NULL,
    digest          text        NOT NULL,
    size            bigint      NOT NULL CHECK (size >= 0),
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, version_id, name),
    FOREIGN KEY (organization_id, version_id) REFERENCES package_versions (organization_id, id),
    FOREIGN KEY (organization_id, digest) REFERENCES blobs (organization_id, digest)
);

-- Blocks refuse reads and writes of an artifact; version '' blocks every version. Tombstones
-- reserve a deleted version, so it can never be published again.
CREATE TABLE blocks (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    format          text        NOT NULL,
    name            text        NOT NULL,
    version         text        NOT NULL DEFAULT '',
    reason          text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, format, name, version)
);

CREATE TABLE tombstones (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    format          text        NOT NULL,
    name            text        NOT NULL,
    version         text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, format, name, version)
);

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['namespaces', 'blobs', 'packages', 'package_versions', 'package_files',
                             'blocks', 'tombstones'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY organization ON %I'
                       ' USING (organization_id = current_setting(''app.organization_id'')::uuid)'
                       ' WITH CHECK (organization_id = current_setting(''app.organization_id'')::uuid)', t);
    END LOOP;
END
$$;
