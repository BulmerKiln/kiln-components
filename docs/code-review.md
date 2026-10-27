# Code review

How code in `kiln-components` is reviewed: on every pull request, and across the whole repository
at the end of each iteration. Reviewers, human or automated, follow the same rules and report
findings the same way.

A review checks the code against **its own requirements** (each module's README) and **these
rules**. It's not the place to redesign a feature. A design question raised in review becomes an
issue, not a blocker, unless the code is wrong without an answer.

## 1. Gates first

A review starts only when the automated gates pass. Reviewers don't spend time on anything a
machine already checks.

```bash
./gradlew build
```

That runs:

- **Compilation** with `-Werror`, Error Prone (including locale checks) and NullAway (BUILD-1, BUILD-2)
- **Formatting and license headers**, with unused imports removed (BUILD-4)
- **Checkstyle**: size, nesting, complexity, naming, and no TODO without an issue (BUILD-9)
- **PMD**: code smells (BUILD-10), Kiln's own input-handling and performance rules (BUILD-13), and
  test cleanup and hygiene: no sleeping, no network (BUILD-11)
- **SpotBugs with FindSecBugs**: bug patterns and security bug patterns (BUILD-12)
- **ArchUnit**: module dependencies, kernel and scan-worker isolation, where Spring may appear,
  naming by contract, and optional integrations only through conditional auto-configurations
  (ARCH-1 to ARCH-11)
- **Every test**: unit, property, fuzz regression inputs, every contract suite, and every module's
  requirement tests, with no Docker or external services
- **The fuzz check**: every parser of untrusted text or bytes has a fuzz target (BUILD-14)
- **Dependency analysis**: no unused or wrongly scoped dependency (BUILD-15)
- **The license check** (BUILD-16), and **locked, checksum-verified dependencies** (BUILD-17)
- **Coverage**: at least 80% of each module's lines and branches (BUILD-18)
- **The README check** (BUILD-8): every requirement ID cited in code exists, every test a README
  names exists, and no ID is used twice

CI adds what's too slow or too broad for every local build: CodeQL taint analysis, Semgrep's
community rules, and CVE scanning of the locked dependencies.

The gates' limits are the rules. A reviewer doesn't argue a method down to 40 lines when the limit
is 50, and code that needs an exception to a limit raises it as its own pull request. A failing
gate is fixed before review, never waived in review.

## 2. What to check, in order

Review in this order, and don't move on to style while a correctness or security question is
open. The gates catch what they can; this section is what they can't.

### 2.1 Correctness

- The code does what its module's requirements say, including the error paths.
- Edge cases are handled: empty, zero, maximum, boundary values, missing optional fields, and
  values exactly at a limit.
- Concurrency is safe: shared state, races between check and use, locks held across I/O, and
  what happens when two requests do the same thing at once.
- Resources are released on every path: streams, channels, locks, scoped values.
- Failures are reported, not swallowed. A caught exception either recovers correctly or becomes a
  clear error.

### 2.2 Security

*Automated*: SpotBugs and FindSecBugs, the fuzz check, the license check, verified dependencies, and in CI CodeQL and CVE scanning. *Reviewer*: whether a new surface needed a threat considered, and the scenarios the tools can't follow.

- **Every new input surface** has a fuzz target.
- **Fail closed**: a missing setting, port or listener stops the boot or refuses the request.
  Nothing is allowed by default.
- **Organization isolation**: work runs inside `OrganizationContext`, keys and paths include the
  organization, and nothing reveals another organization's data or existence.
- **No secret leaks**: not in logs, exceptions, `toString()`, metrics, findings or command lines.
  Errors never repeat a value they refuse.
- **Dependencies**: a new or upgraded dependency is justified, licensed as allowed, and added
  only through `kiln-dependencies`.

### 2.3 Input handling

*Automated*: anchored validation, no `URLDecoder`, no shell, no hand-built JSON, locale-safe case conversion (BUILD-13, BUILD-1), and the fuzz check. *Reviewer*: whether the canonical form is the right one, and refuse versus repair.

Anything from a client, an IdP, a scan worker, a tool's output or configuration is untrusted.

**Validate once, at the boundary, into a type**

- Untrusted text becomes a validated type where it enters (`Digest`, `RepositoryName`,
  `PackageName`, `UploadId`, `CrateName`, `ConnectorId`), and code past the boundary takes that
  type, never the raw string.
- Validation is an allowlist: an anchored grammar (`matches()`, never `find()`) with bounded
  quantifiers. Length is checked before the regex runs.
- Sizes, counts, lengths and nesting are bounded before anything is allocated or parsed.
- Parsing is strict wherever two parsers could read the input differently: duplicate keys,
  trailing data, type coercion, encodings other than UTF-8, byte-order marks.

**Normalize before you validate or compare**

- Normalize to one canonical form first, then validate and compare that form, and store it.
- Decode exactly once. Percent-encoding is decoded only where the protocol says so, only for the
  escapes it allows (`%2f` in npm scopes); any other escape, including double encoding, is refused.
- Case-fold or otherwise fold names only where the format says they're equivalent (crate names
  ignoring case and `-`/`_`, email domains), and compare the folded form.
