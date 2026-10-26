# kiln-scan-osv-scanner

The OSV-Scanner scanner adapter, run by `kiln-scanner-service`.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-scan`](../kiln-scan) |
| Artifact | `com.bulmerkiln:kiln-scan-osv-scanner` |
| Runs in | The scan worker only |
| Depends on | [`kiln-scan`](../kiln-scan) |
| Used by | `kiln-scanner-service` |
| Status | M4, Experimental |

## Purpose

OSV-Scanner matches language packages against the OSV database, which aggregates GitHub advisories and the ecosystems' own feeds. The OSV database also carries OpenSSF's malicious-packages feed, so this adapter is how known-malicious packages are found: no separate adapter is needed for them.

## Responsibilities

**Does**

- Build OSV-Scanner's command line, offline, against the worker's local copy of the input
- Declare the OSV-Scanner versions it reads
- Turn OSV-Scanner's report into findings

**Does not**

- Run OSV-Scanner or download its database; the worker does, from Kiln's mirror
- Merge its findings with other engines'; [`kiln-scan-orchestration`](../kiln-scan-orchestration) does

## Requirements

Inherits every requirement of [`kiln-scan`](../kiln-scan#requirements), and adds:

| ID | Requirement | Verified by |
|---|---|---|
| SCAN-OSV-1 | Exit code 0 (nothing found) and 1 (findings) must both mean the scan completed | `OsvScannerAdapterTest` |
| SCAN-OSV-2 | Severity must come from the CVSS score OSV-Scanner computes for each group of aliases (`max_severity`), else the advisory's own severity | `OsvScannerAdapterTest` |
| SCAN-OSV-3 | Entries from the malicious-packages feed (`MAL-`) must be reported as malware, at critical severity | `OsvScannerAdapterTest` |
| SCAN-OSV-4 | A finding must keep the advisory's aliases, and the first fixed version in its affected ranges | `OsvScannerAdapterTest` |
| SCAN-OSV-5 | Only OSV-Scanner 2.x from 2.7.0 may be read | `ScannerAdapterContract` |
| SCAN-OSV-6 | OSV-Scanner must run with `--offline`, against the mirrored database | `ScannerAdapterContract` (command checks), review |

## How it plugs in *(implementation only)*

Implements `ScannerAdapter` as `OsvScannerAdapter` (scanner ID `osv-scanner`), and passes `ScannerAdapterContract`
in `OsvScannerAdapterTest` against real recordings of OSV-Scanner 2.7.0. The worker runs:

```
/usr/local/bin/osv-scanner scan source -L <sbom> --format json --offline
```

## Configuration

None: the worker provides the database and the input.

## Usage

```java
ScanResult result = ScannerAdapters.run(new OsvScannerAdapter(), job, toolRun);
```

## Security

- **Threats**: hostile content making OSV-Scanner crash or emit garbage; a tool upgrade changing the report
  so findings are misread
- **Untrusted input**: OSV-Scanner's report
- **Defaults**: an unsupported OSV-Scanner version, a crash or an unreadable report is never a clean pass
- **Protections**: the inherited SCAN-6 to SCAN-13, and the requirements above

## Testing

- **Contract suite**: passes `ScannerAdapterContract`
- **Fuzz targets**: none of its own
- **Other**: real recordings in [`src/test/resources`](src/test/resources/com/bulmerkiln/scan/osv), with how they were captured in `RECORDINGS.md`

```bash
./gradlew :kiln-scan-osv-scanner:test
```

No tests need Docker or external services.

## Standards and references

kiln-scan-osv-scanner1

## Status

Implemented: every requirement above, against OSV-Scanner 2.7.0.

Planned: re-recording, and widening the supported versions, with each OSV-Scanner upgrade in `kiln-scanner-service`.
