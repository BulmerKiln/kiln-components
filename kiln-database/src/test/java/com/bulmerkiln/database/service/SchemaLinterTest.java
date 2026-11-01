/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.bulmerkiln.database.KilnPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** DB-6: each way row-level security fails silently is caught. Each case gets its own database. */
class SchemaLinterTest {

    private static final String ORGANIZATION_POLICY =
            "USING (organization_id = current_setting('app.organization_id')::uuid)";

    /** An organization table that passes, for the cases to break. */
    private static final String GADGETS = """
            CREATE TABLE gadgets (organization_id uuid NOT NULL, id uuid NOT NULL, owner uuid,
                                  PRIMARY KEY (organization_id, id));
            ALTER TABLE gadgets ENABLE ROW LEVEL SECURITY;
            ALTER TABLE gadgets FORCE ROW LEVEL SECURITY;
            CREATE POLICY organization ON gadgets %s;
            """.formatted(ORGANIZATION_POLICY);

    static Stream<Arguments> unsafe() {
        return Stream.of(
                arguments("CREATE TABLE loose (id int)", "loose has no non-null uuid organization_id column"),
                arguments("CREATE TABLE loose (organization_id uuid)", "loose has no non-null uuid organization_id"),
                arguments("CREATE TABLE loose (organization_id text NOT NULL)", "loose has no non-null uuid"),
                arguments(
                        "CREATE TABLE loose (organization_id uuid NOT NULL)",
                        "loose doesn't have row-level security enabled and forced"),
                arguments(
                        "CREATE TABLE loose (organization_id uuid NOT NULL); "
                                + "ALTER TABLE loose ENABLE ROW LEVEL SECURITY",
                        "loose doesn't have row-level security enabled and forced"),
                arguments(
                        "CREATE TABLE loose (organization_id uuid NOT NULL)", "loose has no row-level security policy"),
                arguments("CREATE POLICY open ON gadgets USING (true)", "gadgets has permissive policy open"),
                arguments(
                        "CREATE POLICY reads ON gadgets FOR SELECT " + ORGANIZATION_POLICY,
                        "gadgets has permissive policy reads"),
                arguments("CREATE POLICY writes ON gadgets WITH CHECK (true)", "gadgets has permissive policy writes"),
                arguments(
                        "ALTER TABLE gadgets ADD COLUMN name text UNIQUE", "gadgets has unique index gadgets_name_key"),
                arguments(
                        "CREATE UNIQUE INDEX by_id ON gadgets (id, organization_id)", "gadgets has unique index by_id"),
                arguments("CREATE UNIQUE INDEX by_text ON gadgets ((id::text))", "gadgets has unique index by_text"),
                arguments(
                        "ALTER TABLE gadgets ADD COLUMN parent uuid REFERENCES organizations (id)",
                        "gadgets has foreign key gadgets_parent_fkey"),
                arguments("""
                        ALTER TABLE gadgets ADD UNIQUE (owner, id);
                        CREATE TABLE parts (organization_id uuid NOT NULL, gadget uuid NOT NULL,
                            FOREIGN KEY (organization_id, gadget) REFERENCES gadgets (owner, id))
                        """, "parts has foreign key"),
                arguments(
                        "CREATE FUNCTION peek() RETURNS int LANGUAGE sql SECURITY DEFINER AS 'select 1'",
                        "function peek is SECURITY DEFINER"),
                arguments(
                        "CREATE VIEW all_gadgets AS SELECT * FROM gadgets",
                        "view all_gadgets isn't created with security_invoker"));
    }

    @ParameterizedTest
    @MethodSource("unsafe")
    void reports(String ddl, String problem) {
        String url = migrated();
        KilnPostgres.migrator(url, "SET search_path TO kiln; " + ddl);

        assertThat(lintAs(url, KilnPostgres.APP, KilnPostgres.APP_PASSWORD)).anyMatch(p -> p.startsWith(problem));
    }

    static Stream<String> safe() {
        return Stream.of(
                "SELECT 1",
                "CREATE VIEW mine WITH (security_invoker = true) AS SELECT * FROM gadgets",
                "CREATE POLICY only_owned ON gadgets AS RESTRICTIVE USING (owner IS NOT NULL)",
                """
                CREATE TABLE parts (organization_id uuid NOT NULL, id uuid NOT NULL, gadget uuid NOT NULL,
                    PRIMARY KEY (organization_id, id),
                    FOREIGN KEY (organization_id, gadget) REFERENCES gadgets (organization_id, id));
                ALTER TABLE parts ENABLE ROW LEVEL SECURITY;
                ALTER TABLE parts FORCE ROW LEVEL SECURITY;
                CREATE POLICY organization ON parts %s
                    WITH CHECK (organization_id = current_setting('app.organization_id')::uuid)
                """.formatted(ORGANIZATION_POLICY),
                "CREATE TABLE leases (job text PRIMARY KEY, holder text UNIQUE)",
                // A key to an instance table other than organizations names no organization's row.
                "CREATE TABLE leases (job text PRIMARY KEY); "
                        + "ALTER TABLE gadgets ADD COLUMN job text REFERENCES leases (job)");
    }

    @ParameterizedTest
    @MethodSource("safe")
    void accepts(String ddl) {
        String url = migrated();
        KilnPostgres.migrator(url, "SET search_path TO kiln; " + ddl);

        assertThat(lintAs(url, KilnPostgres.APP, KilnPostgres.APP_PASSWORD)).isEmpty();
    }

    static Stream<Arguments> unsafeRoles() {
        return Stream.of(
                arguments(
                        "kiln_lint_bypass",
                        "CREATE ROLE kiln_lint_bypass LOGIN PASSWORD 'x' BYPASSRLS",
                        "runtime role kiln_lint_bypass is a superuser or bypasses row-level security"),
                arguments(
                        "kiln_lint_owner",
                        "CREATE ROLE kiln_lint_owner LOGIN PASSWORD 'x'; "
                                + "ALTER TABLE kiln.gadgets OWNER TO kiln_lint_owner",
                        "runtime role kiln_lint_owner owns tables, functions or schemas"));
    }

    @ParameterizedTest
    @MethodSource("unsafeRoles")
    void reportsAnUnsafeRuntimeRole(String role, String setup, String problem) {
        String url = migrated();
        KilnPostgres.superuser(url, setup + "; GRANT USAGE ON SCHEMA kiln TO " + role);

        assertThat(lintAs(url, role, "x")).contains(problem);
    }

    @Test
    void reportsASuperuserRuntimeRole() {
        String url = migrated();

        assertThat(lintAs(url, KilnPostgres.superuserName(), KilnPostgres.superuserPassword()))
                .anyMatch(p -> p.endsWith("is a superuser or bypasses row-level security"));
    }

    /** A migrated database with the organization table {@code gadgets} besides the test migrations'. */
    private static String migrated() {
        String url = KilnPostgres.newDatabase();
        MigrationRunner.migrate(url, KilnPostgres.MIGRATOR, KilnPostgres.MIGRATOR_PASSWORD, "kiln");
        KilnPostgres.migrator(url, "SET search_path TO kiln; " + GADGETS);
        return url;
    }

    private static List<String> lintAs(String url, String user, String password) {
        String inSchema = url + (url.contains("?") ? "&" : "?") + "currentSchema=kiln";
        try (Connection connection = DriverManager.getConnection(inSchema, user, password)) {
            return SchemaLinter.problems(DSL.using(connection, SQLDialect.POSTGRES));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
