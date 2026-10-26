# Recordings

Real output, captured on 2026-10-26 with the official images, so the unit tests never need the
tool or Docker. Re-record when the bundled tool version changes, and add the version to the
adapter's supported range only once its recording passes the contract.

Inputs: a `package-lock.json` with `lodash@4.17.20` (vulnerable) or `is-number@7.0.0` (clean),
turned into a CycloneDX SBOM with `anchore/syft:1.55.0` (`syft dir:<project> -o cyclonedx-json`).

```
docker run aquasec/trivy:0.76.1 sbom --format json --quiet <sbom>
```
