/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.database.service.MigrationRunner;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A PostgreSQL server for tests, shared by every test in the JVM, with the three roles an operator
 * creates. Each test takes its own empty database from {@link #newDatabase()}, owned by the
 * migration role, so tests never see each other's rows.
 */
public final class KilnPostgres {

    public static final String MIGRATOR = "kiln_migrator";
    public static final String MIGRATOR_PASSWORD = "migrator";
    public static final String APP = "kiln_app";
    public static final String APP_PASSWORD = "app";

    private static final AtomicInteger DATABASES = new AtomicInteger();
    private static final PostgreSQLContainer SERVER = start();

    private KilnPostgres() {}

    private static PostgreSQLContainer start() {
        PostgreSQLContainer server = new PostgreSQLContainer("postgres:17.11-alpine3.23")
                .withInitScript("com/bulmerkiln/database/roles.sql");
        server.start();
        return server;
    }

    /** Creates an empty database owned by the migration role and returns its JDBC URL. */
    public static String newDatabase() {
        String name = "kiln_test_" + DATABASES.incrementAndGet();
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
                organization.id().value(),
                slug,
                slug));
        return organization;
    }

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

    private static void execute(String url, String user, String password, String sql) {
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed: " + sql, e);
        }
    }
}
