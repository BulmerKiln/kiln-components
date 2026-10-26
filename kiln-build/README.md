# kiln-build

The Gradle convention plugins every Kiln Java repository applies.

## At a glance

| | |
|---|---|
| Kind | Foundation |
| Artifact | `com.bulmerkiln:kiln-build` (Gradle plugins `kiln.java-library`, `kiln.publishing`, `kiln.repository` and `kiln.readme-check`) |
| Runs in | Build time only |
| Depends on | Spotless, Error Prone, SpotBugs, the dependency-analysis and license-report Gradle plugins |
| Used by | Every module in `kiln-components`, and the Java deployables |
| Status | M1, Experimental |

## Purpose

Every Kiln repository builds, checks and publishes its Java code the same way. Keeping those
rules in one published set of plugins means a repository applies one plugin instead of copying
build logic, and a change to the rules reaches every repository on the next release train.

## Responsibilities

**Does**

- Set the Java 25 toolchain and compiler flags, with Error Prone and NullAway
- Take every dependency version from `kiln-dependencies`
- Format code and add the license header
- Limit size, nesting and complexity with Checkstyle, and catch code smells with PMD and Kiln's own PMD rules
- Find bugs and security bugs with SpotBugs and FindSecBugs
- Require a fuzz target for every parser
- Check dependencies: unused or wrongly scoped, licensed as allowed, locked, and verified by checksum
- Configure tests (JUnit, jqwik, Jazzer)
- Make archives reproducible, and publish them with Maven Central metadata, signed when a key is provided
- Check that the module READMEs and the code agree (`kiln.readme-check`, applied at the repository root)

**Does not**

- Declare any library versions; those live in [`kiln-dependencies`](../kiln-dependencies)
- Hold CI workflows; those live in the `cicd` repository

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| BUILD-1 | Every module must compile for Java 25 with `-Xlint:all -Werror`, Error Prone and NullAway, so a warning fails the build. Error Prone's `StringCaseLocaleUsage` and `DefaultLocale` checks are errors, so case conversion and formatting never depend on the JVM's locale | The build: `compileJava` in every module |
| BUILD-2 | NullAway must treat every `@NullMarked` package as null-checked, in JSpecify mode. Test code is exempt | The build: `compileJava` in every module |
| BUILD-3 | No module may declare a dependency version. Inside `kiln-components` versions come from the local `kiln-dependencies` project; anywhere else from the `kiln-dependencies` release with the same version as these plugins | The build: dependency resolution in every module |
| BUILD-4 | Every Java source file must carry the SPDX header and be formatted with Palantir Java Format | The build: `spotlessCheck` |
| BUILD-5 | Archives must be reproducible: no file timestamps, a fixed file order | The build: `AbstractArchiveTask` settings |
| BUILD-6 | Publications must carry the POM metadata Maven Central requires (name, description, URL, Apache-2.0 license, developers, SCM) and resolved dependency versions | `publishToMavenLocal` |
| BUILD-7 | Publications must be signed whenever `KILN_SIGNING_KEY` is set, and the signing key must only ever come from the environment | The build: `signing` configuration |
| BUILD-9 | Every module must pass Checkstyle with no warnings. In production code: lines at most 120 characters, methods at most 50 lines and 30 statements, files at most 400 lines, at most 2 nested `if`s, 2 nested loops and 1 nested `try`, cyclomatic complexity at most 10, NPath complexity at most 200, at most 4 operators in a boolean expression, 7 parameters and 10 record components, class fan-out at most 20 and data-abstraction coupling at most 7, plus naming and hygiene rules, and no TODO without an issue number (`TODO(#123)`). Tests may be longer, but not deeper or more complex | The build: `checkstyleMain`, `checkstyleTest`, `checkstyleTestFixtures` |
| BUILD-10 | Production code and test fixtures must pass PMD with no violations: cognitive complexity at most 15 per method, no god classes, no collapsible or redundant conditionals, no unused or reassigned values, no unnecessary imports, no exceptions used for flow control. `PreserveStackTrace` is deliberately off: refusals drop causes so clients never see a stack trace or their own input | The build: `pmdMain`, `pmdTestFixtures` |
| BUILD-11 | Test code must pass PMD's test rules with no violations: no fully qualified name an import would replace, no unnecessary import, no unused local variable or private member, no `Thread.sleep` or `TimeUnit.sleep`, and no network: sockets, URL connections or HTTP clients | The build: `pmdTest` |
| BUILD-12 | Production code must pass SpotBugs at maximum effort and medium confidence, with FindSecBugs, with no findings. Only two patterns are excluded, each for a stated reason: exposing internal representation (records hand out what they hold, copied immutably) and throwing from constructors (how invalid values are made impossible) | The build: `spotbugsMain` |
| BUILD-13 | Production code must pass Kiln's own PMD rules: validation with anchored `matches()`, never `find()`; no `URLDecoder`; no `Runtime.exec` or shell in a `ProcessBuilder`; no JSON built by joining strings; regexes compiled only into static constants; no `List` search, regex or string `+=` inside a loop body | The build: `pmdMain` |
| BUILD-14 | Every static `parse` or `fromPath` method taking a `String` or `byte[]` must be called from a `@FuzzTest` in its module's tests | The build: `fuzzCheck` |
| BUILD-15 | No module may have an unused dependency or one declared at the wrong scope (`api`, `implementation`, `compileOnly`). The conventions' shared annotations and test stack, and `kiln-architecture`, whose dependencies are used by class scanning, are exempt | The build: `buildHealth` |
| BUILD-16 | Every runtime dependency must declare at least one allowed license: Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause, ISC or MPL-2.0. BOMs, which ship no code, and Kiln's own modules are exempt | The build: `checkLicense` |
| BUILD-17 | Every resolved dependency must be locked (strict mode: a configuration without lock state fails), and every artifact verified against its SHA-256 in `gradle/verification-metadata.xml` | The build: dependency locking and verification |
| BUILD-18 | Every module's tests must cover at least 80% of its lines and 80% of its branches, measured by JaCoCo over the module's own main classes. Contract suites in test fixtures count toward the module that runs them. The HTML report is `build/reports/jacoco/test/html` | The build: `jacocoTestCoverageVerification` |
| BUILD-19 | `./gradlew pitest` must run PIT mutation testing over every module's main classes, with bounded memory: at most 3 modules at a time, 4 test JVMs each, 1 GB of heap per JVM. A module whose classes, tests and dependencies haven't changed reuses its cached result. The HTML report is `build/reports/pitest` | `./gradlew pitest` |
| BUILD-20 | `./gradlew stageRelease` must write every published module, the BOM and kiln-build's plugins, with the POM metadata Maven Central requires (name, description, URL, license, developers, SCM), sources and javadoc jars, and checksums, to `build/staging-deploy` in Maven repository layout. With `KILN_SIGNING_KEY` set, every artifact is also signed. The release workflow refuses to publish an unsigned artifact | The release workflow (`BulmerKiln/cicd`, `gradle-release.yml`) |
| BUILD-8 | `build` must fail when the READMEs and the code disagree: a module without a README, a requirement ID defined twice, a README naming a test that doesn't exist, or code citing a requirement ID no README defines | The build: `readmeCheck`, checked against each failure by hand when it was added |


