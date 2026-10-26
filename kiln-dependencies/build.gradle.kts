// The release-train BOM (DEP-1): every kiln-* module and every third-party version Kiln uses.
// Versions come from gradle/libs.versions.toml. Only the Spring Boot artifacts Kiln uses are
// managed, with the families Boot aligns (Framework, Micrometer, Jackson, JUnit) imported at the
// versions that Boot release declares.

plugins {
    `java-platform`
    id("kiln.publishing")
}

description = "Bulmer Kiln dependency management: the release-train BOM"

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform(libs.spring.framework.bom))
    api(platform(libs.micrometer.bom))
    api(platform(libs.jackson.bom))
    api(platform(libs.junit.bom))

    constraints {
        api(project(":kiln-auth"))
        api(project(":kiln-auth-oauth2"))
        api(project(":kiln-auth-oidc"))
        api(project(":kiln-core"))
        api(project(":kiln-format"))
        api(project(":kiln-format-cargo"))
        api(project(":kiln-format-maven"))
        api(project(":kiln-format-npm"))
        api(project(":kiln-format-oci"))
        api(project(":kiln-observability"))
        api(project(":kiln-permissions"))
        api(project(":kiln-scan"))
        api(project(":kiln-scan-gitleaks"))
        api(project(":kiln-scan-grype"))
        api(project(":kiln-scan-orchestration"))
        api(project(":kiln-scan-osv-scanner"))
        api(project(":kiln-scan-trivy"))
        api(project(":kiln-storage"))
        api(project(":kiln-storage-fs"))
        api(project(":kiln-storage-s3"))

        api(libs.spring.boot)
        api(libs.spring.boot.autoconfigure)
        api(libs.spring.boot.configuration.processor)
        api(libs.spring.boot.test)

        api(libs.archunit)
        api(libs.assertj.core)
        api(libs.byte.buddy)
        api(libs.checkstyle)
        api(libs.commons.logging)
        api(libs.error.prone.annotations)
        api(libs.error.prone.core)
        api(libs.findsecbugs)
        api(libs.jacoco.agent)
        api(libs.jacoco.ant)
        api(libs.pitest)
        api(libs.pitest.command.line)
        api(libs.pitest.entry)
        api(libs.pitest.junit5.plugin)
        api(libs.jazzer.junit)
        api(libs.jqwik)
        api(libs.jspecify)
        api(libs.nullaway)
        api(libs.pmd.ant)
        api(libs.pmd.java)
        api(libs.spotbugs)
        api(libs.spotbugs.annotations)
    }
}
