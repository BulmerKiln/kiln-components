# kiln-web

The web layer every format's endpoints run in: the organization and caller bound for each request,
and refusals answered in each format's own error shape.

## At a glance

| | |
|---|---|
| Kind | Foundation |
| Artifact | `com.bulmerkiln:kiln-web` |
| Runs in | Kiln's server |
| Depends on | [`kiln-core`](../kiln-core), [`kiln-format`](../kiln-format), [`kiln-permissions`](../kiln-permissions) |
| Used by | The `kiln-format-*` modules' controllers; [`kiln-auth`](../kiln-auth), which replaces the anonymous `PrincipalResolver` |
| Status | M1, Experimental |

## Purpose

Every format's controller must do the same three things before it touches an artifact: find the
organization, find the caller, and make sure a refusal reaches the client in the format's own
shape. Doing that once, here, means a controller can't forget to bind the organization or answer
a refusal as a `500`, and the controllers stay thin translations of HTTP.

## Responsibilities

**Does**

- Resolve the organization from the artifact path (`OrganizationResolver`) and the caller from
  the credentials (`PrincipalResolver`), and bind both for exactly one handler
- Answer every refusal through the format's `FormatModule`
- Let encoded slashes reach the formats, for npm's scoped names

**Does not**

- Define any route; each format's module registers its own controller, only when it's enabled
- Authenticate anyone; the authentication subsystem provides the `PrincipalResolver`

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| WEB-1 | A format handler must run with the organization from its artifact path and the caller from its credentials bound, and neither may outlive it. An organization that doesn't resolve must be answered as not found, like a missing artifact. Routes without an artifact bind the caller and no organization | `FormatRequestsTest` |
| WEB-2 | A refusal, from the handler or from resolving the caller, must be answered through the format's `FormatModule`, replacing any status or header the handler had set. Once a response is committed, the request fails instead. Anything that isn't a refusal is a fault and is never turned into an answer | `FormatRequestsTest` |
| WEB-3 | Every caller must be anonymous until a `PrincipalResolver` replaces the default, so nothing beyond public pulls is allowed before sign-in exists | `FormatRequestsTest`, `WebAutoConfigurationTest` |
| WEB-5 | Absolute URLs in responses must be built from `kiln.web.external-url`, never from the request's `Host` or forwarded headers, which clients control. The URL is required and must be plain http(s) with a host and no user info, query or fragment | `FormatRequestsTest`, `WebAutoConfigurationTest` |
| WEB-6 | The organization must be the first segment of the artifact path, looked up exactly as sent among active organizations in the `OrganizationDirectory`; the rest of the path is the artifact's. A service may provide its own `OrganizationResolver`; without one and without a directory, Kiln doesn't start | `PathOrganizationResolverTest`, `WebAutoConfigurationTest` |
| WEB-4 | Tomcat must pass encoded slashes (`%2F`) through rather than refuse them, since npm sends `@scope%2fname`; each format parses its names strictly | `WebAutoConfigurationTest` |

## Contract *(foundation only)*

| Type | Role |
|---|---|
| `FormatRequests` | `scoped(request, response, format, path, handler)` and `unscoped(…)`; `write(response, FormatResponse)` |
| `PrincipalResolver` | Who's calling; anonymous by default |
| `PathOrganizationResolver` | The organization from the first path segment, the default `OrganizationResolver` |

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.web.external-url` | none, required | The URL clients reach Kiln at, such as `https://pkg.example.com`; `Location` headers, token realms and links are built from it |
| `kiln.web.cors.allowed-origins` | none | Origins allowed to make cross-origin requests, with credentials; none means same-origin only |
| `kiln.web.cors.allowed-headers` | none | Headers allowed beyond Kiln's own (`Authorization`, `Kiln-Organization`, `Kiln-Format`, `Content-Type`) |
| `kiln.web.cors.allowed-methods` | none | Methods allowed beyond Kiln's own (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`) |

Organizations are resolved from the first path segment through the `OrganizationDirectory`.

## Usage

```java
@GetMapping("/v2/**")
void get(HttpServletRequest request, HttpServletResponse response) throws IOException {
    requests.scoped(request, response, format, repositoryPath(request), path -> manifests.serve(path, response));
}
```

## Security

- **Threats**: a handler acting for the wrong organization or caller; organizations discovered by
  probing paths; a refusal leaking as a `500` or carrying a handler's headers
- **Untrusted input**: the request path and credentials, which this module passes to the
  resolvers and never interprets itself
- **Defaults**: anonymous callers (WEB-3); unknown organizations look like missing artifacts
  (WEB-1)
- **Protections**: scoped values bound per handler (WEB-1); refusals only in the format's shape
  (WEB-2)

## Testing

- **Contract suite**: none
- **Fuzz targets**: none; it parses no input itself
- **Other**: Spring's mock servlet request and response

```bash
./gradlew :kiln-web:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: Jakarta Servlet 6.1
- **Dependencies**: Spring Boot's Tomcat connector customization
- **Reference projects**: distribution/distribution's request context

## Status

Implemented: WEB-1 to WEB-6.

Planned: data-plane request settings (no multipart parsing, streaming bodies, idle and read
timeouts).
