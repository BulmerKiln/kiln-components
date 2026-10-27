# kiln-registry

What every format stores alike: namespaces, the blobs an organization holds, the packages of the
package formats with their versions and files, and blocks and tombstones.

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

- Find the namespace that owns a name in each format (`NamespaceStore`)
- Record the blobs an organization holds (`BlobRecordStore`)
- Keep packages, versions and files, immutable once written (`PackageStore`)
- Keep blocks and tombstones, and answer the formats' `ArtifactPolicy` port from them
- Answer the permissions' `VisibilityLookup` port from namespaces

**Does not**

- Speak any format's protocol, or interpret a format's metadata; formats store their own JSON
- Hold the bytes; the blob store does

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| REG-1 | A namespace must own an OCI prefix (`acme` owns `acme/*`), an npm scope, Maven groupId prefixes (`org.acme` owns `org.acme.*`) and Cargo crate-name prefixes. The longest matching prefix wins; a name no namespace owns has none. Namespaces are per organization, so two organizations can own the same names | `RegistryTest` |
| REG-2 | Recording a blob the organization already holds must be harmless, so concurrent pushes of the same content both succeed | `RegistryTest` |
| REG-3 | A version that exists, or a file of a version under a name that exists with other content, must be refused as a conflict. The same file again is a no-op, since clients retry. Two first publishes of a package must share one package. A format that publishes a version file by file ensures the version instead, and then adds immutable files to it | `RegistryTest` |
| REG-4 | A blocked artifact must be refused with `BLOCKED` (403, never 404) on read and write, for one version or every version; a tombstoned version must be refused as a conflict on write, forever. Formats check both inside the transaction that writes | `RegistryTest` |
| REG-5 | Visibility must come from the namespace; the organization itself and an unknown namespace are private | `RegistryTest` |
| REG-6 | Every registry table must pass the schema linter, and every query filters on the organization as well as relying on row-level security | `RegistryTest` |
| REG-7 | The registry must answer `ArtifactPolicy` and `VisibilityLookup` unless the assembly provides its own | `RegistryAutoConfigurationTest` |

## Contract *(subsystem only)*

| Type | Role |
|---|---|
| `NamespaceStore`, `Namespace` | `forOci`, `forNpm`, `forMaven`, `forCargo`, `byName`, `create` |
| `BlobRecordStore` | `record`, `size` |
| `PackageStore` | Packages, versions and files of the package formats |
| `BlockStore` | `block`, `tombstone`, `checkRead`, `checkWrite`, inside a transaction |
| `RegistryPolicy`, `NamespaceVisibilityLookup` | The ports' implementations |

Every method takes the caller's transaction (`DSLContext` from `Database.transaction`), so a format
checks, writes and records in one commit.

## Configuration

None.

## Usage

```java
database.transaction(tx -> {
    BlockStore.checkWrite(tx, artifact).ifPresent(refusal -> { throw new RefusedException(refusal); });
    PackageVersionEntity version = PackageStore.addVersion(tx, pkg.id(), "1.2.0", metadata);
    BlobRecordStore.record(tx, digest, size);
    PackageStore.addFile(tx, version.id(), "util-1.2.0.tgz", digest, size);
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
- **Other**: PostgreSQL in Testcontainers, with two organizations

```bash
./gradlew :kiln-registry:test
```

The tests need Docker.

## Standards and references

- **Standards**: none of its own
- **Dependencies**: jOOQ, through `kiln-database`
- **Reference projects**: Harbor's projects, Verdaccio's packages, Nexus's components and assets

## Status

Implemented: REG-1 to REG-7.

Planned: the admin API and resources that create namespaces, blocks and deletes; GC of
unreferenced blobs; repository-level visibility.
