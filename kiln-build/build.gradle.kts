import java.util.Properties

plugins {
    `kotlin-dsl`
    `maven-publish`
    signing
}

// kiln-build is released with the train, so it shares the monorepo's group and version.
val train = Properties().apply { file("../gradle.properties").reader().use(::load) }
group = train.getProperty("group")
version = train.getProperty("version")
description = "Bulmer Kiln's Gradle convention plugins"

java {
    withSourcesJar()
    withJavadocJar()
}

// The same POM metadata and signing as kiln.publishing, which can't be applied to the build that
// defines it. Staged with the monorepo's publications for the release (BUILD-20).
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name = provider { artifactId }
            description = provider { project.description }
            url = "https://bulmerkiln.com"
            licenses {
                license {
                    name = "Apache-2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0"
                }
            }
            developers {
                developer {
                    name = "The Bulmer Kiln Authors"
                    url = "https://github.com/BulmerKiln"
                }
            }
            scm {
                url = "https://github.com/BulmerKiln/kiln-components"
                connection = "scm:git:https://github.com/BulmerKiln/kiln-components.git"
            }
        }
    }
    repositories {
        maven {
            name = "staging"
            url = uri(layout.projectDirectory.dir("../build/staging-deploy"))
        }
    }
}

val signingKey = providers.environmentVariable("KILN_SIGNING_KEY")
signing {
    isRequired = signingKey.isPresent
    if (signingKey.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), providers.environmentVariable("KILN_SIGNING_PASSWORD").orNull)
        sign(publishing.publications)
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

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
