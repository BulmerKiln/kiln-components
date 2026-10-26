# Module README template

Every module in `kiln-components` has a `README.md` that follows this structure, so a contributor
can open any module and find the same things in the same place. Copy the template below into
`<module>/README.md` and fill it in.

## Rules

- **The README owns the module's requirements.** It states them in full, here, next to the code
  that meets them. It never defers to a central design document, which drifts out of date as the
  code moves on. Wider architecture that spans modules belongs in the README of the subsystem
  that owns it.
- **Every requirement names the tests that verify it.** A requirement without a test is a wish;
  add the test or mark the requirement as planned in **Status**.
- **Keep every section, in this order.** Write "None." rather than deleting a section, so it's
  clear the question was considered. The sections marked *subsystem only* or *implementation
  only* are the exception: leave out the ones that don't apply to the module's kind.
- **Say what's true today.** Anything not built yet goes in **Status**, not in the other sections.
- **Keep it short.** A README is an entry point. Most are one or two screens; the requirements
  table is the part that grows.
- **Update it in the same pull request** as any change to the module's requirements, contract,
  configuration or security behaviour.

## Module kinds

| Kind | What it is | Examples |
|---|---|---|
| **Foundation** | The kernel and build tooling. No subsystem contract of its own | `kiln-core`, `kiln-build`, `kiln-dependencies` |
| **Subsystem** | Defines a contract: the interfaces implementations provide, the rules every implementation must follow, the ports other subsystems use, and a contract test suite | `kiln-auth`, `kiln-format`, `kiln-permissions`, `kiln-scan`, `kiln-storage` |
| **Implementation** | Implements a subsystem's contract and passes its contract suite | `kiln-auth-oidc`, `kiln-format-npm`, `kiln-scan-trivy`, `kiln-storage-fs` |

A module that both implements one subsystem and serves another, such as `kiln-observability`
(which listens to `kiln-format`'s events), is listed under its main role and names the others in
**At a glance**.

A subsystem's requirements apply to every implementation. An implementation's README lists only
the requirements it adds, and says which subsystem's requirements it inherits.

---

## Template

````markdown
# kiln-<name>

<One sentence: what this module is.>

## At a glance

| | |
|---|---|
| Kind | Foundation / Subsystem / Implementation of [`kiln-<subsystem>`](../kiln-<subsystem>) |
| Artifact | `com.bulmerkiln:kiln-<name>` |
| Runs in | Kiln's server / the scan worker / both / build time only |
| Depends on | <the kiln-* modules it depends on, and any optional dependencies with what they enable> |
| Used by | <modules or deployables that use it> |
| Status | <milestone, and Experimental / Stable> |

## Purpose

<Two to four sentences: the problem this module solves and why it's a separate module.>

## Responsibilities

**Does**

- <what this module owns>

**Does not**

- <what a reader might expect here but belongs elsewhere, and where it lives>

## Requirements

<For an implementation: "Inherits every requirement of [`kiln-<subsystem>`](../kiln-<subsystem>#requirements), and adds:">

| ID | Requirement | Verified by |
|---|---|---|
| <PREFIX>-1 | <One testable statement using "must": what the module must do or must never do, and why when it isn't obvious> | `<TestClass>` |

<IDs use a short prefix per module (for example `STO` for kiln-storage, `STO-FS` for
kiln-storage-fs). They never change meaning or get reused, so tests, issues and pull requests can
cite them. A requirement that's planned but not met yet goes in **Status**, not here.>

## Contract *(subsystem only)*

<The interfaces implementations provide, and the ports other subsystems use. One line each.>

| Type | Role |
|---|---|
| `<Interface>` | <what implementations provide> |

**Rules enforced here**: <the behaviour every implementation gets for free, or can't get wrong,
because this module enforces it centrally, by type where possible.>

## Adding an implementation *(subsystem only)*

1. Create `kiln-<subsystem>-<name>` and depend on `kiln-<subsystem>`.
2. Implement `<Interface>`.
3. Register it from a Spring Boot auto-configuration.
4. Extend `<ContractSuite>` in its tests.

<Anything else an implementer must know: selection, naming, routes, IDs.>

## How it plugs in *(implementation only)*

<Which contract it implements, how it's selected or switched on (for example
`kiln.storage.type=fs`), and which contract suite it passes. Anything it adds beyond the contract.>

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.<...>` | <default> | <what it does> |

<Secrets are always references (`env:` or `file:`), never values. Write "None." if the module has
no configuration.>

## Usage

```kotlin
dependencies {
    implementation(platform("com.bulmerkiln:kiln-dependencies:<version>"))
    implementation("com.bulmerkiln:kiln-<name>")
}
```

<A minimal example of the main entry point, or what a deployable needs to do to use it.>

## Security

- **Threats**: <who might attack this module and how: hostile clients, a compromised worker, a
  malicious IdP response, another organization>
- **Untrusted input**: <what this module parses or receives from outside, and how it's bounded>
- **Defaults**: <what happens when something is missing or misconfigured; Kiln fails closed>
- **Protections**: <the specific defences, each matching a requirement ID above>

## Testing

- **Contract suite**: <the suite it provides (subsystem) or passes (implementation)>
- **Fuzz targets**: <the input surfaces with Jazzer targets>
- **Other**: <property tests, recorded fixtures, and anything special about running them>

```bash
./gradlew :kiln-<name>:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: <the specs this module implements, with section numbers where behaviour is subtle>
- **Dependencies**: <the libraries that do the hard part, and why each was chosen>
- **Reference projects**: <the open-source projects cross-checked for behaviour, tests and known bugs>

## Status

<What's implemented, and what's planned: each planned requirement with its milestone.>
````
