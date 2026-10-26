# kiln-scan-orchestration

Server-side scanning: merging and deduplicating findings across scanners, and later dispatch,
enrichment and policy.

## At a glance

| | |
|---|---|
| Kind | Implementation of [`kiln-scan`](../kiln-scan) (its server side) |
| Artifact | `com.bulmerkiln:kiln-scan-orchestration` |
| Runs in | Kiln's server only, never the worker |
| Depends on | [`kiln-scan`](../kiln-scan) |
| Used by | `kiln-oss-service`, `kiln-cloud-service` |
| Status | M4, Experimental: merging and counts |

## Purpose

Several engines scan each artifact, and they name the same issue differently: Trivy by CVE with
the GHSA as an alias, Grype and OSV-Scanner the other way round. This module merges their results
into one finding per issue, keeps findings only one engine reported, and shows the disagreement
rather than hiding it.

## Responsibilities

**Does**

- Merge one job's results into one list (`FindingMerger`, `MergedFinding`)
- Count findings by severity (`SeverityCounts`)

**Does not**

- Run on the worker
- Parse tool output; the adapters do

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| SCAN-ORCH-1 | Findings must merge when they're of the same kind, affect the same package, and share any identifier, directly or through other findings' aliases. The package is its Package URL without qualifiers or subpath, since engines write those differently for one package | `FindingMergerTest`, `RecordedScansTest` |
| SCAN-ORCH-2 | Different versions of a package, and different kinds of finding, must never merge | `FindingMergerTest` |
| SCAN-ORCH-3 | A merged finding must be named by its CVE, else its GHSA, else its malware (`MAL-`) entry, keeping every other identifier as an alias | `FindingMergerTest`, `RecordedScansTest` |
| SCAN-ORCH-4 | A merged finding must take the highest severity any engine reported | `FindingMergerTest` |
| SCAN-ORCH-5 | A finding must record which engines reported it and how many ran, so "reported by 1 of 3" is shown, never dropped. Failed scans don't count as run; incomplete ones contribute their findings | `FindingMergerTest` |
| SCAN-ORCH-6 | Output must be deterministic: ordered by severity then identifier, with the fixed version and title taken from the first reporter by scanner ID | `FindingMergerTest` |
| SCAN-ORCH-7 | Results from different jobs must never be merged | `FindingMergerTest` |
| SCAN-ORCH-8 | Severity counts must include every severity level, zero or not | `FindingMergerTest` |

## How it plugs in *(implementation only)*

The server side of [`kiln-scan`](../kiln-scan): it consumes `ScanResult`s from the worker.
`RecordedScansTest` merges real Trivy, Grype and OSV-Scanner recordings of one SBOM, and gets five
findings, each reported by all three engines.

## Configuration

None.

## Usage

```java
List<MergedFinding> findings = FindingMerger.merge(resultsForOneJob);
SeverityCounts counts = SeverityCounts.of(findings);
```

## Security

- **Threats**: a finding silently dropped because engines disagree or name it differently
- **Untrusted input**: worker results, already validated by `kiln-scan` (SCAN-3)
- **Defaults**: a finding from any engine is kept (SCAN-ORCH-5)
- **Protections**: SCAN-ORCH-1, SCAN-ORCH-5, SCAN-ORCH-7

## Testing

- **Contract suite**: none of its own
- **Fuzz targets**: none
- **Other**: real recordings of the three vulnerability scanners under `src/test/resources`

```bash
./gradlew :kiln-scan-orchestration:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: the [OSV schema](https://ossf.github.io/osv-schema/) (aliases); [Package URL](https://github.com/package-url/purl-spec)
- **Dependencies**: none beyond `kiln-scan`
- **Reference projects**: Harbor's scan result aggregation, DefectDojo's deduplication

## Status

Implemented: SCAN-ORCH-1 to SCAN-ORCH-8.

Planned (M4): queuing jobs per organization and digest, presigned URLs for the worker, storing
results per organization, EPSS and CISA KEV enrichment, VEX exceptions, and the outputs to the
web app, metrics, notifications and the opt-in blocking policy.
