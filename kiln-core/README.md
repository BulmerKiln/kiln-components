# kiln-core

The shared kernel: the organization context, content digests and the artifact-format identifier.

## At a glance

| | |
|---|---|
| Kind | Foundation |
| Artifact | `com.bulmerkiln:kiln-core` |
| Runs in | Kiln's server and the scan worker |
| Depends on | Nothing but the JDK |
| Used by | Every subsystem |
| Status | M1, Experimental |

## Purpose

Every subsystem needs to know which organization it's working for and how to name content. This
module holds exactly those shared values and nothing else. No subsystem lives here, so any
subsystem can be replaced or tested on its own.

## Responsibilities

**Does**

- `OrgContext`: the organization the current work runs for
- `Org`, `OrgId`: organizations and their slugs, including the reserved slugs
- `Digest`, `DigestAlgorithm`, `Digester`: content digests
- `ArtifactFormat`: the open identifier formats use (`oci`, `npm`, ...)

**Does not**

- Hold any subsystem: storage, formats, authentication and permissions are modules of their own
- Know which formats exist: each format module defines its own `ArtifactFormat`

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| CORE-1 | Reading the current organization outside a scope must fail, never default. This is the same rule as Postgres's `current_setting('app.org_id')` without `missing_ok`: no organization means an error, not every organization | `OrgContextTest` |
| CORE-2 | A scope must never switch to a different organization, including one with the same slug and a different ID. Re-entering the same organization is allowed | `OrgContextTest` |
| CORE-3 | The organization must not leak past its scope: not after it throws, and not into threads or async tasks started inside it. Concurrent scopes each see only their own organization | `OrgContextTest` |
| CORE-4 | Organization slugs must be lowercase letters and digits with single inner hyphens, at most 39 characters, and never a reserved slug: `v2`, `npm`, `maven`, `helm`, `cargo`, `ui`, `api`, `auth`, `token`, `scim`, `login`, `.well-known`, `metrics`, `healthz`, `admin`, `status`, `docs`, `static`, `assets`, `kiln`, `bulmer`. Slugs share the registry's path space with Kiln's own routes, so the list only grows, and a new route may only take a name already reserved | `OrgTest` |
| CORE-5 | Slugs must refuse lookalikes and encodings: other scripts, fullwidth letters, zero-width and control characters, whitespace, `%`, `/`, `\` | `OrgTest` |
| CORE-6 | Digests must use a registered algorithm (`sha256` or `sha512`) with its exact lowercase hex encoding. A well-formed digest with another algorithm must be reported as unsupported, distinct from malformed | `DigestTest` |
| CORE-7 | A `Digest` must be impossible to construct in an invalid form, so it's always safe to use in a storage path | `DigestTest` |
| CORE-8 | Digest parsing must refuse inputs over 256 characters before matching, and must never throw anything but `InvalidDigestException` | `DigestTest` (fuzz target) |
| CORE-9 | Digests must match the published FIPS 180 test vectors, and hashing in chunks must give the same digest as hashing at once | `DigesterTest` |
| CORE-10 | Format IDs must be `[a-z][a-z0-9-]{0,31}`, safe as metric labels and column values. They're open values, so adding a format never changes this module | `ArtifactFormatTest` |


## Configuration

None.

## Usage

```kotlin
OrgContext.run(org, () -> blobStore.stat(digest));

Digest digest = Digest.parse("sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
```

The web layer binds the organization for each request; jobs that cover several organizations
open one scope per organization, one after another.

## Security

- **Threats**: a request or job acting for the wrong organization; crafted slugs that collide
  with routes or look like another organization's; digests crafted to escape a storage path
- **Untrusted input**: digest strings and slugs from URLs and request bodies
- **Defaults**: no organization in context is an error (CORE-1)
- **Protections**: scoped values that can't leak or switch (CORE-2, CORE-3), strict slugs
  (CORE-4, CORE-5), digests that can't be invalid (CORE-7, CORE-8)

## Testing

- **Contract suite**: none
- **Fuzz targets**: digest parsing (`DigestTest`)
- **Other**: jqwik property tests for digest round-trips and chunked hashing

```bash
./gradlew :kiln-core:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: [OCI image-spec, descriptors](https://github.com/opencontainers/image-spec/blob/main/descriptor.md) (digest grammar, registered algorithms); FIPS 180-4 (SHA-2); [JEP 506](https://openjdk.org/jeps/506) (scoped values)
- **Dependencies**: the JDK only
- **Reference projects**: distribution/distribution and go-containerregistry (digest handling)

## Status

Implemented: CORE-1 to CORE-10.
