# kiln-auth

The authentication subsystem: who someone is. It holds the token format, the contract every
sign-in connector implements, users and their memberships, who may sign up, instance admins, web
sessions, and browser sign-in on Spring Security.

## At a glance

| | |
|---|---|
| Kind | Subsystem |
| Artifact | `com.bulmerkiln:kiln-auth` (plus a `test-fixtures` variant) |
| Runs in | Kiln's server |
| Depends on | [`kiln-core`](../kiln-core), [`kiln-database`](../kiln-database), [`kiln-web`](../kiln-web), Spring Security OAuth2 client |
| Used by | [`kiln-auth-oidc`](../kiln-auth-oidc), [`kiln-auth-oauth2`](../kiln-auth-oauth2), `kiln-oss-service` |
| Status | M1, Experimental |

## Purpose

People sign in through many IdPs: OIDC, plain OAuth2 such as GitHub, and others through their own connector
modules. Every connector must reach the same outcome, an identity with its groups, under the
same rules, so access behaves the same whichever IdP someone used. Long-lived tokens need a format
that's safe to store and detectable when leaked.

## Responsibilities

**Does**

- The token format: `bkp_` personal and `bks_` service tokens (`TokenSecret`)
- The connector contract: `IdentityConnector`, `ExternalIdentity`, the shared `IdentityUtils`
- `ConnectorRegistry`: every connector from every module, with startup checks
- Connector settings rules: connector IDs, secret references, endpoints, domains
- `IdentityConnectorContract`, the suite every connector passes
- Users, instance-wide, with their identities; members per organization (tables `users`,
  `user_identities`, `members`)
- Who may become a user: invite links, allowed domains and bootstrap config, with sign-ups
  otherwise off (`invitations`, `allowed_domains`)
- Linking a further identity to a signed-in user
- Instance admins as grants in their own table (`instance_admins`)
- Web sessions kept as the SHA-256 of their IDs (`web_sessions`)
- Browser sign-in on Spring Security's OAuth2 login at `/auth/<connector-id>/callback`, with each
  connector module's client registration (`SecurityAutoConfiguration`), and the session-based
  `PrincipalResolver` (in progress: AUTH-18)

**Does not**

- Decide what anyone may do: connectors surface groups, and roles come from teams in
  [`kiln-permissions`](../kiln-permissions)
