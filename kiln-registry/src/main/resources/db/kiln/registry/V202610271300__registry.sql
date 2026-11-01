-- The registry every format shares: namespaces and the names they claim, the blobs an
-- organization holds, packages with their versions and files, and blocks and tombstones. Each
-- format adds its own detail tables, keyed by these rows.

-- Namespaces: the names an organization owns across formats, and who may read them.
CREATE TABLE namespaces (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    id              uuid        NOT NULL,
    name            text        NOT NULL,
    visibility      text        NOT NULL DEFAULT 'private' CHECK (visibility IN ('private', 'public')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, id),
    UNIQUE (organization_id, name)
);

-- One row per name a namespace claims in a format, so a new format adds rows, not columns. A name
-- matches the value exactly, or starts with value || separator (with no separator, starts with
-- value); the longest match wins (REG-1).
CREATE TABLE namespace_claims (
    organization_id uuid NOT NULL REFERENCES organizations (id),
    namespace_id    uuid NOT NULL,
    format          text NOT NULL,
    value           text NOT NULL CHECK (value <> ''),
    separator       text CHECK (separator <> ''),
    PRIMARY KEY (organization_id, format, value),
    FOREIGN KEY (organization_id, namespace_id) REFERENCES namespaces (organization_id, id) ON DELETE CASCADE
);
CREATE INDEX namespace_claims_by_namespace ON namespace_claims (organization_id, namespace_id);

-- Every blob an organization holds, whichever format stored it: dedupe and GC work from here. The
-- checksums are filled where a format hashes a whole file as it streams in; they're served to
-- clients that ask for them, never used as an address.
CREATE TABLE blobs (
    organization_id    uuid        NOT NULL REFERENCES organizations (id),
    digest             text        NOT NULL,
    size               bigint      NOT NULL CHECK (size >= 0),
    md5                text,
    sha1               text,
    sha256             text,
    sha512             text,
    created_at         timestamptz NOT NULL DEFAULT now(),
    last_referenced_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, digest)
);

-- Packages of every format; an OCI repository is one. updated_at moves with any change a format
-- shows, so it's what a format reports as last modified.
CREATE TABLE packages (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    id              uuid        NOT NULL,
    namespace_id    uuid        NOT NULL,
    format          text        NOT NULL,
    name            text        NOT NULL,
    normalized_name text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, id),
    UNIQUE (organization_id, format, normalized_name),
    FOREIGN KEY (organization_id, namespace_id) REFERENCES namespaces (organization_id, id)
);
CREATE INDEX packages_by_namespace ON packages (organization_id, namespace_id);

-- Versions never change once published (REG-3); what a format lets change lives in its own
-- tables. An OCI version is a manifest, by digest. published_by is the member or service account
-- that published it.
CREATE TABLE package_versions (
    organization_id    uuid        NOT NULL REFERENCES organizations (id),
    id                 uuid        NOT NULL,
    package_id         uuid        NOT NULL,
    version            text        NOT NULL,
    normalized_version text        NOT NULL,
    published_at       timestamptz NOT NULL DEFAULT now(),
    published_by_kind  text        CHECK (published_by_kind IN ('member', 'service_account')),
    published_by_id    uuid,
    PRIMARY KEY (organization_id, id),
    UNIQUE (organization_id, package_id, normalized_version),
    UNIQUE (organization_id, package_id, id),
    CHECK ((published_by_kind IS NULL) = (published_by_id IS NULL)),
    FOREIGN KEY (organization_id, package_id) REFERENCES packages (organization_id, id)
);

CREATE TABLE package_files (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    id              uuid        NOT NULL,
    version_id      uuid        NOT NULL,
    name            text        NOT NULL,
    digest          text        NOT NULL,
    size            bigint      NOT NULL CHECK (size >= 0),
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, id),
    UNIQUE (organization_id, version_id, name),
    FOREIGN KEY (organization_id, version_id) REFERENCES package_versions (organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, digest) REFERENCES blobs (organization_id, digest)
);
CREATE INDEX package_files_by_digest ON package_files (organization_id, digest);

