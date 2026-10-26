// The convention every Kiln Java module applies: the Java 25 toolchain, kiln-dependencies for
// every version, Error Prone with NullAway, formatting with the SPDX header, and the test stack
// (JUnit, AssertJ, jqwik, Jazzer).

import com.bulmerkiln.build.FuzzCheck
import com.bulmerkiln.build.KilnBuildInfo
import com.bulmerkiln.build.PitestLimit
import info.solidsoft.gradle.pitest.PitestTask
import net.ltgt.gradle.errorprone.CheckSeverity
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    checkstyle
    pmd
    jacoco
    id("com.github.spotbugs")
    id("info.solidsoft.pitest")
    id("com.autonomousapps.dependency-analysis")
    id("com.github.jk1.dependency-license-report")
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
    id("kiln.publishing")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
    withJavadocJar()
}

// Versions come only from kiln-dependencies (DEP-1). Inside kiln-components it's the local
// project; anywhere else it's the release with the same version as these conventions.
val kilnManagement = configurations.dependencyScope("kilnManagement")
configurations.configureEach {
    if (isCanBeResolved && name != kilnManagement.name) {
        extendsFrom(kilnManagement.get())
    }
}

dependencies {
    val bom = rootProject.findProject(":kiln-dependencies")?.let { project(it.path) }
        ?: create("com.bulmerkiln:kiln-dependencies:${KilnBuildInfo.VERSION}")
    kilnManagement(platform(bom))

    api("org.jspecify:jspecify")
    implementation("com.google.errorprone:error_prone_annotations")

    errorprone("com.google.errorprone:error_prone_core")
    errorprone("com.uber.nullaway:nullaway")

    checkstyle("com.puppycrawl.tools:checkstyle")
    pmd("net.sourceforge.pmd:pmd-java")
    pmd("net.sourceforge.pmd:pmd-ant")
    spotbugs("com.github.spotbugs:spotbugs")
    // Non-transitive, so platform constraints don't reach it; the version comes from the same catalog.
    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:${KilnBuildInfo.FINDSECBUGS}")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("net.jqwik:jqwik")
    testImplementation("com.code-intelligence:jazzer-junit")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        check("NullAway", if (name.contains("test", ignoreCase = true)) CheckSeverity.OFF else CheckSeverity.ERROR)
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
        // Case conversion and formatting depend on the JVM's locale unless one is given.
        check("StringCaseLocaleUsage", CheckSeverity.ERROR)
        check("DefaultLocale", CheckSeverity.ERROR)
    }
}

// Structure (BUILD-9) and code smells (BUILD-10). The rules ship inside kiln-build, so every
// repository applying it gets the same ones; any violation fails the build.
fun kilnRules(name: String) = resources.text.fromString(
    object {}.javaClass.getResource("/kiln/$name")!!.readText())

checkstyle {
    config = kilnRules("checkstyle.xml")
    maxWarnings = 0
    isIgnoreFailures = false
}

pmd {
    ruleSets = emptyList()
    ruleSetConfig = kilnRules("pmd.xml")
    isConsoleOutput = true
    isIgnoreFailures = false
}

// Tests are held to Checkstyle's nesting and complexity limits, and to PMD's cleanup rules only.
tasks.withType<Pmd>().matching { it.name == "pmdTest" }.configureEach {
    ruleSetConfig = kilnRules("pmd-test.xml")
}

// Bug patterns, and security bug patterns with FindSecBugs (BUILD-12). Production code only.
spotbugs {
    effort = Effort.MAX
    reportLevel = Confidence.MEDIUM
    excludeFilter = layout.buildDirectory.file("kiln/spotbugs-exclude.xml")
}
val spotbugsExclude = tasks.register("kilnSpotbugsExclude") {
    val target = layout.buildDirectory.file("kiln/spotbugs-exclude.xml")
    val content = object {}.javaClass.getResource("/kiln/spotbugs-exclude.xml")!!.readText()
    outputs.file(target)
    doLast { target.get().asFile.apply { parentFile.mkdirs() }.writeText(content) }
}
tasks.withType<SpotBugsTask>().configureEach {
    dependsOn(spotbugsExclude)
    enabled = name == "spotbugsMain"
    reports.create("html") { required = true }
    reports.create("xml") { required = true }
}

