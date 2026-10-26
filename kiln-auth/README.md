# kiln-auth

The authentication subsystem: who someone is. It holds the token format and the contract every
sign-in connector implements.

## At a glance

| | |
|---|---|
| Kind | Subsystem |
| Artifact | `com.bulmerkiln:kiln-auth` (plus a `test-fixtures` variant) |
| Runs in | Kiln's server |
| Depends on | Nothing but the JDK and Spring Boot auto-configuration |
| Used by | [`kiln-auth-oidc`](../kiln-auth-oidc), [`kiln-auth-oauth2`](../kiln-auth-oauth2); kiln-cloud's SAML and Entra ID connectors |
| Status | M1, Experimental |

## Purpose

People sign in through many IdPs: OIDC, plain OAuth2 such as GitHub, and SAML or Entra ID in
kiln-cloud. Every connector must reach the same outcome, an identity with its groups, under the
same rules, so access behaves the same whichever IdP someone used. Long-lived tokens need a format
that's safe to store and detectable when leaked.

## Responsibilities

**Does**

- The token format: `bkp_` personal and `bks_` service tokens (`TokenSecret`)
- The connector contract: `IdentityConnector`, `ExternalIdentity`, the shared `IdentityRules`
- `ConnectorRegistry`: every connector from every module, with startup checks
- Connector settings rules: connector IDs, secret references, endpoints, domains
- `IdentityConnectorContract`, the suite every connector passes

**Does not**

- Decide what anyone may do: connectors surface groups, and roles come from teams in
  [`kiln-permissions`](../kiln-permissions)
- Run the protocol flows themselves: redirects, PKCE and token exchange are Spring Security's

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| AUTH-1 | Tokens must be `bkp_` (personal) or `bks_` (service), then 30 random base62 characters (about 178 bits, drawn uniformly by rejection sampling), then a 6-character base62 CRC32 of the random part | `TokenSecretTest` (a fixed token guards the format) |
| AUTH-2 | Tokens must be stored only as their SHA-256 hash and compared in constant time; the hash handed out is a copy | `TokenSecretTest` |
| AUTH-3 | A token's `toString()` must never reveal the secret | `TokenSecretTest` |
| AUTH-4 | The checksum must reject every single-character typo, and any malformed shape, before a database lookup | `TokenSecretTest` (fuzz target) |
| AUTH-5 | Leaked tokens must be findable with the pattern `bk[ps]_[0-9A-Za-z]{36}`, for GitHub secret scanning and pre-commit hooks | `TokenSecretTest` |
| AUTH-6 | Every connector must turn its IdP's verified response into an `ExternalIdentity` through `identify`, with the same result for the same response | `IdentityConnectorContract` |
| AUTH-7 | A person must be identified only by issuer and subject. An email is a hint for display and discovery, used only when the IdP marks it verified with a JSON `true` | `IdentityConnectorContract`, `StaticConnectorRegistryTest` |
| AUTH-8 | A subject must be 1 to 255 printable characters, or the sign-in fails | `IdentityConnectorContract` |
| AUTH-9 | Groups may come as one name or a list. Non-strings, blank names and names with control characters are dropped, since dropping a group only removes access; more than 1,000 groups, or a name over 255 characters, fails the sign-in | `IdentityConnectorContract` |
| AUTH-10 | No groups from the IdP must mean no groups, and a connector without group support reports none: such a person gets no roles until an admin grants some | `IdentityConnectorContract` |
| AUTH-11 | Connectors must never grant roles; they report groups only | By type: `ExternalIdentity` has no roles |
| AUTH-12 | Connector IDs must be lowercase letters and digits with single inner hyphens, at most 63 characters, safe in callback paths and cookies, and unique across every protocol | `ConnectorContractTest`, `StaticConnectorRegistryTest` |
| AUTH-13 | Kiln must refuse to start without at least one connector. An assembly's own registry (kiln-cloud's, per organization) replaces the static one | `StaticConnectorRegistryTest` |
| AUTH-14 | Secrets must be references, `env:NAME` or `file:/absolute/path`, never values. Anything else is refused without repeating it | `ConnectorContractTest` |
| AUTH-15 | IdP endpoints must be `https` URLs with a host and no user info or fragment; plain `http` only when a connector allows it explicitly. Lookup URLs are always `https` | `ConnectorContractTest`, `StaticConnectorRegistryTest` |
| AUTH-16 | Discovery domains must be plain ASCII hostnames, stored lowercase and matched exactly: a subdomain doesn't match its parent | `ConnectorContractTest`, `StaticConnectorRegistryTest` |
| AUTH-17 | Errors must never repeat the values they refuse | `IdentityConnectorContract`, `ConnectorContractTest` |

