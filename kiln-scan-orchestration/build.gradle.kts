plugins {
    id("kiln.java-library")
}

description = "Bulmer Kiln scan orchestration, run by Kiln's server: merging findings across scanners, and later dispatch, enrichment and policy"

dependencies {
    api(project(":kiln-scan"))

    // The real recordings, merged across scanners, prove deduplication works on actual output.
    testImplementation(project(":kiln-scan-grype"))
    testImplementation(project(":kiln-scan-osv-scanner"))
    testImplementation(project(":kiln-scan-trivy"))
}
