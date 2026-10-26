// Every module applies its conventions from kiln-build. The root only runs the checks that span
// every module.

plugins {
    id("kiln.repository")
}

// Writes every publication, kiln-build's plugins included, to build/staging-deploy, which the
// release workflow bundles for the Central Portal (BUILD-20). Releases run from a fresh checkout,
// so the directory holds only this version.
tasks.register("stageRelease") {
    description = "Stages every publication in build/staging-deploy for a release."
    group = "publishing"
    dependsOn(gradle.includedBuild("kiln-build").task(":publishAllPublicationsToStagingRepository"))
    dependsOn(subprojects.map { "${it.path}:publishAllPublicationsToStagingRepository" })
}
