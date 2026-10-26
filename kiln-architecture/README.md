# kiln-architecture

The architecture rules, checked across every module with ArchUnit. Test only, never published.

## At a glance

| | |
|---|---|
| Kind | Foundation |
| Artifact | None: a test-only module |
| Runs in | Build time only |
| Depends on | Every published module, in its tests; ArchUnit |
| Used by | `./gradlew build` |
| Status | M1, Experimental |

## Purpose

The modularity rules (a kernel that holds no subsystem, implementations that never reach their
siblings, a scan worker that carries no server code) are what let a module be replaced or added
on its own. Rules a reviewer must remember get missed, so this module checks them on every build,
against the compiled classes of every module at once.

## Responsibilities

**Does**

- Define which module may depend on which, in one table (`ArchitectureTest.ALLOWED`)
- Check that table, the kernel's isolation, the worker's isolation and where Spring may appear
- Check the naming rules ArchUnit can see, and its general coding rules

**Does not**

- Hold any production code
- Check style, size or complexity; Checkstyle and PMD do, through [`kiln-build`](../kiln-build)

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| ARCH-1 | `kiln-core` must depend on nothing but the JDK and JSpecify | `ArchitectureTest` |
| ARCH-2 | Every production class must belong to a module listed in `ArchitectureTest.ALLOWED`, and depend only on its own module and the modules listed for it. That table is the module dependency graph: a subsystem never depends on its implementations, an implementation never on a sibling, and a new module or dependency is added to it in the same pull request. Today: `storage.fs`, `storage.s3` on `storage`; each `format.*` on `format`; `auth.oidc`, `auth.oauth2` on `auth`; `scan.orchestration` and each adapter on `scan`; `permissions` and `observability` on `format`; everything may use `core` except `auth`, which needs nothing | `ArchitectureTest` |
| ARCH-3 | There must be no dependency cycle between subsystems | `ArchitectureTest` |
| ARCH-4 | The modules the scan worker loads (`kiln-scan` and the `kiln-scan-*` adapters) must not depend on Spring or Micrometer | `ArchitectureTest` |
| ARCH-5 | Spring may appear only in classes named `*AutoConfiguration` or `*Properties`, so wiring lives in auto-configurations and nowhere else | `ArchitectureTest` |
| ARCH-6 | A class annotated `@AutoConfiguration` must be named `*AutoConfiguration`, and the other way round; a class annotated `@ConfigurationProperties` must be named `*Properties` | `ArchitectureTest` |
| ARCH-7 | Every `Throwable` must be named `*Exception` | `ArchitectureTest` |
| ARCH-8 | No production class may write to standard output or error, throw `Throwable`, `Exception` or `RuntimeException` directly, use `java.util.logging` or Joda-Time, or use field injection | `ArchitectureTest` |
| ARCH-10 | Implementations must be named for their contract: `*FormatModule`, `*Access`, `*Listener`, `*BlobStore`, `*Router`, `*Connector`, `*Connectors` (a connector source), `*Registry`, `*Policy`, `*Adapter` | `ArchitectureTest` |
| ARCH-11 | A module must reach an optional dependency (`permissions` and `observability` on `format`) only through an auto-configuration annotated with `@ConditionalOnClass`: a class that uses the optional module is used only by such auto-configurations, or by other classes that use it | `ArchitectureTest` |
| ARCH-9 | The rules must check every module's production classes and no test classes, so none passes by checking nothing | `ArchitectureTest` |

## Configuration

None.

## Usage

```bash
./gradlew :kiln-architecture:test
```

A new module adds its package to `ALLOWED` with the modules it may use; the build fails until it
does (ARCH-2).

## Security

- **Threats**: a change that quietly couples modules, so a security rule enforced in one subsystem
  can be bypassed through another; server code, with its credentials and database access, ending
  up inside the sandboxed scan worker
- **Untrusted input**: none
- **Defaults**: an unknown module or dependency fails the build
- **Protections**: ARCH-2, ARCH-4, ARCH-5

## Testing

- **Contract suite**: none
- **Fuzz targets**: none
- **Other**: ARCH-9 guards the other rules against passing on an empty import

```bash
./gradlew :kiln-architecture:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: none
- **Dependencies**: [ArchUnit](https://www.archunit.org), used directly from JUnit tests rather than through its JUnit engine
- **Reference projects**: ArchUnit's own examples of layered and onion architectures

## Status

Implemented: ARCH-1 to ARCH-11.
