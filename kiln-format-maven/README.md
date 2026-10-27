# kiln-format-maven

The Maven format: Maven and Gradle repositories, on `/maven/`.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-format`](../kiln-format) |
| Artifact | `com.bulmerkiln:kiln-format-maven` |
| Runs in | Kiln's server |
| Depends on | [`kiln-format`](../kiln-format), [`kiln-web`](../kiln-web), [`kiln-registry`](../kiln-registry), [`kiln-database`](../kiln-database), [`kiln-storage`](../kiln-storage), [`kiln-permissions`](../kiln-permissions) |
| Used by | `kiln-oss-service` |
| Status | M3, Experimental: path parsing, safe XML and errors; no endpoints yet |

## Purpose

A Maven repository is a file tree, so every request is a path, and every POM and
`maven-metadata.xml` is XML. This module parses paths strictly enough that none can traverse or
name one version's file as another's, and parses XML with every external-entity feature off.

## Responsibilities

**Does**

- Parse repository paths into artifact files, metadata files and their checksum sidecars (`MavenPath`)
- Parse untrusted XML safely (`MavenSafeXmlUtils`)
- Render refusals as plain text
- Serve the `/maven/` repository while the format is enabled: deploys, downloads, server-computed
  checksums, generated `maven-metadata.xml`, and snapshot resolution

**Does not**

- Decide access or policy; it asks the `ArtifactAccess` and `ArtifactPolicy` ports first
- Keep its own tables; artifacts, versions and files live in `kiln-registry`

## Requirements

Inherits every requirement of [`kiln-format`](../kiln-format#requirements), and adds:

| ID | Requirement | Verified by |
|---|---|---|
| FMT-MVN-1 | Paths must be at most 1,024 characters of segments matching `[A-Za-z0-9_-][A-Za-z0-9._-]*`: no empty, dot-leading or `..` segment, no leading or trailing slash, no `%`, `\`, `?` or control characters | `MavenPathTest` (fuzz target) |
| FMT-MVN-2 | A file's name must start with `<artifactId>-<version>`. For a release the version matches exactly; for `X-SNAPSHOT` it's either `X-SNAPSHOT` or a timestamped build `X-YYYYMMDD.HHMMSS-N`. A file of one version can never be read as another's | `MavenPathTest` |
| FMT-MVN-3 | A classifier must be `[A-Za-z0-9_-]+`; an extension one or more dot-separated alphanumeric parts (`jar`, `tar.gz`, `jar.asc`, `module`); `.md5`, `.sha1`, `.sha256` and `.sha512` mark checksum sidecars | `MavenPathTest` |
| FMT-MVN-4 | `maven-metadata.xml` must be recognised per artifact, and per version only for a `-SNAPSHOT` version | `MavenPathTest` |
| FMT-MVN-5 | Errors must never echo the path | `MavenPathTest` |
| FMT-MVN-6 | XML must be parsed with any `DOCTYPE` refused, external entities, external DTDs and XInclude off, and secure processing on, which also stops entity-expansion bombs. Parse errors are thrown, never printed | `MavenSafeXmlUtilsTest` |
| FMT-MVN-7 | Errors must be the message as `text/plain; charset=utf-8`, since Maven over HTTP/2 has no reason phrase and Maven and Gradle show the body | `MavenFormatModuleTest` |
| FMT-MVN-8 | Every path below `/maven/` must take `GET`, `HEAD` and `PUT`; other methods are `405` in Maven's text, since deletes go through Kiln | `MavenAccessServiceTest` |
| FMT-MVN-9 | Access must be decided before anything about the artifact is looked up. An artifact belongs to the namespace owning the longest prefix of its groupId, and deploying needs one | `MavenAccessServiceTest` |
| FMT-MVN-10 | Files must stream into the blob store, hashed on the way, and come back as deployed; past `max-file-size` is `413` | `MavenRepositoryTest` |
| FMT-MVN-11 | A stored file must never change: the same bytes again are a no-op, since clients retry; other bytes are `409`. A tombstoned version can't be deployed | `MavenRepositoryTest` |
| FMT-MVN-12 | `.md5`, `.sha1`, `.sha256` and `.sha512` must be computed on the server for every file and metadata. An uploaded checksum must match its stored file, or `400` | `MavenRepositoryTest` |
| FMT-MVN-13 | Anonymous callers must get `401` with `WWW-Authenticate: Basic`, since Maven sends credentials only after a challenge | `MavenAccessServiceTest` |
| FMT-MVN-14 | A `-SNAPSHOT` file name must resolve to the latest timestamped build of the same classifier and extension; a timestamped name is served exactly | `MavenRepositoryTest` |
| FMT-MVN-15 | `maven-metadata.xml` must be generated from the database, never taken from uploads: per artifact, the versions with a POM that no block refuses, with `latest` and `release`; per snapshot version, the latest build of each kind. A blocked version is `403`, with the reason in the body | `MavenRepositoryTest` |
| FMT-MVN-16 | The `/maven/` routes must exist only in a web application while `kiln.format.maven.enabled` isn't `false`. POM deploys and downloads reach the event listeners | `MavenWebAutoConfigurationTest`, `MavenRepositoryTest` |

## How it plugs in *(implementation only)*

Registers `MavenFormatModule` (format `maven`, route `/maven/`), switched by
`kiln.format.maven.enabled`. It passes `FormatModuleContract` in `MavenFormatModuleTest`.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.format.maven.enabled` | `true` | Switches the format off: its `/maven/` routes don't exist |
| `kiln.format.maven.max-file-size` | 2 GiB | The largest file in bytes; larger is `413` |

## Usage

```java
switch (MavenPath.parse(path)) {
    case MavenPath.ArtifactFile file -> ...;
    case MavenPath.Metadata metadata -> ...;
}

Document pom = MavenSafeXmlUtils.parse(bytes);
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

Implemented: FMT-MVN-1 to FMT-MVN-16.

Planned (M3): checksums stored at deploy rather than computed per request, the sweep of
half-deployed versions, and Maven and Gradle themselves as the acceptance test.
