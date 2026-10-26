# kiln-permissions

The permissions subsystem: what a principal may do. Roles, role bindings, teams from IdP groups,
and the access decision.

## At a glance

| | |
|---|---|
| Kind | Subsystem |
| Artifact | `com.bulmerkiln:kiln-permissions` (plus a `test-fixtures` variant) |
| Runs in | Kiln's server |
| Depends on | Spring Boot auto-configuration; optionally [`kiln-format`](../kiln-format), to answer its `ArtifactAccess` port |
| Used by | The format subsystem, the API and web app, the token service |
| Status | M1, Experimental |

## Purpose

Authentication says who someone is; this module decides what they may do, the same way for every
format, endpoint and token. It never asks how someone signed in. It grants nothing by default, and
it answers "you may not" with `404` wherever `403` would reveal that a private resource exists.

## Responsibilities

**Does**

- The roles and what each allows (`Role`, `Action`)
- Role bindings at organization, namespace or repository level (`RoleBinding`, `Scope`)
- Teams from IdP groups (`TeamMembership`)
- The decision: allow, ask to sign in, deny or hide (`PermissionPolicy`, `RoleBasedPolicy`)
- The current principal for each request (`CurrentPrincipal`)
- `PermissionPolicyContract`, the authorization matrix every policy passes

**Does not**

- Authenticate anyone; [`kiln-auth`](../kiln-auth) establishes the principal
- Store bindings or visibility; the data layer implements `RoleBindingRepository` and `VisibilityLookup`
- Apply blocks, quotas or immutability; those are policy, behind `kiln-format`'s `ArtifactPolicy`

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| PERM-1 | Roles must grant exactly: `reader` pull; `writer` pull and push (including publish); `security` pull, block, unblock and security views; `admin` every action, including delete and management | `PermissionPolicyContract` |
| PERM-2 | Roles must be granted to a team, a member or a service account, at the organization, a namespace or one repository. A scope covers itself and everything inside it, and names match exactly, so `acme` never covers `acme-evil` or `Acme` | `PermissionPolicyContract` |
| PERM-3 | Roles from several bindings must add up | `PermissionPolicyContract` |
| PERM-4 | Every decision must be one of four. `ALLOW`. `AUTHENTICATE` (401) for anyone not signed in, whether or not the resource exists, so docker and npm prompt for a login and the answer reveals nothing. `DENY` (403) when the principal may know the resource exists: they may do something to it, or it's their own organization. `HIDE` (404) otherwise, so private resources can't be probed | `PermissionPolicyContract` |
| PERM-5 | Nothing may be granted by default: a member with no teams and no bindings can do nothing, and gets 403 on their own organization's settings | `PermissionPolicyContract` |
| PERM-6 | A service account must get only its own bindings. A workload matched by a trusted publisher must get exactly that publisher's grants, never a role | `PermissionPolicyContract` |
| PERM-7 | Anyone, signed in or not, may pull public content, and nothing more | `PermissionPolicyContract` |
| PERM-8 | Bindings from the data layer must be rechecked against the principal, so a binding returned by mistake can't grant anything | `RoleBasedPolicyTest` |
| PERM-9 | IdP groups must map to teams per connector and group name, matched exactly, so the same group name from two IdPs never means the same thing. Membership is recomputed at every sign-in; no mapped groups leaves only explicit teams | `RoleBasedPolicyTest` |
| PERM-10 | The current principal must be bound for each request, explicitly as anonymous when there are no credentials. Reading it unbound fails, and a request can't switch principal | `RoleBasedPolicyTest` |
| PERM-11 | There must be no default for stored bindings or visibility: without a `RoleBindingRepository`, or a `VisibilityLookup` when formats are present, the boot fails | `RoleBasedPolicyTest` |
| PERM-12 | When the format subsystem is present, this module must answer its `ArtifactAccess` port with the same decisions | `RoleBasedPolicyTest` |

## Contract *(subsystem only)*

| Type | Role |
|---|---|
| `PermissionPolicy` | Decides: `allowed(principal, resource)` and `decide(principal, action, resource)` |
| `Principal` | Member, service account, workload or anonymous |
| `Role`, `Action`, `RoleBinding`, `Scope`, `Resource`, `Visibility`, `Decision` | The model |
| `RoleBindingRepository` | Port: stored bindings, implemented by the data layer |
| `VisibilityLookup` | Port: whether a namespace or repository is public, implemented by the data layer |
| `CurrentPrincipal` | The principal bound for the current request |
| `PermissionPolicyContract` *(test fixtures)* | The authorization matrix every policy extends |

**Rules enforced here**: the four-way decision and when each applies (PERM-4), nothing by
default (PERM-5), rechecked bindings (PERM-8), no default data sources (PERM-11).

## Adding an implementation *(subsystem only)*

A different policy, for example one backed by an external policy engine:

1. Implement `PermissionPolicy`. Override `allowed`; keep the default `decide` unless the four-way
   rule itself changes, which changes PERM-4.
2. Register it as a `PermissionPolicy` bean; `RoleBasedPolicy` then steps aside.
3. Extend `PermissionPolicyContract` in its tests.

## Configuration

None.

## Usage

```java
CurrentPrincipal.run(principal, () -> {
    Decision decision = policy.decide(CurrentPrincipal.get(), Action.PUSH, resource);
    // ALLOW, AUTHENTICATE, DENY or HIDE
});
```

## Security

- **Threats**: privilege escalation through a binding meant for someone else; existence leaks
  through `403` versus `404`; a namespace lookalike gaining access; a workload acting beyond its
  trusted-publisher rule; an IdP group from one connector matching another's
- **Untrusted input**: none directly; it decides on principals and resources others established
- **Defaults**: no roles, no access (PERM-5); no data sources, no start (PERM-11)
- **Protections**: PERM-2, PERM-4 to PERM-10

## Testing

- **Contract suite**: `PermissionPolicyContract` (test fixtures): every role, every action, every
  scope level and every kind of principal. Run here against `RoleBasedPolicy`
- **Fuzz targets**: none
- **Other**: none

```bash
./gradlew :kiln-permissions:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: the NIST RBAC model; [OWASP ASVS](https://owasp.org/www-project-application-security-verification-standard/) access-control requirements
- **Dependencies**: Spring Boot auto-configuration; `ScopedValue` (JEP 506)
- **Reference projects**: Harbor project roles, GitLab roles, GitHub's 404 for private repositories

## Status

Implemented: PERM-1 to PERM-12.

Planned (M1): the Postgres-backed `RoleBindingRepository`, `VisibilityLookup` and team
membership; the API and web-app endpoints' own authorization-matrix tests against every role and
credential rung.