- Run the protocol flows themselves: redirects, PKCE and token exchange are Spring Security's
- Decide who may invite or grant: that's the admin API's check; these services do what they're asked

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
| AUTH-13 | Kiln must refuse to start without at least one connector. An assembly's own registry replaces the static one | `StaticConnectorRegistryTest` |
| AUTH-14 | Secrets must be references, `env:NAME` or `file:/absolute/path`, never values. Anything else is refused without repeating it | `ConnectorContractTest` |
| AUTH-15 | IdP endpoints must be `https` URLs with a host and no user info or fragment; plain `http` only when a connector allows it explicitly. Lookup URLs are always `https` | `ConnectorContractTest`, `StaticConnectorRegistryTest` |
| AUTH-16 | Discovery domains must be plain ASCII hostnames, stored lowercase and matched exactly: a subdomain doesn't match its parent | `ConnectorContractTest`, `StaticConnectorRegistryTest` |
| AUTH-17 | Errors must never repeat the values they refuse | `IdentityConnectorContract`, `ConnectorContractTest` |
| AUTH-18 | Browser sign-in runs on Spring Security's OAuth2 login: authorization code with PKCE, state and nonce, a 12-hour session cookie, session fixation protection and CSRF. Each connector module contributes a `SignInClientDirectory` from its own auto-configuration, built at boot or on first use; the callback is always `/auth/<connector-id>/callback`; disabling a module removes its protocol | `SecurityAutoConfigurationTest`, `SignInClientDirectoryTest` |
| AUTH-19 | A connector's client secret must be resolved at boot, so a missing or unreadable one stops the boot with an error naming the connector and the setting, and must never appear in a sign-in client's string form | `SecretResolverTest`, `SignInClientDirectoryTest` |
| AUTH-20 | Sign-ups must be off: a sign-in becomes a user only as an identity a user already has, through an invite link, a bootstrap entry, or a verified email at an allowed domain, tried in that order. Anything else is refused, records nothing but a log line naming the connector and the reason, and the reason never reaches the person. A disabled user is refused | `SignInServiceTest` |
| AUTH-21 | An invite link must be a 256-bit random token, stored only as its SHA-256, single-use and valid for 7 days. Claiming it, through any connector, creates the user, or adds an existing user, as a member of its organization. An unknown, used or expired link is refused | `SignInServiceTest`, `OpaqueSecretTest` (fuzz target) |
| AUTH-22 | An email may create a user once, ever. After that the user is matched by issuer and subject only: another identity with the same email, in any case, is refused, and an email change at the IdP doesn't move the user. A further identity is linked only explicitly, by a session that signed in within 10 minutes; an identity another user holds is never moved | `SignInServiceTest`, `IdentityLinkServiceTest` |
| AUTH-23 | An allowed domain (`kiln.auth.allowed-domains`) admits a verified email only through its own connector, and matches the domain exactly, ignoring case: a subdomain or look-alike doesn't match. The new user joins its organization as a member with no role. The config is validated at boot and reconciled with the table, so removing an entry stops it admitting anyone | `SignInServiceTest`, `SignUpAutoConfigurationTest` |
| AUTH-24 | Instance admins must be grants in their own table, never a flag on the user. Bootstrap grants come from `kiln.bootstrap.admin-emails` (claimed under AUTH-23's rules) and `admin-subjects` (an issuer and subject pair, never the subject alone), and are reconciled with the config on every start; other grants come only from an active instance admin and survive reconciling. A disabled user is no instance admin | `InstanceAdminServiceTest`, `SignInServiceTest`, `SignUpAutoConfigurationTest` |
| AUTH-25 | A user is a member only of the organizations they were added to, each in its own row-level-secured table; a disabled user or member is no member, and reading members without an organization is refused by the database | `MemberServiceTest`, `SignInServiceTest`, `UserSchemaTest` |
| AUTH-26 | A web session belongs to one user, has a new 256-bit random ID at every sign-in, stored only as its SHA-256, and lasts 12 hours from sign-in however active it is. A malformed, unknown or expired ID, or a disabled user, finds no session; expired rows are removed | `SessionServiceTest` |
| AUTH-27 | The runtime role may change only what the application changes: a user's name and status, an identity's last sign-in, a member's status, and an invitation's claim, once. Everything else in these tables is fixed when the row is created, so a bug or an injection can't clear an invitation's organization, move an identity, membership or session to another user, extend a session or an invitation, or turn an instance admin grant into another kind | `UserTablePrivilegesTest` |

## Contract *(subsystem only)*

| Type | Role |
|---|---|
| `IdentityConnector` | A connector: its ID, type, display name, domains, whether it provides groups, its lookups, and `identify` |
| `IdpResponse` | The verified response: claims, user info or assertion attributes, plus the lookups' results |
| `ExternalIdentity` | The outcome: connector, issuer, subject, verified email, groups |
| `IdentityUtils` | The shared rules for subjects, emails and groups |
| `ConnectorSource` | How a module offers its connectors |
| `ConnectorRegistry`, `StaticConnectorRegistry` | Every connector, for sign-in and discovery |
| `ConnectorId`, `SecretRef`, `EndpointUtils`, `DomainUtils` | Settings rules every connector uses |
| `IdentityConnectorContract` *(test fixtures)* | The suite every connector extends |
| `SecretResolver` | Reads a `SecretRef` from its environment variable or file |
| `SignInService` | Who an identity signs in as, or why it's refused (AUTH-20 to AUTH-24) |
| `InvitationService`, `IdentityLinkService` | Invite links, and linking a further identity to a user |
| `InstanceAdminService`, `AllowedDomainService` | Instance admin grants, and allowed domains, each reconciled with the config |
| `MemberService` | The user as a member of the current organization, and the organizations they belong to |
| `SessionService`, `OpaqueSecret` | Web sessions, and the random secrets kept only as hashes |
| `SecurityAutoConfiguration`, `SessionPrincipalResolver` | The Spring Security filter chain and OAuth2 login, and the caller from the session |

**Rules enforced here**: one outcome for every connector (AUTH-6), the identity rules (AUTH-7 to
AUTH-10), unique connector IDs (AUTH-12), at least one connector (AUTH-13).

## Adding an implementation *(subsystem only)*

1. Create `kiln-auth-<protocol>` and depend on `kiln-auth`.
2. Implement `IdentityConnector`, applying `IdentityUtils` in `identify`. Declare any extra API
   calls the sign-in flow should make (such as group lookups) in `lookups()`.
3. Validate settings with `ConnectorId`, `SecretRef`, `EndpointUtils` and `DomainUtils`, naming the
   connector and setting in every error.
4. Offer the connectors through a `ConnectorSource` bean from a Spring Boot auto-configuration.
5. Extend `IdentityConnectorContract` in its tests.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.bootstrap.admin-emails` | none | Verified emails whose first sign-in, through a connector allowed for their domain, creates an instance admin |
| `kiln.bootstrap.admin-subjects` | none | `issuer` and `subject` pairs that are instance admins, for IdPs without verified emails |
| `kiln.auth.allowed-domains` | none | `connector`, `domain` and `organization` slug: a verified email at the domain, through the connector, creates a member of the organization |

```yaml
kiln:
  bootstrap:
    admin-emails: [root@acme.com]
  auth:
    allowed-domains:
      - connector: google
        domain: acme.com
        organization: acme
```

Each connector module has its own settings.

## Usage

```java
TokenSecret token = TokenSecret.generate(TokenKind.PERSONAL);
store(token.sha256());           // show token.reveal() to its owner once
Optional<TokenSecret> presented = TokenSecret.parse(header);

ExternalIdentity who = registry.get(connectorId).orElseThrow().identify(response);
```

## Security

- **Threats**: stolen or leaked tokens; an attacker setting an unverified email at an IdP to be
  matched to its owner, or a second account claiming a verified email again; an IdP account at a
  look-alike domain or subdomain; an ex-employee's lingering IdP account; a leaked database copy
  replayed as session cookies or invite links; a forged admin grant; a misconfigured or compromised IdP granting access; inline secrets in
  configuration; connector IDs or domains crafted to confuse routing or discovery
- **Untrusted input**: presented tokens, IdP responses, connector settings
- **Defaults**: no groups means no roles (AUTH-10); no connector means no start (AUTH-13);
  sign-ups off (AUTH-20)
- **Protections**: AUTH-2 to AUTH-5, AUTH-7, AUTH-9, AUTH-11, AUTH-14, AUTH-15, AUTH-20 to
  AUTH-27

## Testing

- **Contract suite**: `IdentityConnectorContract` (test fixtures), run here against a minimal connector
- **Fuzz targets**: token parsing (`TokenSecretTest`), opaque secrets (`OpaqueSecretTest`)
- **Abuse cases**: a second identity with a claimed email, an unverified email, a subdomain or
  look-alike domain, a bootstrap email or subject through the wrong connector or issuer, an
  expired, reused or made-up invite link, linking someone else's identity, malformed session IDs
- **Two organizations**: allowed domains and members stay in their organization
  (`SignInServiceTest`, `MemberServiceTest`). The authorization matrix arrives with the routes
  that use these services
- **Other**: a fixed token computed outside the code guards the token format

```bash
./gradlew :kiln-auth:test
```

The user, member and session tests run against PostgreSQL in Testcontainers, so they need
Docker.

## Standards and references

- **Standards**: [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html); [RFC 6749](https://www.rfc-editor.org/rfc/rfc6749) (OAuth 2.0); [RFC 9700](https://www.rfc-editor.org/rfc/rfc9700) (OAuth 2.0 security best practice); GitHub's [token format](https://github.blog/2021-04-05-behind-githubs-new-authentication-token-formats/) and [secret scanning patterns](https://docs.github.com/en/code-security/secret-scanning)
- **Dependencies**: the JDK (`SecureRandom`, `MessageDigest`, `CRC32`), Spring Security (OAuth2 client and
  config), jOOQ through [`kiln-database`](../kiln-database), Spring Boot auto-configuration
- **Reference projects**: Dex (connector model), Keycloak, GitLab personal access tokens

## Status

Implemented: AUTH-1 to AUTH-17, AUTH-19 to AUTH-27. In progress: AUTH-18, browser sign-in, which doesn't yet work end
to end.

Planned: backing the browser session with `SessionService`, the linking and invite-link flows on
the sign-in routes, `/login` and logout, and the token service (`/token`, with Docker token auth) (M1); the
device grant; trusted
publishing for GitHub and GitLab CI and Kubernetes workload identity (M2); device-bound keys with
DPoP and service key pairs (M3). Each rung of the credential ladder will get its own contract and
suite here, producing a principal for `kiln-permissions`.
