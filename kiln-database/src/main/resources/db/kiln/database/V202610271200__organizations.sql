-- The runtime role reads and writes rows and nothing else; the read-only operations role only
-- reads. Neither owns anything, and default privileges cover every table later migrations create.
GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO kiln_app, kiln_ops;
ALTER DEFAULT PRIVILEGES IN SCHEMA ${flyway:defaultSchema}
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO kiln_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA ${flyway:defaultSchema}
    GRANT USAGE, SELECT ON SEQUENCES TO kiln_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA ${flyway:defaultSchema}
    GRANT SELECT ON TABLES TO kiln_ops;

-- The organization registry: an instance table, so it has no organization_id of its own.
CREATE TABLE organizations (
    id           uuid        PRIMARY KEY,
    slug         text        NOT NULL UNIQUE,
    display_name text        NOT NULL,
    status       text        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended', 'deleting')),
    created_at   timestamptz NOT NULL DEFAULT now()
);
