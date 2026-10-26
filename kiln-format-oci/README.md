# kiln-format-oci

The OCI format: container images, Helm charts and other OCI artifacts, on `/v2/`.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-format`](../kiln-format) |
| Artifact | `com.bulmerkiln:kiln-format-oci` |
| Runs in | Kiln's server |
| Depends on | [`kiln-format`](../kiln-format), Jackson |
| Used by | `kiln-oss-service`, `kiln-cloud-service` |
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

**Does not**

- Serve HTTP yet; the `/v2/` endpoints come next
- Store manifests or blobs; it hands validated raw bytes to the layers that do

## Requirements

Inherits every requirement of [`kiln-format`](../kiln-format#requirements), and adds:

| ID | Requirement | Verified by |
|---|---|---|
| FMT-OCI-1 | Repository names must match the distribution-spec grammar (lowercase components separated by `.`, `_`, `__` or dashes, joined by single `/`) and be at most 255 characters. Encoded slashes, dot segments, whitespace, lookalikes, ports and digests are refused, and pathological names are refused quickly | `NamesTest` (fuzz target) |
| FMT-OCI-2 | Tags must match `[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}`. Any reference containing `:` is a digest, and a malformed or unsupported one is `DIGEST_INVALID` | `NamesTest` |
| FMT-OCI-3 | A manifest's digest must be the SHA-256 of the exact bytes pushed, and the bytes are never re-serialized | `ManifestParserTest` |
| FMT-OCI-4 | Manifests must be at most 4 MiB by default, checked before parsing, or `SIZE_INVALID` | `ManifestParserTest`, `ManifestParserAttackTest` |
| FMT-OCI-5 | OCI image manifests and indexes, and Docker v2 manifests and manifest lists, must be accepted. Docker schema 1 is `UNSUPPORTED`; any other media type is `MANIFEST_INVALID` | `ManifestParserTest` |
| FMT-OCI-6 | When a manifest declares `mediaType`, it must equal the `Content-Type` (parameters ignored); without one, the `Content-Type` decides | `ManifestParserTest` |
| FMT-OCI-7 | Manifests must be strict JSON that Go clients read identically: UTF-8 only, no byte-order mark, no duplicate keys (including ones hidden by `\u` escapes), nothing after the value, and no coercion. `schemaVersion` is the integer 2, and sizes are plain integers that fit a `long` | `ManifestParserAttackTest` |
| FMT-OCI-8 | An image manifest must have a config and no `manifests`; an index must have `manifests` and no config or layers. Every descriptor needs a `mediaType`, a valid digest and a non-negative size | `ManifestParserTest`, `ManifestParserAttackTest` |
| FMT-OCI-9 | Deeply nested JSON must be refused quickly, and manifests with many descriptors handled up to the size limit | `ManifestParserAttackTest` |
| FMT-OCI-10 | Errors must use distribution's codes and statuses: `BLOB_UNKNOWN` 404, `BLOB_UPLOAD_INVALID` 400, `BLOB_UPLOAD_UNKNOWN` 404, `DIGEST_INVALID` 400, `MANIFEST_BLOB_UNKNOWN` 400, `MANIFEST_INVALID` 400, `MANIFEST_UNKNOWN` 404, `NAME_INVALID` 400, `NAME_UNKNOWN` 404, `SIZE_INVALID` 400, `UNAUTHORIZED` 401, `DENIED` 403, `UNSUPPORTED` 405, `TOOMANYREQUESTS` 429 | `OciErrorCodeTest` |
| FMT-OCI-11 | A code hint must be used only when it names a code with the refusal's own reason, so a block can never go out as `BLOB_UNKNOWN`. Without a usable hint: blocked, over quota, conflict and denied are `DENIED`; not found is `NAME_UNKNOWN` | `OciFormatModuleTest` |
| FMT-OCI-12 | Errors must use the spec envelope, `{"errors":[{"code":...,"message":...}]}`, in that field order, with `Docker-Distribution-API-Version: registry/2.0` | `OciFormatModuleTest` |
| FMT-OCI-13 | Errors must never echo the manifest | `ManifestParserAttackTest` |

## How it plugs in *(implementation only)*

Registers `OciFormatModule` (format `oci`, route `/v2/`), switched by `kiln.format.oci.enabled`.
It passes `FormatModuleContract` in `OciFormatModuleTest`. Refusals come from `OciException`,
which carries a spec code.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.format.oci.enabled` | `true` | Switches the format off |

## Usage

```java
Manifest manifest = new ManifestParser(ManifestParser.DEFAULT_MAX_SIZE).parse(body, contentType);
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
- **Fuzz targets**: names and references (`NamesTest`), manifests (`ManifestParserTest`)
- **Other**: `ManifestParserAttackTest` collects the crafted manifests

```bash
./gradlew :kiln-format-oci:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: [OCI Distribution Spec 1.1](https://github.com/opencontainers/distribution-spec/blob/main/spec.md) (names, tags, error codes); [OCI Image Spec 1.1](https://github.com/opencontainers/image-spec) (manifests, indexes, descriptors); [RFC 8259](https://www.rfc-editor.org/rfc/rfc8259) section 8.1 (JSON is UTF-8)
- **Dependencies**: Jackson, for validation only
- **Reference projects**: distribution/distribution, Zot, go-containerregistry, Harbor

## Status

Implemented: FMT-OCI-1 to FMT-OCI-13.

Planned (M1): the `/v2/` endpoints (pull, push, chunked and monolithic uploads, cross-repository
mounts, tag listing, deletes), immutable tags and tombstones, and the OCI conformance suite as the
acceptance test. Later: the referrers API and cosign (1.x), the classic Helm `index.yaml` (1.x).
