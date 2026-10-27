# kiln-format-oci

The OCI format: container images, Helm charts and other OCI artifacts, on `/v2/`.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-format`](../kiln-format) |
| Artifact | `com.bulmerkiln:kiln-format-oci` |
| Runs in | Kiln's server |
| Depends on | [`kiln-format`](../kiln-format), [`kiln-web`](../kiln-web), [`kiln-registry`](../kiln-registry), [`kiln-database`](../kiln-database), [`kiln-storage`](../kiln-storage), Jackson |
| Used by | `kiln-oss-service` |
| Status | M1, Experimental: names, references, errors and manifest validation; no endpoints yet |

## Purpose

OCI is the format docker, containerd, Helm, oras and cosign speak. Manifests are identified by
the digest of their exact bytes, so this module validates what clients push without ever
re-encoding it, and parses strictly wherever two parsers could read one manifest differently.

## Responsibilities

**Does**

- Validate repository names, tags and references
- Validate pushed manifests and indexes, and list the blobs, child manifests and subject they reference
- Map refusals to the spec's error codes and envelope
- Serve the `/v2/` API: pulls, pushes, chunked, monolithic and single-request uploads,
  cross-repository mounts, tag listing, referrers and deletes, while the format is enabled
- Keep repositories, manifests (their exact bytes), tags and upload sessions, per organization

**Does not**

- Decide access or policy; it asks the `ArtifactAccess` and `ArtifactPolicy` ports first
- Hold blob bytes; the blob store does, and `kiln-registry` records which blobs an organization
  holds

## Requirements

Inherits every requirement of [`kiln-format`](../kiln-format#requirements), and adds:

| ID | Requirement | Verified by |
|---|---|---|
| FMT-OCI-1 | Repository names must match the distribution-spec grammar (lowercase components separated by `.`, `_`, `__` or dashes, joined by single `/`) and be at most 255 characters. Encoded slashes, dot segments, whitespace, lookalikes, ports and digests are refused, and pathological names are refused quickly | `OciNamesTest` (fuzz target) |
| FMT-OCI-2 | Tags must match `[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}`. Any reference containing `:` is a digest, and a malformed or unsupported one is `DIGEST_INVALID` | `OciNamesTest` |
| FMT-OCI-3 | A manifest's digest must be the SHA-256 of the exact bytes pushed, and the bytes are never re-serialized | `OciManifestParserTest` |
| FMT-OCI-4 | Manifests must be at most 4 MiB by default, checked before parsing, or `SIZE_INVALID` | `OciManifestParserTest`, `OciManifestParserAttackTest` |
| FMT-OCI-5 | OCI image manifests and indexes, and Docker v2 manifests and manifest lists, must be accepted. Docker schema 1 is `UNSUPPORTED`; any other media type is `MANIFEST_INVALID` | `OciManifestParserTest` |
| FMT-OCI-6 | When a manifest declares `mediaType`, it must equal the `Content-Type` (parameters ignored); without one, the `Content-Type` decides | `OciManifestParserTest` |
| FMT-OCI-7 | Manifests must be strict JSON that Go clients read identically: UTF-8 only, no byte-order mark, no duplicate keys (including ones hidden by `\u` escapes), nothing after the value, and no coercion. `schemaVersion` is the integer 2, and sizes are plain integers that fit a `long` | `OciManifestParserAttackTest` |
| FMT-OCI-8 | An image manifest must have a config and no `manifests`; an index must have `manifests` and no config or layers. Every descriptor needs a `mediaType`, a valid digest and a non-negative size | `OciManifestParserTest`, `OciManifestParserAttackTest` |
| FMT-OCI-9 | Deeply nested JSON must be refused quickly, and manifests with many descriptors handled up to the size limit | `OciManifestParserAttackTest` |
| FMT-OCI-10 | Errors must use distribution's codes and statuses: `BLOB_UNKNOWN` 404, `BLOB_UPLOAD_INVALID` 400, `BLOB_UPLOAD_UNKNOWN` 404, `DIGEST_INVALID` 400, `MANIFEST_BLOB_UNKNOWN` 400, `MANIFEST_INVALID` 400, `MANIFEST_UNKNOWN` 404, `NAME_INVALID` 400, `NAME_UNKNOWN` 404, `SIZE_INVALID` 400, `UNAUTHORIZED` 401, `DENIED` 403, `UNSUPPORTED` 405, `TOOMANYREQUESTS` 429 | `OciErrorCodeTest` |
| FMT-OCI-11 | A code hint must be used only when it names a code with the refusal's own reason, so a block can never go out as `BLOB_UNKNOWN`. Without a usable hint: blocked, over quota, conflict and denied are `DENIED`; not found is `NAME_UNKNOWN` | `OciFormatModuleTest` |
| FMT-OCI-12 | Errors must use the spec envelope, `{"errors":[{"code":...,"message":...}]}`, in that field order, with `Docker-Distribution-API-Version: registry/2.0` | `OciFormatModuleTest` |
| FMT-OCI-13 | Errors must never echo the manifest | `OciManifestParserAttackTest` |
| FMT-OCI-14 | Routes below `/v2/` must be read from the end of the path, so a repository name may contain `manifests`, `blobs` or `tags`. A path that's no route is not found; route parsing never throws anything but a refusal | `OciRouteTest` (fuzz target), `OciPushPullTest` |
| FMT-OCI-15 | Access must be decided before anything about the repository is looked up, so a refusal never reveals whether it exists: anonymous callers get `401`, members who may not know it `404`, others `403`. Pushing to a name no namespace owns is refused, even for an admin | `OciAccessServiceTest` |
| FMT-OCI-16 | A `401` must carry docker's challenge: `Bearer realm="<external URL>/token",service="<host>",scope="repository:<name as addressed>:<actions>"` | `OciAccessServiceTest` |
| FMT-OCI-17 | Manifests must be served exactly as pushed, by tag or digest, with their stored media type and `Docker-Content-Digest`, on `GET` and `HEAD`. A block on the tag or the digest is `403`, never `404` | `OciPushPullTest`, `OciAccessServiceTest` |
| FMT-OCI-18 | A manifest push must refuse with `MANIFEST_BLOB_UNKNOWN` any config, layer or child manifest the repository doesn't hold; a subject may be missing. A push with a subject answers `OCI-Subject` | `OciPushPullTest` |
| FMT-OCI-19 | A manifest pushed by digest must match its own content, or `DIGEST_INVALID`. Blocks and tombstones on the tag and digest are checked in the transaction that stores it | `OciPushPullTest`, `OciAccessServiceTest` |
| FMT-OCI-20 | Deleting a tag removes only the tag; deleting a manifest removes its tags too. A manifest an index still references can't be deleted (`409`) until the index is | `OciPushPullTest` |
| FMT-OCI-21 | A blob must be readable only through a repository it was pushed or mounted to, whole or by one `Range` (`206` with `Content-Range`, `416` beyond the end), with `Docker-Content-Digest` | `OciPushPullTest` |
| FMT-OCI-22 | Uploads must work as a session finished by `PUT`, with the last chunk in the `PUT` or not, and as one `POST ?digest=` | `OciUploadTest` |
| FMT-OCI-23 | A mount must link the blob only when the caller may pull from the source repository of the same organization and the blob is linked there; otherwise it's a plain upload (`202`) that reveals nothing | `OciUploadTest` |
| FMT-OCI-24 | Chunks must start where the upload ends: a gap or overlap is `416` and leaves the session as it was. `Range: 0-<last>` is answered on every `PATCH` and status `GET`; a blob past `max-blob-size` is `413` | `OciUploadTest` |
| FMT-OCI-25 | An upload session must be usable only through the repository that started it, by its canonical UUID; content that doesn't match its digest is `DIGEST_INVALID` and ends the session | `OciUploadTest` |
| FMT-OCI-26 | Tags must list in byte order, at most `tag-page-size` per page, with `n`, `last` and an RFC 5988 `Link` to the next page | `OciPushPullTest` |
| FMT-OCI-27 | The referrers API must list the manifests whose subject is the digest, as an OCI index, filterable by `artifactType` with `OCI-Filters-Applied`; an image's artifact type falls back to its config's media type | `OciPushPullTest` |
| FMT-OCI-28 | `GET /v2/` must answer `200 {}` with `Docker-Distribution-API-Version: registry/2.0`, which every response carries | `OciPushPullTest` |
| FMT-OCI-29 | `Content-Range` must be OCI's `<start>-<end>` with `end >= start`, and `n` a page size; anything else is `400` | `OciRouteTest`, `OciUploadTest` |
| FMT-OCI-30 | The `/v2/` routes must exist only in a web application while `kiln.format.oci.enabled` isn't `false`. Pushes and manifest pulls reach the event listeners | `OciWebAutoConfigurationTest`, `OciPushPullTest` |

## How it plugs in *(implementation only)*

Registers `OciFormatModule` (format `oci`, route `/v2/`), switched by `kiln.format.oci.enabled`.
It passes `FormatModuleContract` in `OciFormatModuleTest`. Refusals come from `OciException`,
which carries a spec code.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.format.oci.enabled` | `true` | Switches the format off: its `/v2/` routes don't exist |
| `kiln.format.oci.max-blob-size` | 10 GiB | The largest blob (layer) in bytes; larger is `413` |
| `kiln.format.oci.max-manifest-size` | 4 MiB | The largest manifest or index in bytes |
| `kiln.format.oci.tag-page-size` | `1000` | The most tags one page lists |

