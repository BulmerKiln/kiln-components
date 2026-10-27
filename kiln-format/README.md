# kiln-format

The format subsystem: the contract every registry format (OCI, npm, Maven, Cargo) implements, the
rules every format must follow, and the ports formats use to reach other subsystems.

## At a glance

| | |
|---|---|
| Kind | Subsystem |
| Artifact | `com.bulmerkiln:kiln-format` (plus a `test-fixtures` variant) |
| Runs in | Kiln's server |
| Depends on | [`kiln-core`](../kiln-core) |
| Used by | The `kiln-format-*` modules; [`kiln-permissions`](../kiln-permissions) and [`kiln-observability`](../kiln-observability) implement its ports |
| Status | M1, Experimental |

## Purpose

Each format speaks a different client protocol, but some rules must hold in all of them: a
blocked artifact is never answered with `404`, a client error is never a `5xx`, an error body
never lets a message break out of its JSON. This module enforces those rules once, by type where
possible, so a new format gets them for free and can't get them wrong.

## Responsibilities

**Does**

- Define `FormatModule`, and collect the enabled formats into a `FormatRegistry`
- Define `Refusal`, whose `Reason` fixes the HTTP status, and build every refusal response
- Define the ports formats use: `ArtifactAccess`, `ArtifactPolicy` and `ArtifactEvents`
- Provide `FormatModuleContract`, the suite every format passes

**Does not**

- Implement any format; the `kiln-format-*` modules do
- Decide access, policy or what happens to events; the subsystems behind the ports do

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| FMT-1 | A refusal's HTTP status must come only from its `Reason`, never from a format: `INVALID` 400, `UNAUTHORIZED` 401, `DENIED` 403, `BLOCKED` 403, `OVER_QUOTA` 403, `NOT_FOUND` 404, `UNSUPPORTED` 405, `CONFLICT` 409, `TOO_LARGE` 413, `RATE_LIMITED` 429. A block is never `404`, or containerd mirrors and some npm and Maven setups fall through to a registry that may serve it. Nothing is a `5xx`, which docker and npm retry endlessly; over quota is `403`, not `507`, for the same reason | `FormatRulesTest`, `FormatModuleContract` |
| FMT-2 | Every refusal must carry `X-Content-Type-Options: nosniff`, and `WWW-Authenticate` exactly when it's a `401`. A format's own headers can't replace or misspell either | `FormatRulesTest`, `FormatModuleContract` |
| FMT-3 | No header value may contain CR, LF or other control characters, from a format or a challenge | `FormatRulesTest` |
| FMT-4 | Every format's error body must carry the refusal's message exactly, escaped so quotes, markup, line separators and control characters can't break out of it | `FormatModuleContract` |
| FMT-5 | A refusal message must be 1 to 1,024 characters; a code hint must match `[A-Z][A-Z0-9_]{0,63}`; a challenge is required for, and only for, `UNAUTHORIZED` | `FormatRulesTest` |
| FMT-6 | A format's route segment must be a reserved organization slug (CORE-4), since routes share the path space with slugs. No two formats may claim one ID or one route | `FormatRulesTest`, `FormatModuleContract` |
| FMT-7 | `kiln.format.<id>.enabled=false` must switch a format off; every format is on by default | `FormatRulesTest` |
| FMT-8 | Access outcomes must map to fixed refusals: `ALLOW` none, `AUTHENTICATE` 401 with the format's challenge, `DENY` 403, `HIDE` 404. The challenge is built only for `AUTHENTICATE` | `FormatRulesTest` |
| FMT-9 | Push and pull events must reach every listener, in order. A listener that throws fails the request, so a listener that must not lose events, such as the audit log, can enforce it. Negative byte counts are refused | `FormatRulesTest` |
| FMT-10 | Listeners must be collected however their modules' auto-configurations are ordered, and with no listener at all the first format to need events fails the boot rather than dropping them | `FormatRulesTest`, `KilnMetricsTest` in kiln-observability |
| FMT-11 | The ports must have no allow-everything default: an assembly without an `ArtifactAccess` or `ArtifactPolicy` implementation fails to start rather than serving unchecked | By design: no default implementation exists |
| FMT-12 | Every format must pass `FormatModuleContract` | `OciFormatModuleTest`, `NpmFormatModuleTest`, `MavenFormatModuleTest`, `CargoFormatModuleTest` |

