pluginManagement {
    includeBuild("kiln-build")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kiln-components"

include(
    "kiln-dependencies",
    "kiln-architecture",
    "kiln-core",
    "kiln-crypto",
    "kiln-database",
    "kiln-auth",
    "kiln-auth-oidc",
    "kiln-auth-oauth2",
    "kiln-format",
    "kiln-format-cargo",
    "kiln-format-maven",
    "kiln-format-npm",
    "kiln-format-oci",
    "kiln-observability",
    "kiln-permissions",
    "kiln-registry",
    "kiln-scan",
    "kiln-scan-gitleaks",
    "kiln-scan-grype",
    "kiln-scan-orchestration",
    "kiln-scan-osv-scanner",
    "kiln-scan-trivy",
    "kiln-storage",
    "kiln-storage-fs",
    "kiln-storage-s3",
    "kiln-web",
)