// Runtime dependencies use only allowed licenses (BUILD-16): Apache-2.0, MIT, BSD, ISC and
// MPL-2.0. GPL, LGPL and AGPL are never allowed.
val allowedLicenses = layout.buildDirectory.file("kiln/allowed-licenses.json")
val writeAllowedLicenses = tasks.register("kilnAllowedLicenses") {
    val target = layout.buildDirectory.file("kiln/allowed-licenses.json")
    val content = object {}.javaClass.getResource("/kiln/allowed-licenses.json")!!.readText()
    outputs.file(target)
    doLast { target.get().asFile.apply { parentFile.mkdirs() }.writeText(content) }
}
licenseReport {
    configurations = arrayOf("runtimeClasspath")
    allowedLicensesFile = allowedLicenses.get().asFile
    filters = arrayOf(LicenseBundleNormalizer())
    // A BOM is a list of versions, not code, so nothing under its license ships; Kiln's own modules
    // are Apache-2.0 by definition.
    excludeBoms = true
    excludeOwnGroup = true
}
tasks.matching { it.name == "checkLicense" || it.name == "generateLicenseReport" }.configureEach {
    dependsOn(writeAllowedLicenses)
}
tasks.named("check") {
    dependsOn("checkLicense")
}

// Tests cover at least 80% of each module's lines and branches (BUILD-18). Contract suites in
// test fixtures count toward the module that runs them.
jacoco {
    toolVersion = KilnBuildInfo.JACOCO
}
tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required = true
        html.required = true
    }
}
val coverage = tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}
tasks.named("check") {
    dependsOn(coverage, "jacocoTestReport")
}

// Mutation testing (BUILD-19): PIT changes the code and expects a test to fail.
pitest {
    pitestVersion = KilnBuildInfo.PITEST
    junit5PluginVersion = KilnBuildInfo.PITEST_JUNIT5
    targetClasses = setOf("com.bulmerkiln.*")
    // One JVM per thread, each with a bounded heap: at most 4 per module and 3 modules at a time,
    // so 12 GB at most.
    threads = (Runtime.getRuntime().availableProcessors() / 8).coerceIn(1, 4)
    jvmArgs = listOf("-Xmx1g", "--enable-native-access=ALL-UNNAMED", "-XX:+EnableDynamicAgentLoading")
    mainProcessJvmArgs = listOf("-Xmx1g")
    outputFormats = setOf("HTML", "XML")
    timestampedReports = false
    failWhenNoMutations = false
}
val pitestLimit = gradle.sharedServices.registerIfAbsent("kilnPitest", PitestLimit::class) {
    maxParallelUsages = 3
}
tasks.withType<PitestTask>().configureEach {
    usesService(pitestLimit)
    // A module whose classes, tests and dependencies haven't changed reuses its last result.
    outputs.cacheIf { true }
}

// Every resolved version is locked (BUILD-17), so builds are repeatable and CI scans exactly what
// ships. After changing a version: ./gradlew resolveAndLockAll --write-locks
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}
tasks.register("resolveAndLockAll") {
    description = "Resolves every configuration, to write their lock state with --write-locks."
    notCompatibleWithConfigurationCache("Resolves configurations when it runs")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) { "Run with --write-locks" }
    }
    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}

// Every parser of untrusted text or bytes has a fuzz target (BUILD-14).
val fuzzCheck = tasks.register<FuzzCheck>("fuzzCheck") {
    description = "Checks that every parser has a fuzz target."
    group = "verification"
    mainSources.from(fileTree("src/main/java"))
    testSources.from(fileTree("src/test/java"))
    report = layout.buildDirectory.file("kiln/fuzz-check.txt")
}
tasks.named("check") {
    dependsOn(fuzzCheck)
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Jazzer loads a native library and attaches its agent at runtime, which Java 25 warns about
    // and a future release will block.
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-XX:+EnableDynamicAgentLoading")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

spotless {
    java {
        palantirJavaFormat("2.102.0")
        licenseHeader(
            """
            /*
             * Copyright The Bulmer Kiln Authors
             * SPDX-License-Identifier: Apache-2.0
             */
            """.trimIndent() + "\n",
        )
    }
}