## Configuration

| Setting | Default | Description |
|---|---|---|
| `KILN_SIGNING_KEY` (environment) | unset | ASCII-armoured PGP key; signing is on when it's set |
| `KILN_SIGNING_PASSWORD` (environment) | unset | The key's passphrase |

## Usage

```kotlin
plugins {
    id("kiln.java-library")
}
```

`kiln.java-library` applies `kiln.publishing`; `kiln-dependencies` applies `kiln.publishing` on
its own, since it's a platform, not a library. Inside `kiln-components` the plugins come from the included build (`pluginManagement {
includeBuild("kiln-build") }`). Other repositories add the published plugin and the BOM version
follows it automatically.

## Security

- **Threats**: a compromised or tampered dependency, a build that silently differs between runs
- **Untrusted input**: none at runtime
- **Defaults**: warnings fail the build; signing is never configured from a file in the repository
- **Protections**: one source of versions (BUILD-3), reproducible archives (BUILD-5), signed
  publications (BUILD-7)

## Testing

- **Contract suite**: none
- **Fuzz targets**: none
- **Other**: the plugins are exercised by building every module

```bash
./gradlew build
```

No tests need Docker or external services.

## Standards and references

- **Standards**: [JSpecify](https://jspecify.dev) nullness annotations; [SPDX license identifiers](https://spdx.dev/learn/handling-license-info/)
- **Dependencies**: [Error Prone](https://errorprone.info), [NullAway](https://github.com/uber/NullAway), [Spotless](https://github.com/diffplug/spotless) with [Palantir Java Format](https://github.com/palantir/palantir-java-format), [Checkstyle](https://checkstyle.org), [PMD](https://pmd.github.io)
- **Reference projects**: Spring Boot's own build conventions

## Status

Implemented: BUILD-1 to BUILD-20. PIT runs on demand and doesn't enforce a mutation score yet.

Planned (M1): PGP signature verification alongside the checksums, CycloneDX SBOMs for every
publication, and a fuzzing time budget for CI. CVE scanning, CodeQL and Semgrep run in CI.

To change a version: edit `gradle/libs.versions.toml`, then regenerate the locks and checksums
with an empty Gradle home, so Gradle downloads, and records, every file a fresh CI runner
fetches (with a warm cache it skips the parent and BOM POMs, and CI then fails verification):

```bash
export GRADLE_USER_HOME=$(mktemp -d)
./gradlew resolveAndLockAll --write-locks --write-verification-metadata sha256 --no-configuration-cache
./gradlew build pitest stageRelease --write-verification-metadata sha256
```
