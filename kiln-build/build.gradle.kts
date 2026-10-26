import java.util.Properties

plugins {
    `kotlin-dsl`
}

// kiln-build is released with the train, so it shares the monorepo's group and version.
val train = Properties().apply { file("../gradle.properties").reader().use(::load) }
group = train.getProperty("group")
version = train.getProperty("version")

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.10.4")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.1")
    implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:6.5.13")
    implementation("com.autonomousapps:dependency-analysis-gradle-plugin:3.19.2")
    implementation("com.github.jk1:gradle-license-report:3.1.4")
    implementation("info.solidsoft.gradle.pitest:gradle-pitest-plugin:1.19.0")
}

// Lets the conventions find kiln-dependencies at the same version when applied outside the monorepo,
// and gives them the versions of tools that can't take theirs from it.
val catalog = file("../gradle/libs.versions.toml").readText()
fun catalogVersion(name: String) =
    Regex("""(?m)^$name = "([^"]+)"""").find(catalog)?.groupValues?.get(1)
        ?: error("$name is missing from gradle/libs.versions.toml")

val generateBuildInfo = tasks.register("generateBuildInfo") {
    val output = layout.buildDirectory.dir("generated/kiln-build-info")
    val trainVersion = version.toString()
    val findsecbugs = catalogVersion("findsecbugs")
    val jacoco = catalogVersion("jacoco")
    val pitest = catalogVersion("pitest")
    val pitestJunit5 = catalogVersion("pitest-junit5")
    inputs.property("version", trainVersion)
    inputs.property("findsecbugs", findsecbugs)
    inputs.property("jacoco", jacoco)
    inputs.property("pitest", pitest)
    inputs.property("pitestJunit5", pitestJunit5)
    outputs.dir(output)
    doLast {
        val file = output.get().file("com/bulmerkiln/build/KilnBuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.bulmerkiln.build
            |
            |internal object KilnBuildInfo {
            |    const val VERSION = "$trainVersion"
            |
            |    /** spotbugsPlugins is non-transitive, so it can't take its version from kiln-dependencies. */
            |    const val FINDSECBUGS = "$findsecbugs"
            |    const val JACOCO = "$jacoco"
            |    const val PITEST = "$pitest"
            |    const val PITEST_JUNIT5 = "$pitestJunit5"
            |}
            |""".trimMargin(),
        )
    }
}

kotlin.sourceSets.main {
    kotlin.srcDir(generateBuildInfo)
}