- Paths are built from validated parts, normalized, and never left to resolve `..` at use.
- Security-relevant values compare exactly unless their standard says otherwise: an OIDC issuer
  is compared byte for byte, not after trimming a slash.
- **Refuse rather than repair.** Ambiguous input is an error, not something to clean up: no
  trimming of versions, no guessing an encoding, no accepting lookalike characters. Repair only
  text that's displayed and never identifies anything, such as a tool's finding title
  (`ToolText.clean`).

**Sanitize on the way out**

- Escape for the output's own context, at the point of output: JSON through `FormatJson`,
  headers through `FormatResponse`, command lines as argument lists (never a shell), and nothing
  with control characters into logs.
- Never build JSON, XML, headers or commands by concatenating strings.

### 2.4 Architecture

*Automated*: ArchUnit (ARCH-1 to ARCH-11). *Reviewer*: whether a change to the dependency table is the right design.

- The dependency table in `kiln-architecture` (ARCH-2) changes only when the design does. A new
  dependency between modules is a design decision: justify it in the pull request.
- `kiln-core` stays the kernel: no subsystem is added to it.
- A new subsystem defines its contract, its rules and a contract suite in its test fixtures.
- An implementation reaches other subsystems only through their ports.
- Optional integrations use a `compileOnly` dependency and `@ConditionalOnClass`, so each module
  still works alone.
- Rules that every implementation must follow are enforced centrally, by type where possible,
  rather than repeated in each implementation.

### 2.5 Performance and complexity

*Automated*: nesting and complexity limits (BUILD-9, BUILD-10), and likely-quadratic patterns: a `List` search, regex or string `+=` in a loop body, and regexes compiled outside constants (BUILD-13). *Reviewer*: the actual Big O, which no tool can prove.

Aim for constant time per item: work should grow with the input's size, not its square.

- **Know the Big O of every loop.** The gates cap nesting depth; the reviewer checks what the loops
  multiply. Two nested loops over inputs a client controls (manifest descriptors, aliases, groups,
  findings, headers) are O(n²) and need a different approach.
- **Look up, don't search.** Membership and matching use a `HashMap`, `HashSet`, `EnumSet` or
  `EnumMap`, not `List.contains` or a scan inside a loop. One pass over the input is the goal.
- **A quadratic step is allowed only over a small, fixed bound**, stated in a comment and enforced
  in code, such as the 64 upload lock stripes or the three levels of the blob directory tree.
- **Stream, don't buffer.** Blobs and uploads are never held in memory; reads use fixed buffers.
- **Do expensive work once.** Patterns are compiled once (`static final Pattern`), mappers and
  immutable values are reused, and nothing is recomputed inside a loop that could be computed
  before it.
- **No per-item I/O.** Batch database queries and storage calls instead of one per item.
- **Regexes run in linear time**: no nested or overlapping quantifiers that can backtrack, and
  length checked before matching. Hostile-input tests include pathological cases with a time limit.

### 2.6 Requirements and documentation

- A change in behaviour updates the module's README requirements in the same pull request: a new
  requirement gets a new ID, a changed one keeps its ID, and an ID is never reused.
