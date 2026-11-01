package com.bulmerkiln.build

import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * One PostgreSQL server for every test and mutation-testing JVM in a build, instead of a container
 * per JVM. Started the first time a test task runs, removed when the build ends. Its connection
 * goes to [Params.connectionFile], which the JVMs find through `KILN_TEST_POSTGRES_FILE`; each test
 * still takes its own database, so sharing the server shares no rows.
 *
 * Tuned for throwaway data: the data directory is on tmpfs and nothing waits for the disk.
 *
 * Runs `docker` directly rather than through Testcontainers, whose Ryuk reaper would live as long
 * as the Gradle daemon. Instead each server is labelled with this host and the daemon's process ID,
 * and a build removes any server whose daemon has gone, such as after a crash.
 */
abstract class KilnPostgresServer : BuildService<KilnPostgresServer.Params>, AutoCloseable {

    interface Params : BuildServiceParameters {
        val image: Property<String>
        val connectionFile: RegularFileProperty
    }

    private var container: String? = null

    @Synchronized
    fun start() {
        if (container != null) return
        removeOrphans()
        val password = UUID.randomUUID().toString()
        val id = docker(
            "run", "--detach", "--rm",
            "--label", "$LABEL=postgres",
            "--label", "$HOST_LABEL=$HOST",
            "--label", "$PID_LABEL=${ProcessHandle.current().pid()}",
            "--env", "POSTGRES_PASSWORD=$password",
            "--tmpfs", "/var/lib/postgresql/data:rw",
            "--publish", "127.0.0.1::$PORT",
            parameters.image.get(),
            // Many JVMs at once, each with a pool per test database.
            "postgres", "-c", "fsync=off", "-c", "synchronous_commit=off", "-c", "full_page_writes=off",
            "-c", "max_connections=500",
        ).trim()
        container = id
        awaitReady(id)
        val port = docker("port", id, "$PORT/tcp").lineSequence().first().substringAfterLast(':').trim()
        val connection = Properties().apply {
            setProperty("url", "jdbc:postgresql://127.0.0.1:$port/postgres")
            setProperty("username", "postgres")
            setProperty("password", password)
        }
        val file = parameters.connectionFile.get().asFile
        file.parentFile.mkdirs()
        file.writer().use { connection.store(it, "The build's shared PostgreSQL server; removed when the build ends") }
    }

    override fun close() {
        container?.let { docker("rm", "--force", it) }
        container = null
        parameters.connectionFile.get().asFile.delete()
    }

    /**
     * The entrypoint first starts the server on a socket only, to initialize it, so it's ready once
     * it answers over TCP.
     */
    private fun awaitReady(id: String) {
        val deadline = Instant.now().plus(STARTUP)
        while (Instant.now().isBefore(deadline)) {
            if (run("exec", id, "pg_isready", "--host", "127.0.0.1", "--username", "postgres").exit == 0) return
            Thread.sleep(POLL.toMillis())
        }
        val logs = run("logs", id).output
        throw GradleException("PostgreSQL didn't start within $STARTUP:\n$logs")
    }

    /** Removes servers this host's builds started whose Gradle daemon no longer exists. */
    private fun removeOrphans() {
        val servers = docker(
            "ps", "--all", "--filter", "label=$LABEL=postgres", "--filter", "label=$HOST_LABEL=$HOST",
            "--format", "{{.ID}} {{.Label \"$PID_LABEL\"}}",
        )
        for (line in servers.lines().filter { it.isNotBlank() }) {
            val (id, pid) = line.trim().split(' ', limit = 2).let { it[0] to it.getOrNull(1)?.toLongOrNull() }
            if (pid == null || ProcessHandle.of(pid).isEmpty) docker("rm", "--force", id)
        }
    }

    private fun docker(vararg args: String): String {
        val result = run(*args)
        if (result.exit != 0) {
            throw GradleException("docker ${args.first()} failed (${result.exit}):\n${result.output}")
        }
        return result.output
    }

    private class Result(val exit: Int, val output: String)

    private fun run(vararg args: String): Result {
        val process = try {
            ProcessBuilder(listOf("docker") + args).redirectErrorStream(true).start()
        } catch (e: java.io.IOException) {
            throw GradleException("Tests need Docker for PostgreSQL, and `docker` couldn't be run", e)
        }
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(COMMAND_TIMEOUT.seconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw GradleException("docker ${args.first()} didn't finish within $COMMAND_TIMEOUT")
        }
        return Result(process.exitValue(), output)
    }

    private companion object {
        const val PORT = 5432
        const val LABEL = "com.bulmerkiln.kiln-build"
        const val HOST_LABEL = "com.bulmerkiln.kiln-build.host"
        const val PID_LABEL = "com.bulmerkiln.kiln-build.pid"
        val HOST: String = InetAddress.getLocalHost().hostName
        val STARTUP: Duration = Duration.ofSeconds(60)
        val POLL: Duration = Duration.ofMillis(200)
        val COMMAND_TIMEOUT: Duration = Duration.ofMinutes(5)
    }
}
