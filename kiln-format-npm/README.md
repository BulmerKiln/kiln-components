# kiln-format-npm

The npm format: npm, pnpm and yarn packages, on `/npm/`.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-format`](../kiln-format) |
| Artifact | `com.bulmerkiln:kiln-format-npm` |
| Runs in | Kiln's server |
| Depends on | [`kiln-format`](../kiln-format) |
| Used by | `kiln-oss-service`, `kiln-cloud-service` |
| Status | M2, Experimental: names, integrity checks and errors; no endpoints yet |

## Purpose

npm packages are named, scoped and published in one JSON request with the tarball inside. This
module validates names the way npm does for new packages, accepts scoped names however clients
encode them, and checks every published tarball against the hashes the client declared.

## Responsibilities

**Does**

- Validate package names (`PackageName`), including the URL forms clients send
- Verify tarballs against `dist.integrity` and `dist.shasum` (`DistVerifier`)
- Render refusals in npm's error body

**Does not**

- Serve HTTP yet: packuments, publish, dist-tags, web login and audit come next
- Store packages; it hands verified tarballs to the layers that do

## Requirements

Inherits every requirement of [`kiln-format`](../kiln-format#requirements), and adds:

| ID | Requirement | Verified by |
|---|---|---|
| FMT-NPM-1 | Package names must follow npm's rules for new packages: lowercase, URL-safe, not starting with `.` or `_`, none of `~'!()*`, not `node_modules` or `favicon.ico`, and at most 214 characters including the scope | `PackageNameTest` (fuzz target) |
| FMT-NPM-2 | Scoped names must be accepted as `@scope/name`, `@scope%2fname` or `@scope%2Fname`, and no other escape: a double-encoded or otherwise escaped name is refused, so it can't slip past validation | `PackageNameTest` |
| FMT-NPM-3 | Tarball URLs must use the encoded form, `@scope%2fname` | `PackageNameTest` |
| FMT-NPM-4 | `dist.integrity` must include a sha512 hash, and one listed sha512 must match the tarball exactly (64 bytes). Weaker algorithms in the list are ignored, as Subresource Integrity uses only the strongest | `DistVerifierTest` |
| FMT-NPM-5 | When `dist.shasum` is present, it must be the lowercase hex SHA-1 of the tarball | `DistVerifierTest` |
| FMT-NPM-6 | Errors must be npm's `{"error": "<message>"}` with `application/json` | `NpmFormatModuleTest` |

## How it plugs in *(implementation only)*

Registers `NpmFormatModule` (format `npm`, route `/npm/`), switched by `kiln.format.npm.enabled`.
It passes `FormatModuleContract` in `NpmFormatModuleTest`.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.format.npm.enabled` | `true` | Switches the format off |

## Usage

```java
PackageName name = PackageName.fromPath(rawPathSegment);

DistVerifier verifier = new DistVerifier();
verifier.update(chunk, 0, length); // as the tarball streams in
verifier.verify(integrity, shasum);
```

## Security

- **Threats**: names crafted to dodge validation through encoding; tarballs that don't match their
  declared hashes; hash downgrades to weaker algorithms
- **Untrusted input**: package names in paths and bodies, published tarballs, `dist` fields
- **Defaults**: a publish without sha512 integrity is refused
- **Protections**: FMT-NPM-1, FMT-NPM-2, FMT-NPM-4, FMT-NPM-5

## Testing

- **Contract suite**: passes `FormatModuleContract`
- **Fuzz targets**: package names (`PackageNameTest`)
- **Other**: none

```bash
./gradlew :kiln-format-npm:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: the [npm registry API](https://github.com/npm/registry/blob/main/docs/REGISTRY-API.md); [validate-npm-package-name](https://github.com/npm/validate-npm-package-name); [W3C Subresource Integrity](https://www.w3.org/TR/SRI/) section 3.3
- **Dependencies**: the JDK's `MessageDigest`
- **Reference projects**: Verdaccio, Gitea/Forgejo's npm registry, the npm CLI

## Status

Implemented: FMT-NPM-1 to FMT-NPM-6.

Planned (M2): packuments (full and abbreviated), publish with streaming base64 decoding,
immutable versions, dist-tags, deprecate, web login, `whoami` and `ping`, audit from a local OSV
mirror, registry signatures and provenance. The real npm, pnpm and yarn are the acceptance test.
