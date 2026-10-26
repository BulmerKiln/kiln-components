# kiln-format-maven

The Maven format: Maven and Gradle repositories, on `/maven/`.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-format`](../kiln-format) |
| Artifact | `com.bulmerkiln:kiln-format-maven` |
| Runs in | Kiln's server |
| Depends on | [`kiln-format`](../kiln-format) |
| Used by | `kiln-oss-service`, `kiln-cloud-service` |
| Status | M3, Experimental: path parsing, safe XML and errors; no endpoints yet |

## Purpose

A Maven repository is a file tree, so every request is a path, and every POM and
`maven-metadata.xml` is XML. This module parses paths strictly enough that none can traverse or
name one version's file as another's, and parses XML with every external-entity feature off.

## Responsibilities

**Does**

- Parse repository paths into artifact files, metadata files and their checksum sidecars (`MavenPath`)
- Parse untrusted XML safely (`SafeXml`)
- Render refusals as plain text

**Does not**

- Serve HTTP yet: deploys, metadata regeneration, checksums and snapshots come next
- Store files; it hands validated paths to the layers that do

## Requirements

Inherits every requirement of [`kiln-format`](../kiln-format#requirements), and adds:

| ID | Requirement | Verified by |
|---|---|---|
| FMT-MVN-1 | Paths must be at most 1,024 characters of segments matching `[A-Za-z0-9_-][A-Za-z0-9._-]*`: no empty, dot-leading or `..` segment, no leading or trailing slash, no `%`, `\`, `?` or control characters | `MavenPathTest` (fuzz target) |
| FMT-MVN-2 | A file's name must start with `<artifactId>-<version>`. For a release the version matches exactly; for `X-SNAPSHOT` it's either `X-SNAPSHOT` or a timestamped build `X-YYYYMMDD.HHMMSS-N`. A file of one version can never be read as another's | `MavenPathTest` |
| FMT-MVN-3 | A classifier must be `[A-Za-z0-9_-]+`; an extension one or more dot-separated alphanumeric parts (`jar`, `tar.gz`, `jar.asc`, `module`); `.md5`, `.sha1`, `.sha256` and `.sha512` mark checksum sidecars | `MavenPathTest` |
| FMT-MVN-4 | `maven-metadata.xml` must be recognised per artifact, and per version only for a `-SNAPSHOT` version | `MavenPathTest` |
| FMT-MVN-5 | Errors must never echo the path | `MavenPathTest` |
| FMT-MVN-6 | XML must be parsed with any `DOCTYPE` refused, external entities, external DTDs and XInclude off, and secure processing on, which also stops entity-expansion bombs. Parse errors are thrown, never printed | `SafeXmlTest` |
| FMT-MVN-7 | Errors must be the message as `text/plain; charset=utf-8`, since Maven over HTTP/2 has no reason phrase and Maven and Gradle show the body | `MavenFormatModuleTest` |

## How it plugs in *(implementation only)*

Registers `MavenFormatModule` (format `maven`, route `/maven/`), switched by
`kiln.format.maven.enabled`. It passes `FormatModuleContract` in `MavenFormatModuleTest`.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.format.maven.enabled` | `true` | Switches the format off |

## Usage

```java
switch (MavenPath.parse(path)) {
    case MavenPath.ArtifactFile file -> ...;
    case MavenPath.Metadata metadata -> ...;
}

Document pom = SafeXml.parse(bytes);
```

## Security

- **Threats**: path traversal; one version's file shadowing another's; XXE reading server files
  or reaching internal hosts; entity-expansion bombs
- **Untrusted input**: request paths, POMs, `maven-metadata.xml`
- **Defaults**: anything not explicitly valid is refused
- **Protections**: FMT-MVN-1, FMT-MVN-2, FMT-MVN-5, FMT-MVN-6

## Testing

- **Contract suite**: passes `FormatModuleContract`
- **Fuzz targets**: paths (`MavenPathTest`)
- **Other**: XXE payloads use a temporary file as the secret to read

```bash
./gradlew :kiln-format-maven:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: the [Maven repository layout](https://maven.apache.org/repositories/layout.html); [Gradle Module Metadata](https://github.com/gradle/gradle/blob/master/platforms/documentation/docs/src/docs/design/gradle-module-metadata-latest-specification.md); the [OWASP XML External Entity Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html)
- **Dependencies**: the JDK's XML parser
- **Reference projects**: Reposilite, Gitea/Forgejo's Maven registry, Sonatype Nexus OSS

## Status

Implemented: FMT-MVN-1 to FMT-MVN-7.

Planned (M3): `PUT`, `GET` and `HEAD` for every path, versions visible once their POM lands,
`maven-metadata.xml` regenerated from the database, server-computed checksums (uploaded ones
verified), snapshot resolution, immutable releases (`409` on redeploy), `.module` files, and
`401` with `WWW-Authenticate: Basic` on protected reads.
