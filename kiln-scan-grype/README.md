# kiln-scan-grype

The Grype scanner adapter, run by `kiln-scanner-service`.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-scan`](../kiln-scan) |
| Artifact | `com.bulmerkiln:kiln-scan-grype` |
| Runs in | The scan worker only |
| Depends on | [`kiln-scan`](../kiln-scan) |
| Used by | `kiln-scanner-service` |
| Status | M4, Experimental |

## Purpose

Grype is the second vulnerability engine matching each artifact's stored SBOM, with a different database from Trivy's, so each covers the other's blind spots.

## Responsibilities

**Does**

- Build Grype's command line, offline, against the worker's local copy of the input
- Declare the Grype versions it reads
- Turn Grype's report into findings

**Does not**

- Run Grype or download its database; the worker does, from Kiln's mirror
- Merge its findings with other engines'; [`kiln-scan-orchestration`](../kiln-scan-orchestration) does

## Requirements

Inherits every requirement of [`kiln-scan`](../kiln-scan#requirements), and adds:

| ID | Requirement | Verified by |
|---|---|---|
| SCAN-GRYPE-1 | Only a report whose `descriptor.name` is `grype` may be read, and it must hold a `matches` list | `GrypeAdapterTest` |
| SCAN-GRYPE-2 | A finding must be named by Grype's vulnerability ID, with `relatedVulnerabilities` (such as the CVE for a GHSA) as aliases | `GrypeAdapterTest` |
| SCAN-GRYPE-3 | A fixed version must be reported only when Grype's fix state is `fixed` | `GrypeAdapterTest` |
| SCAN-GRYPE-4 | Only Grype 0.121.x may be read | `ScannerAdapterContract` |
| SCAN-GRYPE-5 | Grype must run without updating its database; the worker sets `GRYPE_DB_AUTO_UPDATE=false` and points it at the mirror | Review |

## How it plugs in *(implementation only)*

Implements `ScannerAdapter` as `GrypeAdapter` (scanner ID `grype`), and passes `ScannerAdapterContract`
in `GrypeAdapterTest` against real recordings of Grype 0.121.0. The worker runs:

```
GRYPE_DB_AUTO_UPDATE=false /usr/local/bin/grype sbom:<sbom> -o json -q
```

## Configuration

None: the worker provides the database and the input.

## Usage

```java
ScanResult result = ScannerAdapters.run(new GrypeAdapter(), job, toolRun);
```

## Security

- **Threats**: hostile content making Grype crash or emit garbage; a tool upgrade changing the report
  so findings are misread
- **Untrusted input**: Grype's report
- **Defaults**: an unsupported Grype version, a crash or an unreadable report is never a clean pass
- **Protections**: the inherited SCAN-6 to SCAN-13, and the requirements above

## Testing

- **Contract suite**: passes `ScannerAdapterContract`
- **Fuzz targets**: none of its own
- **Other**: real recordings in [`src/test/resources`](src/test/resources/com/bulmerkiln/scan/grype), with how they were captured in `RECORDINGS.md`

```bash
./gradlew :kiln-scan-grype:test
```

No tests need Docker or external services.

## Standards and references

kiln-scan-grype1

## Status

Implemented: every requirement above, against Grype 0.121.0.

Planned: re-recording, and widening the supported versions, with each Grype upgrade in `kiln-scanner-service`.