- Every requirement names the tests that verify it, and those tests do verify it.
- Code cites requirement IDs, never central design documents.
- **Status** says what's true: nothing described as built that isn't.

### 2.7 Tests

*Automated*: contract suites, the fuzz check, no sleeping and no network (BUILD-11), the README check, and 80% line and branch coverage (BUILD-18). *Reviewer*: whether each test asserts the right outcome; coverage shows code ran, not that it was checked.

- Each new or changed requirement has a test that fails without the change.
- Tests check behaviour, not implementation details, and assert the exact outcome (status, error
  code, message), not just "an exception was thrown".
- A new implementation extends its subsystem's contract suite instead of copying its tests.
- Tests are deterministic: no sleeps to wait for something, no reliance on ordering that isn't
  guaranteed, no network. Scanner adapters use recorded tool output.
- Attack cases are covered: hostile, oversized, malformed, concurrent and, for parsers,
  pathological input with a time limit.

### 2.8 Consistency and naming

*Automated*: Checkstyle naming, and ArchUnit's naming by role and by contract (ARCH-6, ARCH-7, ARCH-10). *Reviewer*: the rest of the table below, and whether a name says what the thing is.

Where the code already has a pattern, new code follows it. A new pattern is introduced on purpose,
in its own pull request, with the old code moved to it.

| Thing | Pattern | Example |
|---|---|---|
| Modules and packages | `kiln-<subsystem>[-<implementation>]` in `com.bulmerkiln.<subsystem>[.<implementation>]` | `kiln-storage-fs`, `com.bulmerkiln.storage.fs` |
| Spring wiring | One `*AutoConfiguration` per module, registered in `AutoConfiguration.imports` | `FsStorageAutoConfiguration` |
| Settings | A `*Properties` record with `@Nullable` fields, validated into a domain type by a `from(...)` factory that names the setting at fault | `OidcProperties`, `OidcConnector.from` |
| Contract implementations | Named for the contract they implement | `OciFormatModule`, `TrivyAdapter`, `OidcConnector`, `FsBlobStore` |
| Their identity | A `public static final` constant | `OciFormatModule.FORMAT`, `TrivyAdapter.ID` |
| Value types | A record whose compact constructor validates, so an invalid value can't exist | `Digest`, `ConnectorId`, `ByteRange` |
| Factories | `parse(text)` from untrusted text; `of(...)` from parts; `from(properties)` from settings; `random()` for generated IDs | `Digest.parse`, `Refusal.of`, `UploadId.random` |
| Refusals | An unchecked `*Exception`, extending `RefusedException` when it reaches a client | `OciException`, `NpmException` |
| Error messages | A capitalized sentence without a final full stop, naming the setting or rule, never repeating the refused value | `Connector sso: issuer is required` |
| Contract suites | An abstract `*Contract` class in test fixtures, extended by a `*Test` per implementation | `BlobStoreContract`, `FsBlobStoreTest` |
| Test methods | The behaviour as a phrase | `refusesGapsAndOverlaps`, `leavesTheUploadUnchangedWhenAChunkIsTooLarge` |
| Requirement citations | The ID in parentheses | `(STO-3)` |

Names say what something is in the domain's terms (organization, member, connector, refusal),
using the glossary's words, not synonyms.

### 2.9 Cleanup

*Automated*: unused imports and members, unnecessary qualified names, TODOs without an issue, and unused or wrongly scoped dependencies (BUILD-4, BUILD-10, BUILD-11, BUILD-9, BUILD-15). *Reviewer*: leftovers a tool can't tell are obsolete.

