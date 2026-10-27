# kiln-components

The shared Java components of [Bulmer Kiln](https://bulmerkiln.com), the open-source,
security-first artifact registry. Every module is published as `com.bulmerkiln:<module>` and
released together, at one version, through `kiln-dependencies`.

| Module | Contains |
|---|---|
| `kiln-build` | Gradle convention plugins (`kiln.java-library`, `kiln.publishing`): Java 25, Error Prone with NullAway, Palantir formatting with SPDX headers, JUnit, jqwik and Jazzer, reproducible archives, signed publishing |
| `kiln-dependencies` | The release-train BOM: every `kiln-*` module and third-party version, from `gradle/libs.versions.toml` |
| `kiln-architecture` | The architecture rules, checked across every module with ArchUnit (test only) |
| `kiln-core` | The shared kernel: `OrganizationContext` and `Organization`, content digests, and the open `ArtifactFormat` identifier |
| `kiln-auth` | The token format, and the sign-in subsystem: the `IdentityConnector` contract, shared identity rules, the `ConnectorRegistry`, and the contract test suite (test fixtures) every connector runs |
| `kiln-auth-oidc` | The generic OIDC connector with presets (Dex, Keycloak, Authentik, Ory, Okta, GitLab, Google Workspace) and claim mapping |
| `kiln-auth-oauth2` | The generic OAuth2 connector, with the GitHub preset |
| `kiln-permissions` | The permissions subsystem: roles, role bindings, group-to-team mapping, the four-way access decision (allow, sign in, 403, 404), and the authorization-matrix suite (test fixtures) every policy runs |
| `kiln-format` | The format subsystem: the `FormatModule` contract, the format registry, refusals whose reason fixes the HTTP status, the policy, access and events ports (events fan out to every `ArtifactEventListener`), and the contract test suite (test fixtures) every format runs |
| `kiln-format-oci` | OCI names, tags and references, the spec's error envelope, and manifest and index validation |
| `kiln-format-npm` | Package names, and `dist.integrity` and `dist.shasum` verification |
| `kiln-format-maven` | Strict repository path parsing, including SNAPSHOT builds, and XXE-safe XML parsing |
| `kiln-format-cargo` | Crate names, sparse index paths, and the publish body |
| `kiln-storage` | The `BlobStore` contract, `StorageRouter`, backend selection with `kiln.storage.type`, and the contract test suite (test fixtures) every backend runs |
| `kiln-storage-fs` | The `fs` backend: the filesystem `BlobStore`, configured with `kiln.storage.fs.root` |
| `kiln-storage-s3` | The `s3` backend: key layout, settings and the presigned-redirect policy; the `BlobStore` follows |
| `kiln-scan` | The scan subsystem's contract, shared by the server and `kiln-scanner-service`: the versioned job and result schema, open scanner IDs, the `ScannerAdapter` contract with its rules (`ScannerAdapters.run`), and the contract suite (test fixtures) every adapter runs |
| `kiln-scan-trivy`, `kiln-scan-grype`, `kiln-scan-osv-scanner`, `kiln-scan-gitleaks` | One scanner adapter per tool, tested against real recorded output; used by `kiln-scanner-service` |
| `kiln-scan-orchestration` | Server-side scanning: deduplicating findings across scanners, and severity counts |
| `kiln-observability` | Kiln's metrics, with bounded labels; counts every format's pushes and pulls as an `ArtifactEventListener` |

Each module has its own `README.md`, following [the module README template](docs/module-readme-template.md).

## Build

```bash
./gradlew build
```

This needs JDK 25. `build` runs the formatting check (fix it with `./gradlew spotlessApply`),
Error Prone and NullAway, and every test. Fuzz tests run their regression inputs as ordinary
tests. To fuzz one for real:

```bash
JAZZER_FUZZ=1 ./gradlew :kiln-format-oci:test --tests '*ManifestParserTest.parseOnlyFailsWithRegistryErrors'
```

## Using a component

```kotlin
dependencies {
    implementation(platform("com.bulmerkiln:kiln-dependencies:<version>"))
    implementation("com.bulmerkiln:kiln-storage-fs")
}
```

Components are Spring Boot auto-configurations: adding the jar turns the feature on, and `kiln.*`
properties configure it.

## License

Apache-2.0. Contributions are accepted under the [DCO](https://developercertificate.org/): sign
off every commit with `git commit -s`.
