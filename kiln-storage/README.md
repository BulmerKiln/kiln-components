# kiln-storage

The storage subsystem: the contract every blob backend implements, backend selection, and
per-organization routing.

## At a glance

| | |
|---|---|
| Kind | Subsystem |
| Artifact | `com.bulmerkiln:kiln-storage` (plus a `test-fixtures` variant) |
| Runs in | Kiln's server |
| Depends on | [`kiln-core`](../kiln-core) |
| Used by | [`kiln-storage-fs`](../kiln-storage-fs), [`kiln-storage-s3`](../kiln-storage-s3); formats, uploads and GC as they're built |
| Status | M1, Experimental |

## Purpose

Blobs are the bytes behind every artifact: image layers, tarballs, jars. Everything that stores
or serves them goes through one narrow contract, so a backend can be swapped (a filesystem now,
S3 later) without touching the formats, and every backend is held to the same behaviour by one
shared test suite.

## Responsibilities

**Does**

- Define `BlobStore`: resumable uploads, verified commits, ranged reads, delete and list
- Define `StorageRouter`, the only way to reach a store, and select the backend
- Provide `BlobStoreContract`, the suite every backend passes, and `InMemoryBlobStore` for other modules' tests
- Parse `Range` headers (`ByteRange`) and upload IDs (`UploadId`)

**Does not**

- Store anything itself; backends do
- Decide who may read or write a blob (permissions), or track which blobs are referenced (uploads
  and GC, built on top of this module)

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| STO-1 | Every blob operation must act on the organization in `OrganizationContext`, and fail outside one | `BlobStoreContract` |
| STO-2 | Blobs and uploads must never be shared across organizations. The same content is stored separately for each, and no organization can read, abort or delete another's. Deduplicating across organizations would reveal that another organization holds a blob | `BlobStoreContract` |
| STO-3 | A chunk must be applied entirely or not at all: if the upload would exceed its size limit, or the client disconnects mid-chunk, the session is left as it was | `BlobStoreContract` |
| STO-4 | A chunk must start exactly where the upload ends. A gap or an overlap is refused, reporting the offset to resume from | `BlobStoreContract` |
| STO-5 | Committing must verify the content against the declared digest. On a mismatch the upload is deleted and nothing is stored | `BlobStoreContract` |
| STO-6 | Committing content that's already stored must succeed and keep the existing blob, including when many clients push the same content at once | `BlobStoreContract` |
| STO-7 | Concurrent chunks at the same offset must apply exactly once | `BlobStoreContract` |
| STO-8 | An upload session must survive a restart: another store opened on the same storage continues it | `BlobStoreContract` |
| STO-9 | Reads must support one inclusive byte range, and refuse a range beyond the blob with its size, for `416` | `BlobStoreContract` |
| STO-10 | `Range` headers must be read as RFC 9110 says: a header that can't be parsed, uses another unit or asks for several ranges is ignored and the whole blob served; a range starting past the end, or any range of an empty blob, is unsatisfiable. Several ranges are never served, which also stops overlapping-range amplification | `ByteRangeTest` (fuzz and property tests) |
| STO-11 | Upload IDs from URLs must be accepted only in canonical lowercase UUID form, so nothing else reaches a storage key | `BlobTypesTest` |
| STO-12 | `kiln.storage.type` must select exactly one backend. Startup fails, naming the available types, when it's unset, unknown, or claimed by two backends, or when no backend is on the classpath | `StorageSelectionTest` |
| STO-13 | Stores must be reachable only through `StorageRouter`, never as a bean of their own. When the assembly provides its own router (kiln-cloud's per-organization buckets), no store is created here | `StorageSelectionTest` |
| STO-14 | Every backend must pass `BlobStoreContract` | `InMemoryBlobStoreTest`, `FsBlobStoreTest` |

## Contract *(subsystem only)*

| Type | Role |
|---|---|
| `BlobStore` | The storage operations a backend implements |
| `StorageBackend` | A backend module's entry: its `type` for `kiln.storage.type`, and how to create its store |
| `StorageRouter` | Finds the store for an organization; kiln-oss uses `SingleStoreRouter`, kiln-cloud its own |
| `BlobRead`, `BlobInfo`, `UploadId`, `ByteRange` | The values the operations take and return |
| `BlobStoreContract` *(test fixtures)* | The suite every backend extends |
| `InMemoryBlobStore` *(test fixtures)* | A store for other modules' tests, passing the same suite |

**Rules enforced here**: one selected backend (STO-12); no access except through the router
(STO-13); one behaviour for every backend, proved by the shared suite (STO-14).

## Adding an implementation *(subsystem only)*

1. Create `kiln-storage-<name>` and depend on `kiln-storage`.
2. Implement `BlobStore`. Scope every key by `OrganizationContext.current().id()`.
3. Register a `StorageBackend` bean with a new type from a Spring Boot auto-configuration. Validate
   the backend's own settings in `create()`, which runs only when the backend is selected.
4. Extend `BlobStoreContract` in its tests, implementing `newStore()` so a second call opens the
   same storage, as a restarted replica would.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.storage.type` | none, required | The backend: `fs` or `s3` |

## Usage

```java
BlobStore store = storageRouter.store(OrganizationContext.current());
UploadId upload = store.createUpload();
store.append(upload, 0, body, maxBlobSize);
BlobInfo blob = store.commit(upload, digest);
```

## Security

- **Threats**: one organization reading or deleting another's blobs; a crafted upload ID or
  digest reaching a storage path; content that doesn't match its digest; resource exhaustion
  through oversized or concurrent uploads; `Range` abuse
- **Untrusted input**: upload IDs, digests, offsets, chunk bodies and `Range` headers from clients
- **Defaults**: no organization in context is an error (STO-1); an unset `kiln.storage.type`
  stops the boot (STO-12)
- **Protections**: per-organization isolation (STO-2), atomic chunks and size limits (STO-3),
  verified commits (STO-5), canonical IDs (STO-11), single ranges only (STO-10)

## Testing

- **Contract suite**: `BlobStoreContract` (test fixtures), run here against `InMemoryBlobStore`
- **Fuzz targets**: `Range` parsing (`ByteRangeTest`)
- **Other**: jqwik property tests for ranges

```bash
./gradlew :kiln-storage:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110) sections 14.1 and 14.2 (ranges); the [OCI Distribution Spec](https://github.com/opencontainers/distribution-spec) upload semantics
- **Dependencies**: Spring Boot auto-configuration
- **Reference projects**: distribution/distribution's storage drivers, Zot

## Status

Implemented: STO-1 to STO-14.

Planned: upload sessions and quota reservations in Postgres, the GC sweep of expired uploads and
unreferenced blobs (M1); presigned-redirect serving (M3, with `kiln-storage-s3`).
