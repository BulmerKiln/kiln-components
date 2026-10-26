# kiln-scan-trivy

The Trivy scanner adapter, run by `kiln-scanner-service`.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-scan`](../kiln-scan) |
| Artifact | `com.bulmerkiln:kiln-scan-trivy` |
| Runs in | The scan worker only |
| Depends on | [`kiln-scan`](../kiln-scan) |
| Used by | `kiln-scanner-service` |
| Status | M4, Experimental |

## Purpose

Trivy is one of the two vulnerability engines matching each artifact's stored SBOM for OS and language-package vulnerabilities. Matching the SBOM instead of the blob means the content is read once.

## Responsibilities

**Does**

- Build Trivy's command line, offline, against the worker's local copy of the input
- Declare the Trivy versions it reads
- Turn Trivy's report into findings

**Does not**

- Run Trivy or download its database; the worker does, from Kiln's mirror
- Merge its findings with other engines'; [`kiln-scan-orchestration`](../kiln-scan-orchestration) does

## Requirements

Inherits every requirement of [`kiln-scan`](../kiln-scan#requirements), and adds:

| ID | Requirement | Verified by |
|---|---|---|
| SCAN-TRIVY-1 | Only report schema version 2 may be read; any other is a failed scan. A report without `Results`, or a result without `Vulnerabilities`, means nothing was found | `TrivyAdapterTest` |
| SCAN-TRIVY-2 | A finding must be named by Trivy's `VulnerabilityID`, with `VendorIDs` (such as GHSA IDs) as aliases, which is what lets it merge with other engines' | `TrivyAdapterTest` |
| SCAN-TRIVY-3 | Vulnerabilities a VEX statement marks `not_affected` must be skipped | `TrivyAdapterTest` |
| SCAN-TRIVY-4 | Only Trivy 0.76.x may be read | `TrivyAdapterTest` |
| SCAN-TRIVY-5 | Trivy must run offline: `--offline-scan --skip-db-update --skip-java-db-update` | `ScannerAdapterContract` (command checks), review |

## How it plugs in *(implementation only)*

Implements `ScannerAdapter` as `TrivyAdapter` (scanner ID `trivy`), and passes `ScannerAdapterContract`
in `TrivyAdapterTest` against real recordings of Trivy 0.76.1. The worker runs:

```
/usr/local/bin/trivy sbom --format json --quiet --offline-scan --skip-db-update --skip-java-db-update <sbom>
```

## Configuration

None: the worker provides the database and the input.

## Usage

```java
ScanResult result = ScannerAdapters.run(new TrivyAdapter(), job, toolRun);
```

## Security

- **Threats**: hostile content making Trivy crash or emit garbage; a tool upgrade changing the report
  so findings are misread
- **Untrusted input**: Trivy's report
- **Defaults**: an unsupported Trivy version, a crash or an unreadable report is never a clean pass
- **Protections**: the inherited SCAN-6 to SCAN-13, and the requirements above

## Testing

- **Contract suite**: passes `ScannerAdapterContract`
- **Fuzz targets**: none of its own
- **Other**: real recordings in [`src/test/resources`](src/test/resources/com/bulmerkiln/scan/trivy), with how they were captured in `RECORDINGS.md`

```bash
./gradlew :kiln-scan-trivy:test
```

No tests need Docker or external services.

## Standards and references

kiln-scan-trivy1

## Status

Implemented: every requirement above, against Trivy 0.76.1.

Planned: re-recording, and widening the supported versions, with each Trivy upgrade in `kiln-scanner-service`.
