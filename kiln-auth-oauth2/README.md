# kiln-auth-oauth2

The generic OAuth2 connector for IdPs without OpenID Connect, with a GitHub preset.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-auth`](../kiln-auth) |
| Artifact | `com.bulmerkiln:kiln-auth-oauth2` |
| Runs in | Kiln's server |
| Depends on | [`kiln-auth`](../kiln-auth) |
| Used by | `kiln-oss-service`, `kiln-cloud-service` |
| Status | M2, Experimental: settings, the GitHub preset and user mapping; no Spring Security wiring yet |

## Purpose

Some IdPs, GitHub above all, have no OpenID Connect, so identity comes from a user-info endpoint
read through configured paths, with groups from extra API calls. This connector makes that safe:
the subject is always an immutable ID, and emails count only when the IdP vouches for them.

## Responsibilities

**Does**

- Validate OAuth2 connector settings and apply the GitHub preset (`OAuth2Connector`, `OAuth2Preset`)
- Read user info through dotted paths (`JsonPath`) and map it to an identity (`OAuth2UserMapper`)
- Turn GitHub's team list into groups

**Does not**

- Run the OAuth2 flow or make the lookup calls: Spring Security and the sign-in flow do
- Grant roles

## Requirements

Inherits every requirement of [`kiln-auth`](../kiln-auth#requirements), and adds:

| ID | Requirement | Verified by |
|---|---|---|
| AUTH-OAUTH2-1 | The subject must come from the configured user-ID path, as a JSON string or integer only, never a float, boolean or object, and with no fallback. For GitHub it's the numeric `id`, never the `login`, which can be renamed and then registered by someone else | `OAuth2ConnectorTest`, `GitHubConnectorContractTest` |
| AUTH-OAUTH2-2 | An email must be trusted only when the connector also configures where the IdP marks it verified, and that value is `true` | `OAuth2ConnectorTest` |
| AUTH-OAUTH2-3 | JSON paths must be 1 to 8 dotted object keys: no array indexes or wildcards, so a path names exactly one value | `OAuth2ConnectorTest` |
| AUTH-OAUTH2-4 | The GitHub preset must take groups from `/user/teams`, next to its user-info endpoint, as `org` and `org/team`, lowercased. A failed lookup only means fewer groups | `GitHubConnectorContractTest`, `OAuth2ConnectorTest` |
| AUTH-OAUTH2-5 | Every endpoint must be `https`. A generic connector must configure all of them; GitHub Enterprise Server overrides the preset's | `OAuth2ConnectorTest` |

## How it plugs in *(implementation only)*

Offers every connector under `kiln.auth.oauth2.connectors` through `OAuth2Connectors`, a
`ConnectorSource`. Connectors pass `IdentityConnectorContract` in `GitHubConnectorContractTest`
and `GenericOAuth2ConnectorContractTest`.

## Configuration

Under `kiln.auth.oauth2.connectors[n]`:

| Property | Default | Description |
|---|---|---|
| `id` | none, required | The connector ID (AUTH-12) |
| `display-name` | the ID | Shown on the sign-in page |
| `preset` | `generic` | `generic` or `github` |
| `issuer` | the preset's | The namespace subjects live in, such as `https://github.com` |
| `urls.authorization`, `urls.token`, `urls.user-info` | the preset's | The endpoints; required for `generic` |
| `urls.allow-insecure` | `false` | Allows `http` endpoints, for a local IdP only |
| `paths.user-id` | the preset's | Where the user ID is, such as `id` or `data.user.id` |
| `paths.email`, `paths.email-verified` | none | Where the email and its verified flag are; both or neither |
| `paths.groups` | none | Where a list of groups is |
| `scopes` | the preset's | Requested scopes |
| `client-id` | none, required | The client ID |
| `client-secret` | none, required | A secret reference, `env:` or `file:` |
| `domains` | none | Email domains for discovery |

## Usage

```yaml
kiln:
  auth:
    oauth2:
      connectors:
        - id: github
          preset: github
          client-id: Iv1.0123456789abcdef
          client-secret: env:KILN_GITHUB_SECRET
```

## Security

- **Threats**: account takeover by re-registering a renamed login; unverified emails; ambiguous
  paths reading the wrong value
- **Untrusted input**: user-info responses and lookup results, connector settings
- **Defaults**: no email unless verified; `https` endpoints only
- **Protections**: AUTH-OAUTH2-1, AUTH-OAUTH2-2, AUTH-OAUTH2-3

## Testing

- **Contract suite**: passes `IdentityConnectorContract` (GitHub and a generic connector)
- **Fuzz targets**: none
- **Other**: none

```bash
./gradlew :kiln-auth-oauth2:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: [RFC 6749](https://www.rfc-editor.org/rfc/rfc6749) (OAuth 2.0); [RFC 7636](https://www.rfc-editor.org/rfc/rfc7636) (PKCE); GitHub's [OAuth apps](https://docs.github.com/en/apps/oauth-apps) and [teams API](https://docs.github.com/en/rest/teams/teams#list-teams-for-the-authenticated-user)
- **Dependencies**: Spring Security's OAuth2 client (with the wiring)
- **Reference projects**: Dex's GitHub connector

## Status

Implemented: AUTH-OAUTH2-1 to AUTH-OAUTH2-5.

Planned (M2): Spring Security wiring; following GitHub's pagination for people in more than 100
teams; a verified email from GitHub's `/user/emails`; lookups for generic connectors.
