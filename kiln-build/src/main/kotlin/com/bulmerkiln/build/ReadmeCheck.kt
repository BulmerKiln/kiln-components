package com.bulmerkiln.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Keeps the module READMEs and the code in step: every module has a README, every requirement ID
 * is unique, every test a README names exists, and every requirement ID the code cites is defined.
 */
abstract class ReadmeCheck : DefaultTask() {

    /** The repository root, to report paths relative to it. */
    @get:Internal
    abstract val root: DirectoryProperty

    /** Every module's build file, to find the modules. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val modules: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val readmes: ConfigurableFileCollection

    /** Java sources, build scripts and the version catalog, which may cite requirement IDs. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val rootDir = root.get().asFile
        fun rel(file: java.io.File) = file.relativeTo(rootDir).path
        val problems = mutableListOf<String>()

        val readmeDirs = readmes.files.map { it.parentFile }.toSet()
        for (module in modules.files.map { it.parentFile }.sorted()) {
            if (module !in readmeDirs) problems += "${rel(module)} has no README.md"
        }

        val row = Regex("""^\| ([A-Z]+(?:-[A-Z0-9]+)*-\d+) \|(.*)\|(.*)\|\s*$""", RegexOption.MULTILINE)
        val named = Regex("""`([A-Z][A-Za-z0-9]*(?:Test|Contract))`""")
        val defined = mutableMapOf<String, String>()
        val testsNamed = mutableMapOf<String, String>()
        for (readme in readmes.files.sorted()) {
            for (match in row.findAll(readme.readText())) {
                val id = match.groupValues[1]
                val previous = defined.putIfAbsent(id, rel(readme))
                if (previous != null) problems += "$id is defined in both $previous and ${rel(readme)}"
                for (test in named.findAll(match.groupValues[3])) testsNamed.putIfAbsent(test.groupValues[1], "$id in ${rel(readme)}")
            }
        }

        val classes = sources.files.filter { it.extension == "java" }.map { it.nameWithoutExtension }.toSet()
        for ((test, where) in testsNamed.toSortedMap()) {
            if (test !in classes) problems += "$where names $test, which doesn't exist"
        }

        // Only prefixes the READMEs use count as citations, so SHA-256 or CVE-2024-3094 never do.
        val prefixes = defined.keys.map { it.substringBefore('-') }.toSet().sorted()
        if (prefixes.isNotEmpty()) {
            val cited = Regex("""\b((?:${prefixes.joinToString("|")})(?:-[A-Z0-9]+)*-\d+)\b""")
            for (source in sources.files.sorted()) {
                for (match in cited.findAll(source.readText())) {
                    val id = match.groupValues[1]
                    if (id !in defined) problems += "${rel(source)} cites $id, which no README defines"
                }
            }
        }

        val summary = "${defined.size} requirements, ${testsNamed.size} tests named, ${problems.size} problems"
        report.get().asFile.writeText((problems + summary).joinToString("\n", postfix = "\n"))
        if (problems.isNotEmpty()) {
            throw GradleException("The READMEs and the code disagree:\n" + problems.joinToString("\n") { "  - $it" })
        }
    }
}
