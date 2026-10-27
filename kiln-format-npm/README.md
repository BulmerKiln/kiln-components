# kiln-format-npm

The npm format: npm, pnpm and yarn packages, on `/npm/`.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-format`](../kiln-format) |
| Artifact | `com.bulmerkiln:kiln-format-npm` |
| Runs in | Kiln's server |
| Depends on | [`kiln-format`](../kiln-format), [`kiln-web`](../kiln-web), [`kiln-registry`](../kiln-registry), [`kiln-database`](../kiln-database), [`kiln-storage`](../kiln-storage), [`kiln-permissions`](../kiln-permissions), Jackson |
| Used by | `kiln-oss-service` |
| Status | M2, Experimental: names, integrity checks and errors; no endpoints yet |

## Purpose

npm packages are named, scoped and published in one JSON request with the tarball inside. This
module validates names the way npm does for new packages, accepts scoped names however clients
encode them, and checks every published tarball against the hashes the client declared.

## Responsibilities

**Does**

- Validate package names (`NpmPackageName`), including the URL forms clients send
- Verify tarballs against `dist.integrity` and `dist.shasum` (`NpmDistVerifier`)
- Render refusals in npm's error body
- Serve the `/npm/` registry while the format is enabled: packuments (full and abbreviated),
  versions, tarballs, publish, deprecate, dist-tags, `ping` and `whoami`
- Answer the npm commands Kiln doesn't serve with guidance, never a `5xx`

**Does not**

- Decide access or policy; it asks the `ArtifactAccess` and `ArtifactPolicy` ports first
- Keep its own tables; packages, versions and files live in `kiln-registry`

## Requirements

Inherits every requirement of [`kiln-format`](../kiln-format#requirements), and adds:

| ID | Requirement | Verified by |
|---|---|---|
| FMT-NPM-1 | Package names must follow npm's rules for new packages: lowercase, URL-safe, not starting with `.` or `_`, none of `~'!()*`, not `node_modules` or `favicon.ico`, and at most 214 characters including the scope | `NpmPackageNameTest` (fuzz target) |
| FMT-NPM-2 | Scoped names must be accepted as `@scope/name`, `@scope%2fname` or `@scope%2Fname`, and no other escape: a double-encoded or otherwise escaped name is refused, so it can't slip past validation | `NpmPackageNameTest` |
| FMT-NPM-3 | Tarball URLs must use the encoded form, `@scope%2fname` | `NpmPackageNameTest` |
| FMT-NPM-4 | `dist.integrity` must include a sha512 hash, and one listed sha512 must match the tarball exactly (64 bytes). Weaker algorithms in the list are ignored, as Subresource Integrity uses only the strongest | `NpmDistVerifierTest` |
| FMT-NPM-5 | When `dist.shasum` is present, it must be the lowercase hex SHA-1 of the tarball | `NpmDistVerifierTest` |
| FMT-NPM-6 | Errors must be npm's `{"error": "<message>"}` with `application/json` | `NpmFormatModuleTest` |
| FMT-NPM-7 | Routes below `/npm/` must take a scoped name as `@scope/name` or `@scope%2fname` (either case of `%2f`), and route parsing never throws anything but a refusal | `NpmRouteTest` (fuzz target), `NpmRegistryTest` |
| FMT-NPM-8 | Versions must be strict SemVer 2.0.0, at most 256 characters; a dist-tag can't look like a version | `NpmRouteTest`, `NpmRegistryTest` |
| FMT-NPM-9 | Access must be decided before anything about the package is looked up. A scoped package belongs to the namespace that owns its scope; publishing needs one, so an unscoped or unowned name can't be published | `NpmAccessServiceTest`, `NpmRegistryTest` |
| FMT-NPM-10 | Packuments must be served full, or abbreviated for `Accept: application/vnd.npm.install-v1+json` with only the fields installs use. Tarball links are absolute, from the external URL, and versions a block refuses are left out, along with tags pointing at them | `NpmRegistryTest`, `NpmAccessServiceTest` |
| FMT-NPM-11 | A version's manifest and its tarball must be served exactly as published; a blocked version is `403` | `NpmRegistryTest` |
| FMT-NPM-12 | A publish must carry exactly one version and its tarball, named `<name>-<version>.tgz`, under the URL's package. The tarball is decoded from base64 straight into the blob store while it's hashed, never held whole, and must match `dist.integrity` and `dist.shasum`; past `max-tarball-size` is `413` | `NpmRegistryTest` |
| FMT-NPM-13 | A published version must never change: publishing it again, or a tombstoned one, is `409`. `npm deprecate` changes only each version's `deprecated` message | `NpmRegistryTest` |
| FMT-NPM-14 | Dist-tags must point at versions that exist; publishing moves the tags it names, the first version becomes `latest`, and `latest` can be moved but not removed | `NpmRegistryTest` |
| FMT-NPM-15 | The `/npm/` routes must exist only in a web application while `kiln.format.npm.enabled` isn't `false`. Publishes and tarball downloads reach the event listeners | `NpmWebAutoConfigurationTest`, `NpmRegistryTest` |
| FMT-NPM-16 | `-/ping` and `-/whoami` must answer, `whoami` asking anonymous callers to sign in. npm commands Kiln doesn't serve (`search`, `login`, `audit`, `unpublish`, `owner`, `access`, …) are a `404` with npm's `error` and an `npm-notice` naming the alternative, never a `5xx` | `NpmAccessServiceTest` |

## How it plugs in *(implementation only)*

Registers `NpmFormatModule` (format `npm`, route `/npm/`), switched by `kiln.format.npm.enabled`.
It passes `FormatModuleContract` in `NpmFormatModuleTest`.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.format.npm.enabled` | `true` | Switches the format off: its `/npm/` routes don't exist |
| `kiln.format.npm.max-tarball-size` | 256 MiB | The largest tarball in bytes; larger is `413` |

## Usage

```java
NpmPackageName name = NpmPackageName.fromPath(rawPathSegment);

NpmDistVerifier verifier = new NpmDistVerifier();
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
- **Fuzz targets**: package names (`NpmPackageNameTest`)
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

Implemented: FMT-NPM-1 to FMT-NPM-16.

Planned (M2): web login, unpublish as a Kiln delete, audit from a local OSV mirror, search,
registry signatures and provenance. The real npm, pnpm and yarn are the acceptance test.
