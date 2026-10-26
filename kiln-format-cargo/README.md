# kiln-format-cargo

The Cargo format: Rust crates through the sparse index, on `/cargo/`.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-format`](../kiln-format) |
| Artifact | `com.bulmerkiln:kiln-format-cargo` |
| Runs in | Kiln's server |
| Depends on | [`kiln-format`](../kiln-format) |
| Used by | `kiln-oss-service`, `kiln-cloud-service` |
| Status | 1.x, Experimental: names, index paths, the publish body and errors; no endpoints yet |

## Purpose

Cargo finds crates through a sparse index of files named after each crate, and publishes with one
binary body. This module validates crate names so none can impersonate another, maps names to
index files, and reads the publish body without trusting its length fields.

## Responsibilities

**Does**

- Validate crate names and their uniqueness form (`CrateName`)
- Compute sparse index paths
- Parse the publish body (`PublishBody`)
- Render refusals in Cargo's error body

**Does not**

- Serve HTTP yet: the index, `config.json`, publish, download and yank come next
- Store crates

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

## How it plugs in *(implementation only)*

Registers `CargoFormatModule` (format `cargo`, route `/cargo/`), switched by
`kiln.format.cargo.enabled`. It passes `FormatModuleContract` in `CargoFormatModuleTest`.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.format.cargo.enabled` | `true` | Switches the format off |

## Usage

```java
CrateName name = new CrateName("serde_json");
String indexFile = name.indexPath();          // se/rd/serde_json
PublishBody body = PublishBody.parse(request, PublishBody.DEFAULT_MAX_METADATA, maxCrateSize);
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

Implemented: FMT-CARGO-1 to FMT-CARGO-6.

Planned (1.x): `config.json` with `auth-required`, index files with `ETag` and `Last-Modified`,
publish with checksum verification, downloads, yank as a soft block that keeps lockfiles working,
and authentication through `kiln-cli` as a Cargo credential provider.
