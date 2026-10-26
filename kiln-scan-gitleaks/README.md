# kiln-scan-gitleaks

The Gitleaks scanner adapter, run by `kiln-scanner-service`.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-scan`](../kiln-scan) |
| Artifact | `com.bulmerkiln:kiln-scan-gitleaks` |
| Runs in | The scan worker only |
| Depends on | [`kiln-scan`](../kiln-scan) |
| Used by | `kiln-scanner-service` |
| Status | M4, Experimental |

## Purpose

Images and packages sometimes ship credentials by mistake. Gitleaks finds them in the unpacked content inside the sandbox. A finding says which rule matched; it must never carry the secret itself.

## Responsibilities

**Does**

- Build Gitleaks's command line, offline, against the worker's local copy of the input
- Declare the Gitleaks versions it reads
- Turn Gitleaks's report into findings

**Does not**

- Run Gitleaks or download its database; the worker does, from Kiln's mirror
- Merge its findings with other engines'; [`kiln-scan-orchestration`](../kiln-scan-orchestration) does

## Requirements

Inherits every requirement of [`kiln-scan`](../kiln-scan#requirements), and adds:

| ID | Requirement | Verified by |
|---|---|---|
| SCAN-GL-1 | Gitleaks must always run with `--redact`, and the adapter must read only the rule and its description, never `Match`, `Secret`, the surrounding text or the file path, so a secret can't reach a finding even if redaction were lost | `GitleaksAdapterTest`, `ScannerAdapterContract` |
| SCAN-GL-2 | Every leak must be reported as a secret finding at high severity, since Gitleaks has no severities of its own and a leaked credential is always serious | `GitleaksAdapterTest` |
| SCAN-GL-3 | Exit code 0 (nothing found) and 1 (leaks) must both mean the scan completed | `ScannerAdapterContract` |
| SCAN-GL-4 | Only Gitleaks 8.x from 8.30.0 may be read | `ScannerAdapterContract` |

## How it plugs in *(implementation only)*

Implements `ScannerAdapter` as `GitleaksAdapter` (scanner ID `gitleaks`), and passes `ScannerAdapterContract`
in `GitleaksAdapterTest` against real recordings of Gitleaks 8.30.1. The worker runs:

```
/usr/local/bin/gitleaks dir <content> --report-format json --report-path - --redact --no-banner --log-level error
```

## Configuration

None: the worker provides the database and the input.

## Usage

```java
ScanResult result = ScannerAdapters.run(new GitleaksAdapter(), job, toolRun);
```

## Security

- **Threats**: hostile content making Gitleaks crash or emit garbage; a tool upgrade changing the report
  so findings are misread; a secret's value copied into a finding
- **Untrusted input**: Gitleaks's report
- **Defaults**: an unsupported Gitleaks version, a crash or an unreadable report is never a clean pass
- **Protections**: the inherited SCAN-6 to SCAN-13, and the requirements above

## Testing

- **Contract suite**: passes `ScannerAdapterContract`
- **Fuzz targets**: none of its own
- **Other**: real recordings in [`src/test/resources`](src/test/resources/com/bulmerkiln/scan/gitleaks), with how they were captured in `RECORDINGS.md`

```bash
./gradlew :kiln-scan-gitleaks:test
```

No tests need Docker or external services.

## Standards and references

kiln-scan-gitleaks1

## Status

Implemented: every requirement above, against Gitleaks 8.30.1.

Planned: re-recording, and widening the supported versions, with each Gitleaks upgrade in `kiln-scanner-service`.
