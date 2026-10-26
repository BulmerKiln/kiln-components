plugins {
    id("kiln.java-library")
}

description = "Bulmer Kiln scanner adapter for Gitleaks, run by kiln-scanner-service"

dependencies {
    api(project(":kiln-scan"))

    testImplementation(testFixtures(project(":kiln-scan")))
}
