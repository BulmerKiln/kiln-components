# kiln-auth-oidc

The generic OpenID Connect connector, with presets for common IdPs.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-auth`](../kiln-auth) |
| Artifact | `com.bulmerkiln:kiln-auth-oidc` |
| Runs in | Kiln's server |
| Depends on | [`kiln-auth`](../kiln-auth) |
| Used by | `kiln-oss-service`, `kiln-cloud-service` |
| Status | M1, Experimental: settings, presets and claim mapping; no Spring Security wiring yet |

## Purpose

Most IdPs speak OpenID Connect, so one connector with presets covers Keycloak, Authentik, Ory,
Okta, GitLab, Google Workspace and the quick-start's Dex. Okta is a preset here rather than a
module of its own. The presets only set defaults; any connector can override them.

## Responsibilities

**Does**

- Validate OIDC connector settings and apply presets (`OidcConnector`, `OidcPreset`)
- Turn a verified ID token's claims into an identity (`OidcClaimsMapper`)
- Offer the configured connectors to the registry (`OidcConnectors`)

**Does not**

- Validate ID token signatures, audience, expiry or nonce: Spring Security does that before the
  claims reach this module
- Grant roles

## Requirements

Inherits every requirement of [`kiln-auth`](../kiln-auth#requirements), and adds:

| ID | Requirement | Verified by |
|---|---|---|
| AUTH-OIDC-1 | A token's `iss` must equal the configured issuer exactly, with no normalization: not a trailing slash, case change, port, scheme or path difference, or a token from another IdP or realm could pass | `OidcClaimsMapperTest` |
| AUTH-OIDC-2 | `sub` must be a string | `OidcClaimsMapperTest`, `OidcConnectorContractTest` |
| AUTH-OIDC-3 | The Google Workspace preset must require domains, and accept only tokens whose `hd` claim is exactly one of them, since Google otherwise signs in any Google account. Other presets ignore `hd` | `OidcConnectorTest`, `OidcClaimsMapperTest` |
| AUTH-OIDC-4 | Groups must come only from the connector's groups claim, so a token can't inject groups through another claim | `OidcClaimsMapperTest` |
| AUTH-OIDC-5 | Scopes must include `openid`, and the issuer must have no query | `OidcConnectorTest` |
| AUTH-OIDC-6 | A bad connector must stop the boot with an error naming the connector and the setting | `OidcConnectorTest`, `OidcAutoConfigurationTest` |

## How it plugs in *(implementation only)*

Offers every connector under `kiln.auth.oidc.connectors` through `OidcConnectors`, a
`ConnectorSource`. Each connector passes `IdentityConnectorContract` in `OidcConnectorContractTest`.

| Preset | Groups claim | Default scopes |
|---|---|---|
| `generic` | `groups` | `openid profile email` |
| `dex` | `groups` | `openid profile email groups` |
| `keycloak` | `groups` | `openid profile email` |
| `authentik` | `groups` | `openid profile email` |
| `ory` | `groups` | `openid profile email` |
| `okta` | `groups` | `openid profile email groups` |
| `gitlab` | `groups_direct` | `openid profile email` |
| `google-workspace` | none | `openid profile email` |

## Configuration

Under `kiln.auth.oidc.connectors[n]`:

| Property | Default | Description |
|---|---|---|
| `id` | none, required | The connector ID (AUTH-12) |
| `display-name` | the ID | Shown on the sign-in page |
| `preset` | `generic` | One of the presets above |
| `issuer` | none, required | The issuer URL, matched exactly |
| `client-id` | none, required | The client ID |
| `client-secret` | none, required | A secret reference, `env:` or `file:` |
| `scopes` | the preset's | Must include `openid` |
| `groups-claim` | the preset's | The claim holding groups |
| `domains` | none | Email domains for discovery; required for `google-workspace` |
| `allow-insecure-issuer` | `false` | Allows an `http` issuer, for a local IdP only |

## Usage

```yaml
kiln:
  auth:
    oidc:
      connectors:
        - id: acme-sso
          preset: okta
          issuer: https://acme.okta.com
          client-id: kiln
          client-secret: env:KILN_OKTA_SECRET
          domains: [acme.com]
```

## Security

- **Threats**: a token from another IdP or realm; any Google account signing in to a Workspace
  connector; groups injected through an unexpected claim; inline client secrets
- **Untrusted input**: ID token claims (already signature-checked), connector settings
- **Defaults**: `https` issuers only; a bad connector stops the boot
- **Protections**: AUTH-OIDC-1, AUTH-OIDC-3, AUTH-OIDC-4, and the inherited AUTH-7 to AUTH-10, AUTH-14

## Testing

- **Contract suite**: passes `IdentityConnectorContract` (Keycloak, and Google Workspace without groups)
- **Fuzz targets**: none
- **Other**: none

```bash
./gradlew :kiln-auth-oidc:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html) (section 3.1.3.7, ID token validation), [OpenID Connect Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html), [RFC 7636](https://www.rfc-editor.org/rfc/rfc7636) (PKCE)
- **Dependencies**: Spring Security's OAuth2 client (with the wiring)
- **Reference projects**: Dex, Keycloak

## Status

Implemented: AUTH-OIDC-1 to AUTH-OIDC-6.

Planned (M1): Spring Security wiring (discovery, authorization code with PKCE, ID token
validation), `login_hint` from IdP discovery, and refresh checks for deprovisioning. Each
preset's groups claim follows its IdP's documentation, and must be confirmed against the real IdP
when the connector is first used.