-- Blocks refuse reads and writes. A block targets an exact version, a version range in the
-- format's own syntax, a digest (whatever points at it, so no name) or the whole package. It's
-- lifted with a reason, never deleted, so its history stays.
CREATE TABLE blocks (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    id              uuid        NOT NULL,
    format          text        NOT NULL,
    name            text,
    selector_kind   text        NOT NULL CHECK (selector_kind IN ('version', 'range', 'digest', 'package')),
    selector        text        NOT NULL,
    reason          text        NOT NULL,
    source          text        NOT NULL CHECK (source IN ('manual', 'malware_feed', 'scanner_policy')),
    created_by_kind text        CHECK (created_by_kind IN ('member', 'service_account')),
    created_by_id   uuid,
    created_at      timestamptz NOT NULL DEFAULT now(),
    lifted_at       timestamptz,
    lifted_by_kind  text        CHECK (lifted_by_kind IN ('member', 'service_account')),
    lifted_by_id    uuid,
    lift_reason     text,
    PRIMARY KEY (organization_id, id),
    CHECK ((selector_kind = 'digest') = (name IS NULL)),
    CHECK ((selector_kind = 'package') = (selector = '')),
    CHECK ((created_by_kind IS NULL) = (created_by_id IS NULL)),
    CHECK ((source = 'manual') = (created_by_id IS NOT NULL)),
    CHECK ((lifted_at IS NULL) = (lift_reason IS NULL) AND (lifted_at IS NULL) = (lifted_by_id IS NULL)),
    CHECK ((lifted_by_kind IS NULL) = (lifted_by_id IS NULL))
);
-- One active block per target; a lifted one doesn't stop a new block.
CREATE UNIQUE INDEX blocks_active ON blocks (organization_id, format, coalesce(name, ''), selector_kind, selector)
    WHERE lifted_at IS NULL;
CREATE INDEX blocks_by_name ON blocks (organization_id, format, name) WHERE lifted_at IS NULL;
CREATE INDEX blocks_by_digest ON blocks (organization_id, selector) WHERE lifted_at IS NULL AND selector_kind = 'digest';

-- Blocks instance admins set, which apply in every organization: a compromised public package is
-- blocked once, for everyone. An instance table, so it has no organization_id.
CREATE TABLE instance_blocks (
    id                 uuid        PRIMARY KEY,
    format             text        NOT NULL,
    name               text,
    selector_kind      text        NOT NULL CHECK (selector_kind IN ('version', 'range', 'digest', 'package')),
    selector           text        NOT NULL,
    reason             text        NOT NULL,
    source             text        NOT NULL CHECK (source IN ('manual', 'malware_feed', 'scanner_policy')),
    created_by_user_id uuid,
    created_at         timestamptz NOT NULL DEFAULT now(),
    lifted_at          timestamptz,
    lifted_by_user_id  uuid,
    lift_reason        text,
    CHECK ((selector_kind = 'digest') = (name IS NULL)),
    CHECK ((selector_kind = 'package') = (selector = '')),
    CHECK ((source = 'manual') = (created_by_user_id IS NOT NULL)),
    CHECK ((lifted_at IS NULL) = (lift_reason IS NULL) AND (lifted_at IS NULL) = (lifted_by_user_id IS NULL))
);
CREATE UNIQUE INDEX instance_blocks_active ON instance_blocks (format, coalesce(name, ''), selector_kind, selector)
    WHERE lifted_at IS NULL;
CREATE INDEX instance_blocks_by_name ON instance_blocks (format, name) WHERE lifted_at IS NULL;

-- A deleted version, or a deleted OCI tag, can never be published again. Keyed by name, not
-- package, so it outlives the package.
CREATE TABLE tombstones (
    organization_id uuid        NOT NULL REFERENCES organizations (id),
    format          text        NOT NULL,
    name            text        NOT NULL,
    kind            text        NOT NULL CHECK (kind IN ('version', 'tag')),
    value           text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, format, name, kind, value)
);

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['namespaces', 'namespace_claims', 'blobs', 'packages', 'package_versions',
                             'package_files', 'blocks', 'tombstones'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY organization ON %I'
                       ' USING (organization_id = current_setting(''app.organization_id'')::uuid)'
                       ' WITH CHECK (organization_id = current_setting(''app.organization_id'')::uuid)', t);
    END LOOP;
END
$$;
