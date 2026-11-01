/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.database.service.MigrationRunner;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.sql.DataSource;
import lombok.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A PostgreSQL server for tests, with the three roles an operator creates. Under Gradle it's the
 * build's shared server, named by {@code KILN_TEST_POSTGRES_FILE}, so every test and mutation-testing
 * JVM uses one; otherwise, such as from an IDE, this JVM starts its own. Each test takes its own empty
 * database from {@link #newDatabase()}, owned by the migration role, so tests never see each other's
 * rows, even from another JVM.
 */
public final class KilnPostgres {

    public static final String MIGRATOR = "kiln_migrator";
    public static final String MIGRATOR_PASSWORD = "migrator";
    public static final String APP = "kiln_app";
    public static final String APP_PASSWORD = "app";

    /** The image when this JVM starts its own server: the {@code postgres-image} in the version catalog. */
    private static final String IMAGE = "postgres:17.11-alpine3.23";

    /** Serializes creating the roles across every JVM sharing the server. */
    private static final int ROLES_LOCK = 0x6b696c6e;

    private static final Server SERVER = Server.connect();

    /** The repositories' context of each database, built once and kept for the test JVM's life. */
    private static final Map<Database, AnnotationConfigApplicationContext> CONTEXTS = new WeakHashMap<>();

    private KilnPostgres() {}

    /** Where the server is, and its superuser. */
    @Value
    private static final class Server {
        private final String jdbcUrl;

        private final String username;

        private final String password;

        static Server connect() {
            String file = System.getenv("KILN_TEST_POSTGRES_FILE");
            Server server = file == null || file.isBlank() ? startOwn() : shared(Path.of(file));
            server.createRoles();
            return server;
        }

        private static Server shared(Path file) {
            Properties connection = new Properties();
            try (Reader reader = Files.newBufferedReader(file)) {
                connection.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("Can't read the build's PostgreSQL server from " + file, e);
            }
            return new Server(
                    required(connection, "url", file),
                    required(connection, "username", file),
                    required(connection, "password", file));
        }

        private static String required(Properties connection, String key, Path file) {
            String value = connection.getProperty(key);
            if (value == null) {
                throw new IllegalStateException(file + " has no " + key);
            }
            return value;
        }

        /** Tuned like the build's shared server: tmpfs, and nothing waits for the disk. */
        @SuppressWarnings("resource") // Testcontainers stops it when the JVM exits.
        private static Server startOwn() {
            PostgreSQLContainer server = new PostgreSQLContainer(IMAGE)
                    .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
                    .withCommand(
                            "postgres",
                            "-c",
                            "fsync=off",
                            "-c",
                            "synchronous_commit=off",
                            "-c",
                            "full_page_writes=off");
            server.start();
            return new Server(server.getJdbcUrl(), server.getUsername(), server.getPassword());
        }

        /** Creates the roles once, whichever JVM sharing the server gets here first. */
        private void createRoles() {
            try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
                    Statement statement = connection.createStatement()) {
                // Held until the connection closes.
                statement.execute("SELECT pg_advisory_lock(" + ROLES_LOCK + ")");
                try (ResultSet exists =
                        statement.executeQuery("SELECT 1 FROM pg_roles WHERE rolname = '" + MIGRATOR + "'")) {
                    if (!exists.next()) {
                        statement.execute(rolesScript());
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to create the roles", e);
            }
        }

        private static String rolesScript() {
            try (InputStream script = KilnPostgres.class.getResourceAsStream("roles.sql")) {
                if (script == null) {
                    throw new IllegalStateException("roles.sql is missing from the test fixtures");
                }
                return new String(script.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Can't read roles.sql", e);
            }
        }
    }

    /**
     * Creates an empty database owned by the migration role and returns its JDBC URL. The name is
     * random, since other JVMs create databases on the same server.
     */
    public static String newDatabase() {
        String name = "kiln_test_" + UUID.randomUUID().toString().replace("-", "");
        superuser(SERVER.getJdbcUrl(), "CREATE DATABASE " + name + " OWNER " + MIGRATOR);
        return SERVER.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
    }

    /**
     * A new database with every migration on the classpath applied, connected as the runtime role.
     * Close it when done.
     */
    public static Database database() {
        String url = newDatabase();
        MigrationRunner.migrate(url, MIGRATOR, MIGRATOR_PASSWORD, "kiln");
        return Database.open(url, APP, APP_PASSWORD, "kiln", 4);
    }

    /** Adds an organization to {@code database}'s registry, as a service's directory would. */
    public static Organization organization(Database database, String slug) {
        Organization organization = new Organization(new OrganizationId(UUID.randomUUID()), slug);
        database.instanceTransaction(db -> db.execute(
                "INSERT INTO organizations (id, slug, display_name) VALUES (?, ?, ?)",
                organization.getId().getValue(),
                slug,
                slug));
        return organization;
    }

    /**
     * A repository over {@code database}, as an assembly's context would make it: every Kiln
     * repository on the classpath is available, each needing the caller's transaction. For tests and
     * harnesses that build services by hand. One context serves every repository of a database, and
     * holds nothing to close; {@code database} keeps owning the pool.
     */
    public static <R> R repository(Database database, Class<R> type) {
        AnnotationConfigApplicationContext context;
        synchronized (CONTEXTS) {
            context = CONTEXTS.computeIfAbsent(database, KilnPostgres::repositories);
        }
        return context.getBean(type);
    }

    private static AnnotationConfigApplicationContext repositories(Database database) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, database::dataSource, bean -> bean.setDestroyMethodName(""));
        context.registerBean("transactionManager", PlatformTransactionManager.class, database::transactionManager);
        context.registerBean(
                NamedParameterJdbcOperations.class, () -> new NamedParameterJdbcTemplate(database.dataSource()));
        context.register(Repositories.class);
        context.refresh();
        return context;
    }

    /** Spring Data JDBC over every Kiln repository, without defaults that open transactions. */
    @Configuration(proxyBeanMethods = false)
    @EnableJdbcRepositories(basePackages = "com.bulmerkiln", enableDefaultTransactions = false)
    static class Repositories extends AbstractJdbcConfiguration {}

    /** Runs {@code sql} as the server's superuser, for setup a migration role couldn't do. */
    public static void superuser(String url, String sql) {
        execute(url, SERVER.getUsername(), SERVER.getPassword(), sql);
    }

    /**
     * Drops a database from {@link #newDatabase()}. Postgres refuses while anyone is connected to it,
     * after giving sessions that are closing a few seconds, so this also proves every connection
     * to it was closed.
     */
    public static void dropDatabase(String url) {
        superuser(SERVER.getJdbcUrl(), "DROP DATABASE " + url.replaceFirst(".*/([^/?]+).*", "$1"));
    }

    /** Runs {@code sql} as the migration role, which owns the schema. */
    public static void migrator(String url, String sql) {
        execute(url, MIGRATOR, MIGRATOR_PASSWORD, sql);
    }

    /** The superuser's name, to show what a runtime role must never be. */
    public static String superuserName() {
        return SERVER.getUsername();
    }

    public static String superuserPassword() {
        return SERVER.getPassword();
    }

    /** The {@code kiln.database.*} properties for a database from {@link #newDatabase()}. */
    public static Map<String, String> properties(String url) {
        return Map.of(
                "kiln.database.url", url,
                "kiln.database.username", APP,
                "kiln.database.password", APP_PASSWORD,
                "kiln.database.migrator-username", MIGRATOR,
                "kiln.database.migrator-password", MIGRATOR_PASSWORD);
    }

    /** The same properties, as {@code key=value} pairs for a context runner. */
    public static String[] propertyValues(String url) {
        return properties(url).entrySet().stream()
                .map(property -> property.getKey() + "=" + property.getValue())
                .toArray(String[]::new);
    }

    private static void execute(String url, String user, String password, String sql) {
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed: " + sql, e);
        }
    }
}