## Usage

```java
OciManifest manifest = new OciManifestParser(OciManifestParser.DEFAULT_MAX_SIZE).parse(body, contentType);
// manifest.blobs(), manifest.manifests() and manifest.subject() must exist before the push is stored
```

## Security

- **Threats**: parser differentials between Kiln and Go clients (duplicate keys, UTF-16,
  coercion), so a manifest means one thing to Kiln and another to docker; parser exhaustion;
  crafted names reaching another repository; a block disguised as "not found"
- **Untrusted input**: repository names, references, manifest bodies and `Content-Type`
- **Defaults**: anything not explicitly valid is refused
- **Protections**: FMT-OCI-1, FMT-OCI-2, FMT-OCI-4, FMT-OCI-7, FMT-OCI-9, FMT-OCI-11, FMT-OCI-13

## Testing

- **Contract suite**: passes `FormatModuleContract`
- **Fuzz targets**: names and references (`OciNamesTest`), manifests (`OciManifestParserTest`)
- **Other**: `OciManifestParserAttackTest` collects the crafted manifests

```bash
./gradlew :kiln-format-oci:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: [OCI Distribution Spec 1.1](https://github.com/opencontainers/distribution-spec/blob/main/spec.md) (names, tags, error codes); [OCI Image Spec 1.1](https://github.com/opencontainers/image-spec) (manifests, indexes, descriptors); [RFC 8259](https://www.rfc-editor.org/rfc/rfc8259) section 8.1 (JSON is UTF-8)
- **Dependencies**: Jackson, for validation only
- **Reference projects**: distribution/distribution, Zot, go-containerregistry, Harbor

## Status

Implemented: FMT-OCI-1 to FMT-OCI-30.

Planned (M1): immutable tag policies, the token service behind the challenge, and the OCI
conformance suite as the acceptance test. Later: the referrers fallback tag for older cosign, and
the classic Helm `index.yaml` (1.x).
