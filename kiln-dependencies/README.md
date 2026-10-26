# kiln-dependencies

The release-train BOM: one version for every `kiln-*` module and every third-party library Kiln
uses.

## At a glance

| | |
|---|---|
| Kind | Foundation |
| Artifact | `com.bulmerkiln:kiln-dependencies` (a Gradle platform, published as a Maven BOM) |
| Runs in | Build time only |
| Depends on | The Spring Framework, Micrometer, Jackson and JUnit BOMs |
| Used by | Every module, through [`kiln-build`](../kiln-build), and every deployable |
| Status | M1, Experimental |

## Purpose

Kiln is many modules and several deployables. If each declared its own versions they would drift,
and a deployable could mix module versions that were never tested together. This BOM is the
single place versions are decided, and each `kiln-components` release is one tested set.

## Responsibilities

**Does**

- Pin every `kiln-*` module at the train's version
- Pin every third-party library Kiln uses, from [`gradle/libs.versions.toml`](../gradle/libs.versions.toml)
- Import the Spring Framework, Micrometer, Jackson and JUnit BOMs at the versions Spring Boot declares

**Does not**

- Import `spring-boot-dependencies` as a whole: only the Spring Boot artifacts Kiln uses are managed
- Hold Gradle plugin versions; those live in [`kiln-build`](../kiln-build)

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| DEP-1 | Every dependency version used by any Kiln repository must come from this BOM, so third-party upgrades happen once and reach every repository on the next train | The build: every module resolves through it (BUILD-3) |
| DEP-2 | Every third-party version must be declared in `gradle/libs.versions.toml`, and nothing but this BOM may read that catalog, with one exception: `kiln-build` reads FindSecBugs's version from it, because SpotBugs plugins resolve without the BOM's constraints | Review |
| DEP-3 | The versions marked `# Boot` in the catalog must equal those `spring-boot-dependencies` declares at the catalog's Spring Boot version, so they move together on every Boot upgrade | Review on every Spring Boot upgrade |
| DEP-4 | Adopting a new Spring Boot minor or major version must be the train's first change, made on its own, so breakages are clearly attributable. Patch releases may land at any time | Review |
| DEP-5 | Every published `kiln-*` module in `settings.gradle.kts` must be listed here; the test-only `kiln-architecture` is not | Review: adding a module adds its constraint |


## Configuration

None.

## Usage

```kotlin
dependencies {
    implementation(platform("com.bulmerkiln:kiln-dependencies:<version>"))
    implementation("com.bulmerkiln:kiln-storage-fs")
}
```

Modules in this repository don't need that line: `kiln.java-library` adds the platform for them.

## Security

- **Threats**: a freshly compromised release pulled in by an upgrade
- **Untrusted input**: none at runtime
- **Defaults**: versions change only through this BOM; Renovate opens upgrades with a 3-day
  minimum release age
- **Protections**: one reviewed place for every version (DEP-1)

## Testing

- **Contract suite**: none
- **Fuzz targets**: none
- **Other**: the whole build resolves through this BOM

```bash
./gradlew build
```

No tests need Docker or external services.

## Standards and references

- **Standards**: Maven BOMs; Gradle [`java-platform`](https://docs.gradle.org/current/userguide/java_platform_plugin.html)
- **Dependencies**: the Spring Framework, Micrometer, Jackson and JUnit BOMs
- **Reference projects**: `spring-boot-dependencies`, Spring Cloud release trains

## Status

Implemented: DEP-1 to DEP-5 (DEP-2 to DEP-5 by review).

Planned: a build check that fails when a module is missing from the BOM (DEP-5) or when a `# Boot`
version disagrees with Spring Boot's (DEP-3).
