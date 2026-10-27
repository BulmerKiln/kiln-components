# kiln-storage-s3

The `s3` storage backend: blobs in S3 or an S3-compatible store. Its layout, settings and redirect
rules are built; the store itself comes next.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-storage`](../kiln-storage) |
| Artifact | `com.bulmerkiln:kiln-storage-s3` |
| Runs in | Kiln's server |
| Depends on | [`kiln-storage`](../kiln-storage) |
| Used by | `kiln-oss-service` (with several replicas), `kiln-cloud-service` |
| Status | M3, Experimental: settings and rules only, no `BlobStore` yet |

## Purpose

Object storage is the backend for several replicas and large installations: it scales, and it
can serve downloads itself through presigned URLs so the bytes never pass through Kiln. This
module fixes how keys are laid out, how it's configured, and which clients may be redirected.

## Responsibilities

**Does**

- Lay out object keys (`S3Keys`) the same way the filesystem backend lays out paths
- Validate its settings (`S3StorageProperties`)
- Decide which downloads redirect to a presigned URL and which are proxied (`RedirectPolicy`)

**Does not**

- Store anything yet: the `BlobStore` and its auto-configuration come with the AWS SDK
- Provision buckets per organization; that's kiln-cloud's `StorageRouter`

## Requirements

Inherits every requirement of [`kiln-storage`](../kiln-storage#requirements) once its store
exists, and adds:

| ID | Requirement | Verified by |
|---|---|---|
| STO-S3-1 | Keys must mirror the filesystem layout, `[prefix/]<organization-id>/blobs/<algorithm>/<ab>/<hex>` and `[prefix/]<organization-id>/_uploads/<uuid>`, so blobs copy between backends unchanged | `S3StorageTest` |
| STO-S3-2 | No organization's keys may fall under another's prefix | `S3StorageTest` |
| STO-S3-3 | A key prefix must be lowercase segments separated by single slashes: no leading or trailing slash, no empty, dot or `..` segment | `S3StorageTest` |
| STO-S3-4 | In `auto` mode, downloads may redirect only for clients known to follow a redirect without forwarding `Authorization` to storage, matched by the exact product name at the start of `User-Agent`: `docker`, `containerd`, `buildkit`, `containers`, `podman`, `skopeo`, `buildah`, `crane`, `go-containerregistry`, `oras`, `Helm`, `npm`, `Apache-Maven`, `Gradle`. Every other client is proxied. `always` and `never` override the list | `S3StorageTest` |
| STO-S3-5 | Presigned URLs must live 1 to 60 seconds, 60 by default. That lifetime is the only window in which someone given a URL just before a block can still fetch the blob | `S3StorageTest` |
| STO-S3-6 | Bucket names must follow S3's rules without dots (dotted names break virtual-hosted TLS), and must not start with `xn--` or end with `-s3alias` | `S3StorageTest` |
| STO-S3-7 | A custom endpoint must be `https` without credentials in the URL. Plain `http` is allowed only with `allow-insecure-endpoint`, for a store on the same host | `S3StorageTest` |

## How it plugs in *(implementation only)*

Will register the `s3` `StorageBackend`, selected by `kiln.storage.type=s3`, and pass
`BlobStoreContract`. Until the store exists, the settings can be validated with
`S3StorageProperties.validate()`.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.storage.s3.bucket` | none, required | The bucket |
| `kiln.storage.s3.region` | none | The region |
| `kiln.storage.s3.endpoint` | AWS | An S3-compatible endpoint, such as SeaweedFS or Ceph RGW |
| `kiln.storage.s3.path-style` | `false` | Path-style addressing, which most S3-compatible stores need |
| `kiln.storage.s3.prefix` | none | A key prefix within the bucket |
| `kiln.storage.s3.redirect` | `auto` | `auto`, `always` or `never` |
| `kiln.storage.s3.presign-ttl` | `60s` | Presigned URL lifetime, 1 to 60 seconds |
| `kiln.storage.s3.allow-insecure-endpoint` | `false` | Allows an `http` endpoint |

Credentials will come from the AWS SDK's default chain or secret references, never from these
properties.

## Usage

```yaml
kiln:
  storage:
    type: s3
    s3:
      bucket: kiln-blobs
      region: eu-west-1
```

## Security

- **Threats**: Kiln credentials forwarded to storage by a client following a redirect; a
  presigned URL used after a block; one organization's keys reachable from another's prefix;
  credentials leaked through an endpoint URL
- **Untrusted input**: the `User-Agent` header, which only chooses between redirecting and
  proxying; spoofing it can't grant access
- **Defaults**: `auto` redirects, 60-second URLs, `https` endpoints only
- **Protections**: the redirect allowlist (STO-S3-4), short-lived URLs (STO-S3-5), separate key
  prefixes (STO-S3-2), endpoint checks (STO-S3-7)

## Testing

- **Contract suite**: will pass `BlobStoreContract` once the store exists
- **Fuzz targets**: none
- **Other**: none

```bash
./gradlew :kiln-storage-s3:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: the [Amazon S3 API](https://docs.aws.amazon.com/AmazonS3/latest/API/), [presigned URLs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/ShareObjectPreSignedURL.html), [bucket naming rules](https://docs.aws.amazon.com/AmazonS3/latest/userguide/bucketnamingrules.html)
- **Dependencies**: the AWS SDK for Java v2 (with the store)
- **Reference projects**: distribution/distribution's S3 driver and redirect middleware

## Status

Implemented: STO-S3-1 to STO-S3-7.

Planned (M3): the `BlobStore` on the AWS SDK for Java v2, with multipart uploads and presigned
redirects; its auto-configuration; and running `BlobStoreContract` against it without Docker in
the unit tests, either with an in-process S3 fake or as a separate integration-test task.
