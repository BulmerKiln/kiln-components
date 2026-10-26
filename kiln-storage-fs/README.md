# kiln-storage-fs

The `fs` storage backend: blobs on a local or shared filesystem.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-storage`](../kiln-storage) |
| Artifact | `com.bulmerkiln:kiln-storage-fs` |
| Runs in | Kiln's server |
| Depends on | [`kiln-storage`](../kiln-storage) |
| Used by | `kiln-oss-service` |
| Status | M1, Experimental |

## Purpose

A filesystem, usually one persistent volume, is the simplest place to keep blobs and is enough for
a small installation. This backend stores them with an atomic, crash-safe layout that matches the
S3 backend's keys, so moving to object storage later means copying files, not converting them.

## Responsibilities

**Does**

- Store blobs and uploads under one root, separated by organization
- Verify uploads by re-hashing at commit, so sessions survive restarts
- Move finished uploads into place with an atomic rename, and serve reads with zero-copy transfer

**Does not**

- Decide which backend runs; `kiln.storage.type` does
- Work across replicas on storage without atomic rename

## Requirements

Inherits every requirement of [`kiln-storage`](../kiln-storage#requirements), and adds:

| ID | Requirement | Verified by |
|---|---|---|
| STO-FS-1 | Blobs must be stored at `<root>/<org-id>/blobs/<algorithm>/<first two hex>/<hex>` and uploads at `<root>/<org-id>/_uploads/<uuid>`, with every path built from a validated `Digest`, `UploadId` or organization ID, never from client text | `FsBlobStoreTest` |
| STO-FS-2 | The store must check at startup that the filesystem supports atomic rename, which several replicas sharing it rely on, and leave nothing behind from the check | `FsBlobStoreTest` (the probe leaves nothing) |
| STO-FS-3 | The root must be normalized, so `x/../store` means `store`, and a root that's a file must stop the boot | `FsBlobStoreTest`, `FsStorageAutoConfigurationTest` |
| STO-FS-4 | Listing must skip anything in the tree that isn't a blob: stray files, unknown algorithms, misplaced names | `FsBlobStoreTest` |
| STO-FS-5 | `kiln.storage.fs.root` must be required when `fs` is selected, and nothing may be touched on disk when another backend is selected | `FsStorageAutoConfigurationTest` |

## How it plugs in *(implementation only)*

Registers the `fs` `StorageBackend`; `kiln.storage.type=fs` selects it. It passes
`BlobStoreContract` in `FsBlobStoreTest`.

Beyond the contract: each chunk is flushed to disk before `append` returns; a committed upload is
flushed before the rename, and its directory after it (best effort, since Windows can't open
directories). Two locks guard each session: an in-process lock, because a JVM can hold only one
file lock per file, and a file lock for other processes. Reads use `FileChannel.transferTo`, so
bytes never pass through the heap.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.storage.fs.root` | none, required | The directory holding every organization's blobs and uploads. With several replicas, a shared filesystem with atomic rename (NFS, CephFS) |

## Usage

```yaml
kiln:
  storage:
    type: fs
    fs:
      root: /var/lib/kiln/blobs
```

## Security

- **Threats**: path traversal into another organization's directory or out of the root; a
  filesystem that can't rename atomically corrupting blobs under concurrent replicas
- **Untrusted input**: none beyond what `kiln-storage` validates
- **Defaults**: a missing root, a root that's a file, or a filesystem without atomic rename stops
  the boot
- **Protections**: paths only from validated types (STO-FS-1); the startup rename check (STO-FS-2)

## Testing

- **Contract suite**: passes `BlobStoreContract`
- **Fuzz targets**: none of its own
- **Other**: tests use JUnit temporary directories

```bash
./gradlew :kiln-storage-fs:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: POSIX `rename(2)` atomicity
- **Dependencies**: `java.nio` (`FileChannel`, `Files.move` with `ATOMIC_MOVE`)
- **Reference projects**: distribution/distribution's filesystem driver

## Status

Implemented: STO-FS-1 to STO-FS-5.

Planned: a test for the refusal path of STO-FS-2, which needs a filesystem without atomic rename;
`kiln doctor`'s storage checks.
