# kiln-scan

The scan subsystem's contract, shared by Kiln's server and the sandboxed scan worker: the job and
result protocol, the contract every scanner adapter implements, and the rules every adapter runs
under.

## At a glance

| | |
|---|---|
| Kind | Subsystem |
| Artifact | `com.bulmerkiln:kiln-scan` (plus a `test-fixtures` variant) |
| Runs in | Kiln's server and the scan worker (`kiln-scanner-service`) |
| Depends on | [`kiln-core`](../kiln-core), Jackson |
| Used by | [`kiln-scan-orchestration`](../kiln-scan-orchestration) and the `kiln-scan-<tool>` adapters |
| Status | M4, Experimental |

## Purpose

Kiln layers several open-source scanners rather than trusting one, and runs them in a sandboxed
worker because they open hostile content. The worker and the server need one shared language, and
every tool's adapter must handle broken output, limits and secrets the same way. That's this
module. It holds no server code, so the worker carries nothing it doesn't need.

## Responsibilities

**Does**

- The protocol: `ScanJob`, `ScanResult`, `Finding`, `Limits` and the `ScanProtocol` codec
- The adapter contract: `ScannerAdapter`, `ToolRun`, and `ScannerAdapters.run` with its rules
- Shared helpers every adapter uses: `ToolOutput`, `ToolText`, `ToolVersion`, `Severities`
- `ScannerAdapterContract`, the suite every adapter passes

**Does not**

- Run tools or fetch blobs: the worker does
- Merge, enrich or act on findings: [`kiln-scan-orchestration`](../kiln-scan-orchestration) does, in the server

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| SCAN-1 | Messages must carry protocol version 1; any other version is refused as unsupported. Unknown fields are ignored, so adding an optional field needs no version change | `ScanProtocolTest` |
| SCAN-2 | Messages must be strict JSON: no duplicate keys, nothing after the value, no type coercion; and over 64 MiB is refused before parsing | `ScanProtocolTest` (fuzz target) |
| SCAN-3 | Results must be treated as untrusted, since the worker opens hostile content and may be compromised. Identifiers match `[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}`; text is printable and bounded (titles 512, versions 255); a package is a Package URL of at most 2,048 characters; at most 50 aliases and 100,000 findings | `ScanProtocolTest` |
| SCAN-4 | An `INCOMPLETE` result must say why; a `FAILED` result has no findings | `ScanProtocolTest` |
| SCAN-5 | Scanner IDs must be open values (`[a-z][a-z0-9-]{0,31}`) that travel as plain strings, so a new tool needs no protocol change | `ScanProtocolTest` |
| SCAN-6 | A tool that didn't complete, or output its adapter can't read, must give `FAILED` with no findings. A run must never pass from partial data, and parsing problems never throw out of `ScannerAdapters.run` | `ScannerAdapterContract` |
| SCAN-7 | A timeout or an archive limit must give `INCOMPLETE`, keeping what was found. It's never counted as a clean pass | `ScannerAdapterContract` |
| SCAN-8 | More findings than the protocol allows must give `FAILED`, never a truncated pass | `ExampleAdapterContractTest` |
| SCAN-9 | Tool-reported versions and text must be cleaned of control characters and bounded, so they can't forge log lines or break a result | `ScannerAdapterContract`, `ToolHelpersTest` |
| SCAN-10 | Every adapter must declare the tool versions it reads, and must refuse anything that isn't an exact version. For `0.x` tools, whose minor releases may change their output, that's one minor version per recording | `ScannerAdapterContract`, `ToolHelpersTest` |
| SCAN-11 | A clean scan must be recognised as complete with no findings, distinct from a broken one | `ScannerAdapterContract` |
| SCAN-12 | No result may contain a secret's value, even when the tool reports it | `ScannerAdapterContract` |
| SCAN-13 | Tools must run directly, never through a shell, on a local copy of the artifact; the presigned blob URL never appears on a command line | `ScannerAdapterContract` |
| SCAN-14 | Severities must map the same way for every tool: `critical`, `high`, `medium` or `moderate`, `low` or `negligible`, else unknown; CVSS scores by the v3 bands (9.0 critical, 7.0 high, 4.0 medium, above 0 low) | `ToolHelpersTest` |
| SCAN-15 | Tool reports must be parsed strictly by one shared parser, with Jackson's nesting and size limits | `ToolHelpersTest` |

## Contract *(subsystem only)*

| Type | Role |
|---|---|
| `ScannerAdapter` | One tool: its ID, supported versions, accepted exit codes, command line and report parser |
| `ToolRun` | What running the tool produced: versions, exit code, output, timeout or limit hit |
| `ScannerAdapters` | `run(adapter, job, run)`: applies SCAN-6 to SCAN-9 to any adapter |
| `ScanJob`, `ScanResult`, `Finding`, `Limits`, `ScannerId`, `Severity` | The protocol |
| `ScanProtocol` | Reads and writes messages |
| `ToolOutput`, `ToolText`, `ToolVersion`, `Severities` | Shared parsing and mapping |
| `ScannerAdapterContract` *(test fixtures)* | The suite every adapter extends |

**Rules enforced here**: no clean pass from a failed, partial or oversized run (SCAN-6 to
SCAN-8); cleaned tool text (SCAN-9); one severity mapping (SCAN-14).

## Adding an implementation *(subsystem only)*

1. Create `kiln-scan-<tool>` and depend on `kiln-scan`.
2. Implement `ScannerAdapter`. Run the tool offline against the worker's mirrored databases, read
   only the report fields you need, and never copy a secret's value.
3. Capture real output with the tool's official image (a report with findings, and a clean one),
   save it under `src/test/resources`, and describe how in a `RECORDINGS.md` next to it.
4. Extend `ScannerAdapterContract` with those recordings. The tests then need neither the tool nor Docker.
5. Add the module to `kiln-scanner-service`, which bundles the tool at a supported version.

## Configuration

None: adapters are configured by the worker.

## Usage

```java
ScanResult result = ScannerAdapters.run(adapter, job, toolRun);
byte[] message = ScanProtocol.write(result);
```

## Security

- **Threats**: a compromised worker sending hostile results (forged log lines, markup, unbounded
  data); hostile content making a tool crash or emit garbage, so a broken scan reads as clean;
  secrets copied into findings; presigned URLs leaked through command lines
- **Untrusted input**: every message from the worker; every tool's output
- **Defaults**: anything a scan can't vouch for is `FAILED` or `INCOMPLETE`, never `COMPLETE`
- **Protections**: SCAN-2, SCAN-3, SCAN-6 to SCAN-9, SCAN-12, SCAN-13

## Testing

- **Contract suite**: `ScannerAdapterContract` (test fixtures), run here against a minimal adapter
- **Fuzz targets**: result messages (`ScanProtocolTest`)
- **Other**: none

```bash
./gradlew :kiln-scan:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: the [OSV schema](https://ossf.github.io/osv-schema/); [Package URL](https://github.com/package-url/purl-spec); [CVSS v3.1 rating bands](https://www.first.org/cvss/v3.1/specification-document#Qualitative-Severity-Rating-Scale)
- **Dependencies**: Jackson
- **Reference projects**: Harbor's pluggable scanner API, Anchore's Syft and Grype pipeline

## Status

Implemented: SCAN-1 to SCAN-15.

Planned (M4): an SBOM-generator contract for Syft, which produces SBOMs rather than findings, and
the protocol message for it; an optional location on findings, so the same rule found in two
files stays two findings.
