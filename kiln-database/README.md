# kiln-database

The Postgres layer every subsystem uses: migrations, the database roles, row-level security with
the organization set per transaction, and the schema linter.

## At a glance

| | |
|---|---|
| Kind | Foundation |
| Artifact | `com.bulmerkiln:kiln-database` (plus a `test-fixtures` variant) |
| Runs in | Kiln's server |
| Depends on | [`kiln-core`](../kiln-core) |
| Used by | Every subsystem that stores data; `kiln-oss-service` |
| Status | M1, Experimental |

## Purpose

Organizations share one database, so a query that forgets its organization must fail rather than
return everyone's rows. Postgres row-level security is the second line of defence behind the
application's own checks, but it fails silently in several common setups: a table without a
policy, a policy that isn't forced on the owner, a role that bypasses it, a setting that outlives
its request on a pooled connection. This module designs each of those out, and checks the schema
at every start.

## Responsibilities

**Does**

- Run every module's migrations as the migration role (`kiln_migrator`), which owns the schema
- Connect at runtime as `kiln_app`: no owner, no superuser, no `BYPASSRLS`, rows only
- Bind the organization in `OrganizationContext` to each transaction, transaction-local
- Refuse to start when the schema or the runtime role isn't organization-safe

**Does not**

- Create the roles or the database; the operator does, once, before the first start
- Define any subsystem's tables; each module ships its own migrations

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| DB-1 | Migrations must run as the migration role, from `db/kiln/<module>/` in every module on the classpath, in one history. Versions are timestamps (`V202610271200__name.sql`), so modules never collide | `DatabaseAutoConfigurationTest` |
| DB-2 | Migrations must be skippable (`kiln.database.migrate=false`) for installations that run them separately; the runtime role never needs the migration role's credentials then | `DatabaseAutoConfigurationTest` |
| DB-3 | Every query must run inside one of `Database`'s transactions. The runtime pool and the organization-binding transaction manager are beans for the data layer only (repositories and auto-configurations, ARCH-12); the pool is the runtime role's and closes with `Database` | `DatabaseAutoConfigurationTest` |
| DB-4 | An organization transaction must set `app.organization_id` with `set_config(…, true)`, so it ends with the transaction and a pooled connection never carries it into the next. Each organization sees and writes only its own rows; a failed transaction rolls back. With repositories, the transaction manager binds the organization of every transaction `Database.transaction` starts, and only those | `DatabaseTest`, `KilnRepositoryTest` |
| DB-5 | Without an organization in context, an organization transaction must fail before reaching the database, and an instance transaction, or any transaction that names no organization, must be refused by every organization table, on a fresh connection or a reused one | `DatabaseTest`, `KilnRepositoryTest` |
| DB-6 | Kiln must refuse to start unless every table outside `SchemaLinter.INSTANCE_TABLES` has a non-null `uuid` `organization_id`, row-level security enabled and forced, and exactly the organization policy as its only permissive policy; every unique index starts with `organization_id`; every foreign key to `organizations` or another organization table includes it, on both sides between organization tables (a key to another instance table, such as `users`, needn't); no function is `SECURITY DEFINER`; every view uses `security_invoker`; and the runtime role isn't a superuser, doesn't bypass row-level security and owns nothing | `SchemaLinterTest`, `DatabaseAutoConfigurationTest`, `DatabaseTest` |
| DB-7 | Every organization table's policy must be `organization_id = current_setting('app.organization_id')::uuid`, without `missing_ok`, for reads and writes | `SchemaLinterTest` |
| DB-8 | Settings must be checked before any connection: a PostgreSQL URL, a runtime role, a migration role different from it when migrating, a lowercase schema name, a pool of at least one | `DatabaseAutoConfigurationTest` |
| DB-9 | `StoredOrganizationDirectory` must find only active organizations, by ID or exact slug; a suspended or deleting one, an unknown slug or an invalid one finds none. Creating an organization that exists changes nothing, so its ID never changes | `StoredOrganizationDirectoryTest` |
| DB-10 | The organizations `kiln.bootstrap.organizations` lists must exist after start, and none is deleted for leaving the list; an invalid or reserved slug stops the boot | `StoredOrganizationDirectoryTest` |
| DB-11 | Every repository must extend `KilnRepository`, which offers only `save`, `findById` and `deleteById` beside the repository's own queries, and needs the caller's transaction: a repository called outside one fails rather than running in auto-commit, and opens no transaction of its own. `save` only inserts; an update is a named `@Modifying @Query` | `KilnRepositoryTest` |

## Contract *(foundation only)*

| Type | Role |
|---|---|
| `Database` | `transaction(work)` for the current organization; `instanceTransaction(work)` for instance tables. Repositories run in the `Supplier` forms; the jOOQ forms remain until every module has moved |
| `KilnRepository` | The base of every repository: `save` (inserts only), `findById`, `deleteById`, each needing the caller's transaction |
| `DataJdbcAutoConfiguration` | Spring Data JDBC over the runtime pool; a module enables its own repositories after it, with `enableDefaultTransactions = false` |
| `MigrationRunner` | Runs every module's migrations; `LOCATION` is where they live |
| `SchemaLinter` | The organization-safety rules, and the instance-table list |
| `StoredOrganizationDirectory` | The `OrganizationDirectory` over the `organizations` table, unless the service provides its own |

A module that stores data adds its migrations under `src/main/resources/db/kiln/<module>/`, and
its tests run them with `KilnPostgres` from the test fixtures. Its tables sit behind repositories
in its `repository` package, each extending `KilnRepository`, with entities in `entity`:

```java
public interface ThingRepository extends KilnRepository<ThingEntity, ThingId> {

    @Query("SELECT organization_id, id, name FROM things WHERE organization_id = :organizationId AND name = :name")
    Optional<ThingEntity> findByName(UUID organizationId, String name);

    @Modifying
    @Query("UPDATE things SET name = :name WHERE organization_id = :organizationId AND id = :id")
    int rename(UUID organizationId, UUID id, String name);
}

// An entity with an ID Kiln assigns says whether it's new, and Spring Data reads rows through
// the constructor marked @PersistenceCreator:
@Table("things")
public record ThingEntity(@Id ThingId id, String name, @Transient boolean fresh) implements Persistable<ThingId> {

    @PersistenceCreator
    ThingEntity(ThingId id, String name) {
        this(id, name, false);
    }

    @Override
    public ThingId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }
}

// In a service, inside the organization's transaction:
database.transaction(() -> things.findByName(organizationId, name));
```

Every organization table follows this shape:

```sql
CREATE TABLE things (
    organization_id uuid NOT NULL REFERENCES organizations (id),
    id              uuid NOT NULL,
    PRIMARY KEY (organization_id, id)
);
ALTER TABLE things ENABLE ROW LEVEL SECURITY;
ALTER TABLE things FORCE ROW LEVEL SECURITY;
CREATE POLICY organization ON things
    USING (organization_id = current_setting('app.organization_id')::uuid)
    WITH CHECK (organization_id = current_setting('app.organization_id')::uuid);
```

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.database.url` | none, required | A `jdbc:postgresql://` URL |
| `kiln.database.username`, `kiln.database.password` | none, required | The runtime role, `kiln_app` |
| `kiln.database.migrator-username`, `kiln.database.migrator-password` | none, required when migrating | The migration role, `kiln_migrator`, which owns the schema |
| `kiln.database.schema` | `kiln` | The schema Kiln's tables live in |
| `kiln.database.migrate` | `true` | Run migrations at startup |
| `kiln.database.maximum-pool-size` | `10` | The runtime connection pool's size |
| `kiln.bootstrap.organizations` | none | Slugs of organizations to create on start if they're missing |

The operator creates the roles and the database once:

```sql
CREATE ROLE kiln_migrator LOGIN PASSWORD '…';
CREATE ROLE kiln_app LOGIN PASSWORD '…' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
CREATE ROLE kiln_ops LOGIN PASSWORD '…' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
CREATE DATABASE kiln OWNER kiln_migrator;
```

## Usage

```java
List<String> names = OrganizationContext.call(organization, () -> database.transaction(db ->
        db.select(THINGS.NAME).from(THINGS).fetch(THINGS.NAME)));
```

## Security

- **Threats**: one organization reading or writing another's rows through a missing filter, a
  missing or permissive policy, a role that bypasses row-level security, or a setting left on a
  pooled connection; a unique violation or foreign key revealing another organization's row
- **Untrusted input**: none; queries take values as bind parameters
- **Defaults**: no organization means an error, never every row (DB-5, DB-7); an unsafe schema
  or runtime role stops the boot (DB-6)
- **Protections**: transaction-local organization (DB-4); forced row-level security checked at
  every start (DB-6); separate migration and runtime roles (DB-1)

## Testing

- **Contract suite**: none yet; modules run `SchemaLinter` against their own migrations
- **Fuzz targets**: none; it parses no untrusted input
- **Other**: every test runs against PostgreSQL 17 with the three roles, each in its own database.
  Under Gradle that's one server per build, shared by every test JVM (`kiln-build` starts
  it and passes it in `KILN_TEST_POSTGRES_FILE`); run elsewhere, such as from an IDE, `KilnPostgres`
  starts its own container

```bash
./gradlew :kiln-database:test
```

The tests need Docker.

## Standards and references

- **Standards**: PostgreSQL row-level security (`CREATE POLICY`, `FORCE ROW LEVEL SECURITY`),
  `set_config` with `is_local`
- **Dependencies**: [jOOQ](https://www.jooq.org), [Flyway](https://github.com/flyway/flyway),
  [HikariCP](https://github.com/brettwooldridge/HikariCP), the PostgreSQL JDBC driver,
  [Testcontainers](https://testcontainers.com)
- **Reference projects**: the AWS and Crunchy Data guides to multi-tenant row-level security

## Status

Implemented: DB-1 to DB-10.

Planned: the organization predicate jOOQ adds to every query, so the application filters as well
as the database; jOOQ code generation from the migrations; a separate migration command for
installations that don't migrate at startup.
