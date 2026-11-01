# kiln-registry

What every format stores alike: namespaces and the names they claim, the blobs an organization
holds, packages with their versions and files, and blocks and tombstones. Each format keeps what
only it has in its own tables, keyed by these rows.

## At a glance

| | |
|---|---|
| Kind | Subsystem |
| Artifact | `com.bulmerkiln:kiln-registry` |
| Runs in | Kiln's server |
| Depends on | [`kiln-core`](../kiln-core), [`kiln-database`](../kiln-database), [`kiln-format`](../kiln-format), [`kiln-permissions`](../kiln-permissions) |
| Used by | The `kiln-format-*` modules |
| Status | M1, Experimental |

## Purpose

Formats differ in protocol, but not in what they need to remember: who owns a name, which blobs
an organization holds, which versions of a package exist and which files make them up, and what
may no longer be read or published. Keeping that here means one set of tables to secure, to
garbage-collect, and to show in the web app, and one place that decides immutability and blocks.

## Responsibilities

**Does**

- Find the namespace that owns a name in each format, from the names each namespace claims
  (`NamespaceService`)
- Record the blobs an organization holds, with the checksums a format took (`BlobRecordService`)
- Keep packages of every format, their versions and files, immutable once written, and when each
  package last changed (`PackageService`)
- Keep blocks and tombstones, and answer the formats' `ArtifactPolicy` port from them
  (`BlockService`, `RegistryPolicy`)
- Answer the permissions' visibility port, a `Lookup<Scope, Visibility>`, from namespaces

**Does not**

- Speak any format's protocol, or interpret a format's metadata; formats keep their own in their
  own tables, keyed by a package, version or file
- Hold the bytes; the blob store does

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| REG-1 | A namespace must own a name in a format through a claim: a value and, for formats whose names have one, a separator. A name matches a claim it equals, or one followed by its separator (any continuation when it has none), so `acme` with `/` owns OCI `acme/api`, `@acme` with `/` owns npm `@acme/ui`, `org.acme` with `.` owns Maven `org.acme.core`, and `acme-` with none owns Cargo `acme-core`. The longest matching claim wins; a name no namespace claims has none, and a value is claimed once per format. Namespaces are per organization, so two organizations can own the same names | `RegistryTest` |
| REG-2 | Recording a blob the organization already holds must be harmless, so concurrent pushes of the same content both succeed; checksums already recorded are kept, and missing ones added | `RegistryTest` |
| REG-3 | A version that exists, or a file of a version under a name that exists with other content, must be refused as a conflict. The same file again is a no-op, since clients retry. Two first publishes of a package must share one package. A format that publishes a version file by file ensures the version instead, and then adds immutable files to it | `RegistryTest` |
| REG-4 | A blocked artifact must be refused with `BLOCKED` (403, never 404) on read and write, for one version or every version; a tombstoned version must be refused as a conflict on write, forever. Formats check both inside the transaction that writes. A manual block names the member or service account that set it | `RegistryTest` |
| REG-5 | Visibility must come from the namespace; the organization itself and an unknown namespace are private | `RegistryTest` |
| REG-6 | Every registry table must pass the schema linter, and every query filters on the organization as well as relying on row-level security | `RegistryTest` |
| REG-7 | The registry must answer `ArtifactPolicy` and the visibility `Lookup<Scope, Visibility>` unless the assembly provides its own, and give formats its services | `RegistryAutoConfigurationTest` |
| REG-8 | Deleting a version must remove it and its files, and a package can be removed once it has no versions. Callers tombstone a version before deleting it, so it can't be published again | `RegistryTest` |
| REG-9 | A package's last change must move with every change a format shows: a new version or file, a deleted version, or a change the format marks (a moved tag, a deprecation, a yank), and not with a read | `RegistryTest` |

## Contract *(subsystem only)*

| Type | Role |
|---|---|
| `NamespaceService`, `Namespace`, `NamespaceClaim` | `create`, `owner(format, name)`, `byName`, `byId` |
| `BlobRecordService`, `BlobChecksums` | `record`, `size`, `checksums` |
| `PackageService`, `RegistryPackage`, `PackageVersion`, `PackageFile` | Packages, versions and files of every format, and `touch` for a format's own changes |
| `BlockService` | `block`, `tombstone`, `checkRead`, `checkWrite` |
| `RegistryPolicy`, `NamespaceVisibilityLookup` | The ports' implementations |

The services run inside the caller's transaction (`Database.transaction`), and their repositories
refuse to run without one, so a format checks, writes and records in one commit. A format extends
these rows with its own tables, each keyed by the row it extends with `organization_id` first, and
its own repositories.

## Configuration

None.

## Usage

```java
database.transaction(() -> {
    blocks.checkWrite(artifact).ifPresent(refusal -> { throw new RefusedException(refusal); });
    RegistryPackage pkg = packages.create(format, namespace.getId(), "@acme/util", "@acme/util");
    PackageVersion version = packages.addVersion(pkg.getId(), "1.2.0", CurrentPrincipal.get());
    blobs.record(digest, size);
    packages.addFile(version.getId(), "util-1.2.0.tgz", digest, size);
    return version;
});
```

## Security

- **Threats**: one organization's names, blobs or packages reaching another; a published version
  replaced under the same name; a blocked or deleted artifact served or published again
- **Untrusted input**: names and versions from requests, always bound as parameters
- **Defaults**: unknown namespaces are private (REG-5); a name no namespace owns can't be
  published
- **Protections**: row-level security and an explicit organization filter on every query
  (REG-6); immutable versions and files (REG-3); blocks and tombstones in the writing
  transaction (REG-4)

## Testing

- **Contract suite**: none
- **Fuzz targets**: none; it parses no input
- **Other**: PostgreSQL in Testcontainers, with two organizations. The `KilnRegistry` test fixture
  builds the services over a test database, for formats' harnesses

```bash
./gradlew :kiln-registry:test
```

The tests need Docker.

## Standards and references

- **Standards**: none of its own
- **Dependencies**: Spring Data JDBC, through `kiln-database`
- **Reference projects**: Pulp's master and detail content tables, Gitea's packages, versions,
  files and blobs, Harbor's artifacts and tags, Nexus's components and assets

## Status

Implemented: REG-1 to REG-9.

Planned: the admin API and resources that create namespaces, blocks and deletes; blocks by version
range, digest, malware feed and scanner policy, instance-wide blocks, lifting a block, and tag
tombstones, whose tables exist; GC of unreferenced blobs; repository-level visibility.