- **Imports**: no unused, wildcard or fully qualified names in code (the gates catch most of this;
  check what's left, such as qualified names in tests).
- **Dead code**: no unused methods, fields, parameters, classes or test resources; no
  commented-out code; no debugging leftovers.
- **Build files**: no unused dependencies, and nothing declared at a wider scope than it needs
  (`implementation` over `api`, `compileOnly` for optional integrations).
- **Leftovers of the change itself**: helpers, branches, settings and docs the change made
  obsolete are removed in the same pull request.
- **TODOs** carry an issue number, or don't exist.

### 2.10 Simplicity and readability

- Code within the gates' limits can still be hard to follow; say so when it is.
- The change is as small as the problem allows. No speculative abstraction, option or extension
  point without a current use.
- It reads like the code around it: structure and comment density.
- Comments say why, not what.

## 3. Severity

| Severity | Means | Outcome |
|---|---|---|
| **Blocker** | A security hole, a correctness bug, a broken requirement, or a broken architecture rule | Fixed before merge. Only a maintainer can accept one, with the reason recorded in the pull request |
| **Major** | A real problem that's contained: a missing test, an unhandled edge case with limited impact, a misleading README | Fixed before merge, or tracked as an issue and fixed before the iteration closes |
| **Minor** | Worth fixing, low risk: unclear naming, a missing comment on a subtle rule, duplication | Fixed, or tracked at the author's choice |
| **Nit** | Preference | The author decides; never blocks |

When in doubt between two severities, choose the higher and say why.

## 4. Reporting a finding

Every finding is reported the same way, whoever or whatever found it:

```markdown
**<Severity>**: <one sentence stating the defect>
- **Where**: `<module>/src/.../File.java:<line>`
- **Requirement**: <ID it breaks, or "none">
- **Scenario**: <concrete input or sequence of events, and the wrong result it produces>
- **Fix**: <suggested change, if known>
```

- **A finding needs a scenario.** If the reviewer can't describe the input and the wrong result,
  it's a question, not a finding. Ask it as a question.
- **One finding per defect.** Several symptoms of one cause are one finding.
- **Findings from automated reviewers are verified before they're acted on.** Whoever acts on one
  confirms the scenario first, with a test where possible.

## 5. Resolving findings

1. The author either fixes the finding, or replies with why it's not a defect.
2. A fix comes with a test that fails without it, wherever that's possible.
3. The reviewer confirms the fix, or the reasoning, and resolves the finding.
4. A disagreement on a Blocker or Major goes to a maintainer, whose decision is recorded in the
   pull request.

## 6. Pull request review

1. **The author self-reviews** against section 2 before asking for review, and says in the pull
   request which requirements changed.
2. **The gates pass** (section 1).
3. **The reviewer reviews** in section 2's order and reports findings as in section 4.
4. **Findings are resolved** as in section 5.
5. **Merge** once no Blocker is open and every Major is fixed or tracked.

## 7. Iteration review

At the end of each iteration, the whole repository is reviewed, not just the latest changes.

1. **Run the gates** on a clean checkout, with the build cache off:
   `./gradlew clean build --no-build-cache`.
2. **Review module by module, in dependency order**: foundations (`kiln-build`,
   `kiln-dependencies`, `kiln-core`), then each subsystem, then its implementations. A
   subsystem's rules are understood before its implementations are judged against them.
3. **For each module, review its README first, then its code.** Check that every requirement is
   met and tested, that the code does nothing the README doesn't state, and that **Status** is
   accurate.
4. **Then review across modules**: dependency directions, duplicated rules that belong in a
   subsystem, and contract suites that every implementation runs.
5. **Record the review** in `docs/reviews/<YYYY-MM-DD>-<iteration>.md`: scope, the commit
   reviewed, every finding in section 4's format, and its outcome (fixed, tracked as an issue, or
   accepted with the reason).
6. **Fix the Blockers and Majors**, re-run the gates, and record the commit the iteration closed
   on.

## 8. Checklists by module kind

Use these with section 2; they cover what's specific to each kind.

**Foundation** (`kiln-build`, `kiln-dependencies`, `kiln-core`)

- Nothing subsystem-specific has crept in.
- A version change is justified, and a Spring Boot minor or major upgrade comes alone (DEP-4).

**Subsystem**

- The contract is small, and each type in it has a current use.
- Every rule all implementations share is enforced here, by type where possible.
- Ports have no allow-everything or silent default.
- The contract suite covers every subsystem requirement, and runs here against a minimal
  implementation.

**Implementation**

- It depends only on its subsystem and the kernel, plus any optional `compileOnly` integrations.
- It extends the contract suite, and adds tests only for its own requirements.
- Its README lists only the requirements it adds, and links to its subsystem's.
