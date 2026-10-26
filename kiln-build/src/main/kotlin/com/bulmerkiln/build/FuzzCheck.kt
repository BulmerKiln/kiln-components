package com.bulmerkiln.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails when a parser of untrusted text or bytes has no fuzz target: every static `parse` or
 * `fromPath` method taking a `String` or `byte[]` must be called from a test with a `@FuzzTest`.
 */
abstract class FuzzCheck : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainSources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSources: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val parser = Regex("""\bstatic\s+[\w<>\[\],?\s]+?\s(parse|fromPath)\(\s*(?:final\s+)?(?:String|byte\s*\[\s*])\s""")
        val fuzzTests = testSources.files.map { it.readText() }.filter { it.contains("@FuzzTest") }
        val missing = mutableListOf<String>()
        var parsers = 0
        for (source in mainSources.files.filter { it.extension == "java" }.sorted()) {
            val type = source.nameWithoutExtension
            for (method in parser.findAll(source.readText()).map { it.groupValues[1] }.toSortedSet()) {
                parsers++
                if (fuzzTests.none { it.contains("$type.$method(") }) missing += "$type.$method"
            }
        }
        report.get().asFile.writeText((missing + "$parsers parsers, ${missing.size} without a fuzz target").joinToString("\n", postfix = "\n"))
        if (missing.isNotEmpty()) {
            throw GradleException("These parsers have no @FuzzTest calling them: " + missing.joinToString(", "))
        }
    }
}
