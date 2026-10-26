// Applied at the root of kiln-components: `check` (and so `build`) fails when the module READMEs
// and the code disagree. See docs/code-review.md, section 1.

import com.bulmerkiln.build.ReadmeCheck

plugins {
    base
}

val readmeCheck = tasks.register<ReadmeCheck>("readmeCheck") {
    description = "Checks that every module's README matches its code."
    group = "verification"
    root = layout.projectDirectory
    modules.from(fileTree(rootDir) { include("kiln-*/build.gradle.kts") })
    readmes.from(fileTree(rootDir) { include("kiln-*/README.md") })
    sources.from(fileTree(rootDir) {
        include("kiln-*/src/**/*.java", "kiln-*/**/*.gradle.kts", "kiln-build/src/**/*.kt", "gradle/libs.versions.toml")
        exclude("**/build/**")
    })
    report = layout.buildDirectory.file("readme-check.txt")
}

tasks.named("check") {
    dependsOn(readmeCheck)
}
