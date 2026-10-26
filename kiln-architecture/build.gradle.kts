plugins {
    id("kiln.java-library")
}

description = "Bulmer Kiln architecture rules, checked across every module with ArchUnit. Test only; never published"

// Not an artifact: it only holds the rules every other module is checked against.
tasks.matching { it.name.startsWith("publish") }.configureEach {
    enabled = false
}

dependencies {
    testImplementation(project(":kiln-core"))
    testImplementation(project(":kiln-auth"))
    testImplementation(project(":kiln-auth-oidc"))
    testImplementation(project(":kiln-auth-oauth2"))
    testImplementation(project(":kiln-format"))
    testImplementation(project(":kiln-format-cargo"))
    testImplementation(project(":kiln-format-maven"))
    testImplementation(project(":kiln-format-npm"))
    testImplementation(project(":kiln-format-oci"))
    testImplementation(project(":kiln-observability"))
    testImplementation(project(":kiln-permissions"))
    testImplementation(project(":kiln-scan"))
    testImplementation(project(":kiln-scan-gitleaks"))
    testImplementation(project(":kiln-scan-grype"))
    testImplementation(project(":kiln-scan-orchestration"))
    testImplementation(project(":kiln-scan-osv-scanner"))
    testImplementation(project(":kiln-scan-trivy"))
    testImplementation(project(":kiln-storage"))
    testImplementation(project(":kiln-storage-fs"))
    testImplementation(project(":kiln-storage-s3"))
    testImplementation("com.tngtech.archunit:archunit")
}