## Contract *(subsystem only)*

| Type | Role |
|---|---|
| `IdentityConnector` | A connector: its ID, type, display name, domains, whether it provides groups, its lookups, and `identify` |
| `IdpResponse` | The verified response: claims, user info or assertion attributes, plus the lookups' results |
| `ExternalIdentity` | The outcome: connector, issuer, subject, verified email, groups |
| `IdentityRules` | The shared rules for subjects, emails and groups |
| `ConnectorSource` | How a module offers its connectors |
| `ConnectorRegistry`, `StaticConnectorRegistry` | Every connector, for sign-in and discovery |
| `ConnectorId`, `SecretRef`, `Endpoints`, `Domains` | Settings rules every connector uses |
| `IdentityConnectorContract` *(test fixtures)* | The suite every connector extends |

**Rules enforced here**: one outcome for every connector (AUTH-6), the identity rules (AUTH-7 to
AUTH-10), unique connector IDs (AUTH-12), at least one connector (AUTH-13).

## Adding an implementation *(subsystem only)*

1. Create `kiln-auth-<protocol>` and depend on `kiln-auth`.
2. Implement `IdentityConnector`, applying `IdentityRules` in `identify`. Declare any extra API
   calls the sign-in flow should make (such as group lookups) in `lookups()`.
3. Validate settings with `ConnectorId`, `SecretRef`, `Endpoints` and `Domains`, naming the
   connector and setting in every error.
4. Offer the connectors through a `ConnectorSource` bean from a Spring Boot auto-configuration.
5. Extend `IdentityConnectorContract` in its tests.

## Configuration

None of its own: each connector module has its own settings.

## Usage

```java
TokenSecret token = TokenSecret.generate(TokenKind.PERSONAL);
store(token.sha256());           // show token.reveal() to its owner once
Optional<TokenSecret> presented = TokenSecret.parse(header);

ExternalIdentity who = registry.get(connectorId).orElseThrow().identify(response);
```

## Security

- **Threats**: stolen or leaked tokens; an attacker setting an unverified email at an IdP to be
  matched to its owner; a misconfigured or compromised IdP granting access; inline secrets in
  configuration; connector IDs or domains crafted to confuse routing or discovery
- **Untrusted input**: presented tokens, IdP responses, connector settings
- **Defaults**: no groups means no roles (AUTH-10); no connector means no start (AUTH-13)
- **Protections**: AUTH-2 to AUTH-5, AUTH-7, AUTH-9, AUTH-11, AUTH-14, AUTH-15

## Testing

- **Contract suite**: `IdentityConnectorContract` (test fixtures), run here against a minimal connector
- **Fuzz targets**: token parsing (`TokenSecretTest`)
- **Other**: a fixed token computed outside the code guards the token format

```bash
./gradlew :kiln-auth:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html); [RFC 6749](https://www.rfc-editor.org/rfc/rfc6749) (OAuth 2.0); [RFC 9700](https://www.rfc-editor.org/rfc/rfc9700) (OAuth 2.0 security best practice); GitHub's [token format](https://github.blog/2021-04-05-behind-githubs-new-authentication-token-formats/) and [secret scanning patterns](https://docs.github.com/en/code-security/secret-scanning)
- **Dependencies**: the JDK (`SecureRandom`, `MessageDigest`, `CRC32`), Spring Boot auto-configuration
- **Reference projects**: Dex (connector model), Keycloak, GitLab personal access tokens

## Status

Implemented: AUTH-1 to AUTH-17.

Planned: the token service (`/token`, with Docker token auth and the device grant) and Spring
Security wiring for the connectors (M1); trusted publishing for GitHub and GitLab CI and
Kubernetes workload identity (M2); device-bound keys with DPoP and service key pairs (M3). Each
rung of the credential ladder will get its own contract and suite here, producing a principal
for `kiln-permissions`.
