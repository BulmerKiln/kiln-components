# kiln-format-cargo

The Cargo format: Rust crates through the sparse index, on `/cargo/`.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-format`](../kiln-format) |
| Artifact | `com.bulmerkiln:kiln-format-cargo` |
| Runs in | Kiln's server |
| Depends on | [`kiln-format`](../kiln-format), [`kiln-web`](../kiln-web), [`kiln-registry`](../kiln-registry), [`kiln-database`](../kiln-database), [`kiln-storage`](../kiln-storage), Jackson |
| Used by | `kiln-oss-service` |
| Status | 1.x, Experimental: names, index paths, the publish body and errors; no endpoints yet |

## Purpose

Cargo finds crates through a sparse index of files named after each crate, and publishes with one
binary body. This module validates crate names so none can impersonate another, maps names to
index files, and reads the publish body without trusting its length fields.

## Responsibilities

**Does**

- Validate crate names and their uniqueness form (`CargoCrateName`)
- Compute sparse index paths
- Parse the publish body (`CargoPublishRequest`)
- Render refusals in Cargo's error body
- Serve the `/cargo/` sparse registry while the format is enabled: `config.json`, index files,
  publish, download, yank and unyank

**Does not**

- Decide access or policy; it asks the `ArtifactAccess` and `ArtifactPolicy` ports first
- Keep its own tables; crates, versions and files live in `kiln-registry`

## Requirements

Inherits every requirement of [`kiln-format`](../kiln-format#requirements), and adds:

| ID | Requirement | Verified by |
|---|---|---|
| FMT-CARGO-1 | Crate names must follow crates.io's rules: ASCII letters, digits, `-` and `_`, starting with a letter, at most 64 characters | `CargoTest` (fuzz target) |
| FMT-CARGO-2 | Names that Cargo would write as Windows device files (`con`, `prn`, `aux`, `nul`, `com1`-`com9`, `lpt1`-`lpt9`) and the standard library crates (`std`, `core`, `alloc`, `proc-macro`, `test`) must be refused, as crates.io does | `CargoTest` |
| FMT-CARGO-3 | Names must be unique ignoring case and the difference between `-` and `_`, so `Serde_JSON` can't impersonate `serde-json` | `CargoTest` |
| FMT-CARGO-4 | Index paths must follow the sparse index: `1/a`, `2/ab`, `3/a/abc`, `ab/cd/abcd` for longer names, lowercase | `CargoTest` |
| FMT-CARGO-5 | The publish body's two 32-bit little-endian lengths must be read as unsigned, checked against the limits (`413`) before allocating anything, and against the bytes actually present (`400`); nothing may follow the crate | `CargoTest` (fuzz target) |
| FMT-CARGO-6 | Errors must be Cargo's `{"errors":[{"detail":"<message>"}]}` | `CargoFormatModuleTest` |
| FMT-CARGO-7 | Routes below `/cargo/` must be the sparse index (`index/config.json`, `index/<path>` at exactly the path the name defines) and the API (`api/v1/crates/new`, `…/<name>/<version>/download`, `yank`, `unyank`); route parsing never throws anything but a refusal | `CargoRouteTest` (fuzz target) |
| FMT-CARGO-8 | Access must be decided before anything about the crate is looked up. A crate belongs to the namespace owning the longest prefix of its canonical name, and publishing needs one | `CargoAccessServiceTest` |
| FMT-CARGO-9 | A version's index line must be built from the publish: `req` from `version_req`, a renamed dependency with its real name in `package`, `cksum` the SHA-256 of the `.crate`, `v` 2 | `CargoRegistryTest` |
| FMT-CARGO-10 | A version must never change, and a name can't be published under another spelling of a crate that exists; the metadata must name a valid crate and SemVer version; past `max-crate-size` is `413` | `CargoRegistryTest` |
| FMT-CARGO-11 | `config.json` must name `dl` and `api` from the external URL and require auth, and be readable by anyone since Cargo asks before it sends credentials. Index files carry `ETag` and `Last-Modified`, and `If-None-Match` is `304`. Unsupported commands (`owner`, `search`) get guidance in Cargo's error format | `CargoRegistryTest`, `CargoAccessServiceTest` |
| FMT-CARGO-12 | A yanked version must stay in the index, marked, and downloadable, so lockfiles keep working; a blocked one leaves the index and is `403`; a tombstoned one can't be published | `CargoRegistryTest` |
| FMT-CARGO-13 | The `/cargo/` routes must exist only in a web application while `kiln.format.cargo.enabled` isn't `false`. Publishes and downloads reach the event listeners | `CargoWebAutoConfigurationTest`, `CargoRegistryTest` |

## How it plugs in *(implementation only)*

Registers `CargoFormatModule` (format `cargo`, route `/cargo/`), switched by
`kiln.format.cargo.enabled`. It passes `FormatModuleContract` in `CargoFormatModuleTest`.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.format.cargo.enabled` | `true` | Switches the format off: its `/cargo/` routes don't exist |
| `kiln.format.cargo.max-crate-size` | 10 MiB | The largest `.crate` in bytes; larger is `413` |

## Usage

```java
CargoCrateName name = new CargoCrateName("serde_json");
String indexFile = name.indexPath();          // se/rd/serde_json
CargoPublishRequest body = CargoPublishRequest.parse(request, CargoPublishRequest.DEFAULT_MAX_METADATA, maxCrateSize);
```

## Security

- **Threats**: typosquatting through case and separator variants; names that break Windows
  clients; publish bodies whose lengths cause huge allocations or read past the data
- **Untrusted input**: crate names, publish bodies
- **Defaults**: anything not explicitly valid is refused
- **Protections**: FMT-CARGO-2, FMT-CARGO-3, FMT-CARGO-5

## Testing

- **Contract suite**: passes `FormatModuleContract`
- **Fuzz targets**: names and publish bodies (`CargoTest`)
- **Other**: none

```bash
./gradlew :kiln-format-cargo:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: Cargo's [registry index](https://doc.rust-lang.org/cargo/reference/registry-index.html) (sparse protocol, RFC 2789) and [registry web API](https://doc.rust-lang.org/cargo/reference/registry-web-api.html); [credential providers](https://doc.rust-lang.org/cargo/reference/credential-provider-protocol.html) (RFC 3139)
- **Dependencies**: the JDK only
- **Reference projects**: crates.io, Kellnr, Gitea/Forgejo's Cargo registry

## Status

Implemented: FMT-CARGO-1 to FMT-CARGO-13.

Planned (1.x): authentication through `kiln-cli` as a Cargo credential provider, and Cargo
itself as the acceptance test.
