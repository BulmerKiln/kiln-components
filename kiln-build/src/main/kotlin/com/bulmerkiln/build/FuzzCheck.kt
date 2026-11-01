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
 * `fromPath` method taking a `String` or `byte[]`, and every method annotated `@UntrustedInput`,
 * must be called from the body of a `@FuzzTest` method (BUILD-14).
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
        val main = mainSources.files.filter { it.extension == "java" }.sortedBy { it.path }
            .map { it.nameWithoutExtension to it.readText() }
        val result = FuzzTargets.check(main, testSources.files.map { it.readText() })
        val summary = "${result.targets} parsers, ${result.missing.size} without a fuzz target"
        report.get().asFile.writeText((result.missing + summary).joinToString("\n", postfix = "\n"))
        if (result.missing.isNotEmpty()) {
            throw GradleException("These parsers have no @FuzzTest calling them: " + result.missing.joinToString(", "))
        }
    }
}

/** The source matching behind [FuzzCheck], kept apart from Gradle so it can be tested. */
internal object FuzzTargets {

    private val parser = Regex("""\bstatic\s+[\w<>\[\],?\s]+?\s(parse|fromPath)\(\s*(?:final\s+)?(?:String|byte\s*\[\s*])\s""")
    private val untrusted = Regex("""@UntrustedInput\b""")
    private val annotation = Regex("""@\w+(?:\s*\([^)]*\))?""")
    private val methodName = Regex("""(\w+)\s*\(""")
    private val staticModifier = Regex("""\bstatic\b""")

    data class Target(val type: String, val method: String, val isStatic: Boolean)

    data class Result(val targets: Int, val missing: List<String>)

    /** Checks each `(type, source)` against the `@FuzzTest` bodies in [tests]. */
    fun check(main: List<Pair<String, String>>, tests: List<String>): Result {
        val fuzzed = tests.filter { it.contains("@FuzzTest") }.map { it to fuzzBodies(it) }
        val targets = main.flatMap { (type, source) -> targets(type, source) }
        val missing = targets.filterNot { target -> fuzzed.any { (file, bodies) -> calls(target, file, bodies) } }
            .map { "${it.type}.${it.method}" }
        return Result(targets.size, missing)
    }

    /** The parsers declared in [source], the file of [type]. */
    fun targets(type: String, source: String): Set<Target> {
        val found = sortedSetOf<Target>(compareBy({ it.method }, { it.isStatic }))
        parser.findAll(source).forEach { found += Target(type, it.groupValues[1], true) }
        for (match in untrusted.findAll(source)) {
            val end = source.indexOfAny(charArrayOf('{', ';'), match.range.last + 1).let { if (it < 0) source.length else it }
            // Drop the other annotations, such as @Nullable or @SuppressWarnings("…"), so the first
            // name followed by a parenthesis is the method's own.
            val header = annotation.replace(source.substring(match.range.last + 1, end), " ")
            val name = methodName.find(header) ?: continue
            found += Target(type, name.groupValues[1], staticModifier.containsMatchIn(header.substring(0, name.range.first)))
        }
        return found
    }

    /**
     * A static target must be called as `Type.method(` inside a fuzz body. An instance target's
     * receiver can't be told from the source, so it must be called as `.method(` inside a fuzz
     * body, in a test file that names its type.
     */
    private fun calls(target: Target, file: String, bodies: List<String>): Boolean {
        if (target.isStatic) {
            return bodies.any { it.contains("${target.type}.${target.method}(") }
        }
        val call = Regex("""\.${Regex.escape(target.method)}\s*\(""")
        return Regex("""\b${Regex.escape(target.type)}\b""").containsMatchIn(file) && bodies.any { call.containsMatchIn(it) }
    }

    /** The bodies of the methods annotated `@FuzzTest` in [test]. */
    fun fuzzBodies(test: String): List<String> =
        Regex("""@FuzzTest\b""").findAll(test).mapNotNull { match ->
            val open = test.indexOf('{', match.range.last + 1)
            if (open < 0) null else block(test, open)
        }.toList()

    /** The block opening at [open], skipping braces inside comments, strings and characters. */
    private fun block(text: String, open: Int): String {
        var depth = 0
        var i = open
        while (i < text.length) {
            when {
                text.startsWith("//", i) -> i = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                text.startsWith("/*", i) -> i = text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 1 }
                text.startsWith("\"\"\"", i) -> i = text.indexOf("\"\"\"", i + 3).let { if (it < 0) text.length else it + 2 }
                text[i] == '"' || text[i] == '\'' -> i = literalEnd(text, i)
                text[i] == '{' -> depth++
                text[i] == '}' -> if (--depth == 0) return text.substring(open, i + 1)
            }
            i++
        }
        return text.substring(open)
    }

    private fun literalEnd(text: String, start: Int): Int {
        var i = start + 1
        while (i < text.length && text[i] != text[start]) {
            if (text[i] == '\\') i++
            i++
        }
        return i
    }
}