## Contract *(subsystem only)*

| Type | Role |
|---|---|
| `FormatModule` | A format: its `ArtifactFormat`, its route segment, and how it renders a refusal |
| `Refusal`, `Reason`, `RefusedException` | A format-neutral refusal and the exception that carries it |
| `RefusalBody`, `FormatResponse` | A format's rendering, and the complete response built from it |
| `FormatRegistry` | The enabled formats, by ID and by route |
| `ArtifactAccess` | Port: may the current caller do this? Implemented by `kiln-permissions` |
| `ArtifactPolicy` | Port: blocks, tombstones, immutability and quotas. Implemented by the policy and quota subsystems |
| `ArtifactEventListener`, `ArtifactEvents` | Port: pushes and pulls, delivered to every listener |
| `FormatJson` | Writes error bodies as escaped JSON |
| `FormatModuleContract` *(test fixtures)* | The suite every format extends |

**Rules enforced here**: statuses fixed by reason (FMT-1); security headers formats can't touch
(FMT-2, FMT-3); routes that can't collide with organizations (FMT-6); no unchecked or silent
defaults on the ports (FMT-10, FMT-11).

## Adding an implementation *(subsystem only)*

1. Create `kiln-format-<name>` and depend on `kiln-format`.
2. Implement `FormatModule`: define `public static final ArtifactFormat FORMAT` and render
   refusals in the format's own error body, using `FormatJson` for JSON.
3. Make the route segment a reserved slug; reserve it in `Organization.RESERVED_SLUGS` first if it's new.
4. Register the module as a `FormatModule` bean from a Spring Boot auto-configuration.
5. Throw `RefusedException` (or a subclass) to refuse; never write a status of your own.
6. Extend `FormatModuleContract` in its tests.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.format.<id>.enabled` | `true` | Switches a format off, for example `kiln.format.npm.enabled=false` |

## Usage

```java
// In a format's request handling
access.check(artifact, Operation.PULL)
        .toRefusal("repository acme/app not found", () -> challenge)
        .ifPresent(refusal -> { throw new RefusedException(refusal); });

// In the web layer
FormatResponse response = FormatResponse.refusal(format, exception.refusal());
```

## Security

- **Threats**: a client probing for private artifacts; a mirror falling through to another
  registry on a misleading status; error messages that quote client input breaking out of JSON or
  splitting headers; a browser sniffing an error body as HTML
- **Untrusted input**: anything a format quotes in a refusal message
- **Defaults**: missing ports or listeners stop the boot (FMT-10, FMT-11)
- **Protections**: fixed statuses (FMT-1), central headers (FMT-2), header injection checks
  (FMT-3), escaped bodies (FMT-4), 401/403/404 by access outcome (FMT-8)

## Testing

- **Contract suite**: `FormatModuleContract` (test fixtures), run here against a minimal format
- **Fuzz targets**: none of its own; each format fuzzes its parsers
- **Other**: none

```bash
./gradlew :kiln-format:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110) (status codes, field names); [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details as the model for format-neutral errors
- **Dependencies**: Jackson (error bodies), Spring Boot auto-configuration
- **Reference projects**: distribution/distribution, Verdaccio, Reposilite (how each format reports errors)

## Status

Implemented: FMT-1 to FMT-12.

Planned: the HTTP routing that dispatches requests to formats by segment (M1); the policy
implementation of `ArtifactPolicy` (M1 immutability, M4 blocks and quotas).
