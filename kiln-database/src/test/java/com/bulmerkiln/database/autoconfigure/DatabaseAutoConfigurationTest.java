/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.KilnPostgres;
import com.bulmerkiln.database.SchemaViolationException;
import com.bulmerkiln.database.service.MigrationRunner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DatabaseAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DatabaseAutoConfiguration.class));

    /** DB-1, DB-3: migrations run, and only Database is a bean, never the pool. */
    @Test
    void migratesAndOffersOnlyTheDatabase() {
        String url = KilnPostgres.newDatabase();

        runner.withPropertyValues(properties(KilnPostgres.properties(url))).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(Database.class).doesNotHaveBean(DataSource.class);
            Database database = context.getBean(Database.class);
            int organizations = database.instanceTransaction(db -> db.fetchCount(db.selectFrom("organizations")));
            String role = database.instanceTransaction(
                    db -> db.fetchSingle("select current_user").get(0, String.class));
            assertThat(organizations).isZero();
            assertThat(role).isEqualTo(KilnPostgres.APP);
        });
    }

    /** DB-2: with migrations off, nothing is created, and the runtime role still connects. */
    @Test
    void canLeaveMigrationsToSomeoneElse() {
        String url = KilnPostgres.newDatabase();
        Map<String, String> settings = new HashMap<>(KilnPostgres.properties(url));
        settings.remove("kiln.database.migrator-username");
        settings.put("kiln.database.migrate", "false");

        runner.withPropertyValues(properties(settings)).run(context -> {
            assertThat(context).hasNotFailed();
            int schemas = context.getBean(Database.class)
                    .instanceTransaction(
                            db -> db.fetchCount(db.select().from("pg_namespace").where("nspname = 'kiln'")));
            assertThat(schemas).isZero();
        });
    }

    /** DB-6: a schema that isn't organization-safe stops the boot. */
    @Test
    void refusesToStartOnAnUnsafeSchema() {
        String url = KilnPostgres.newDatabase();
        MigrationRunner.migrate(url, KilnPostgres.MIGRATOR, KilnPostgres.MIGRATOR_PASSWORD, "kiln");
        KilnPostgres.migrator(url, "CREATE TABLE kiln.loose (id int)");

        runner.withPropertyValues(properties(KilnPostgres.properties(url)))
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(SchemaViolationException.class)
                        .hasMessageContaining("loose has no non-null uuid organization_id column"));
    }

    /** DB-6: the runtime role can't be a superuser. */
    @Test
    void refusesASuperuserAsTheRuntimeRole() {
        String url = KilnPostgres.newDatabase();

        runner.withPropertyValues(properties(KilnPostgres.properties(url)))
                .withPropertyValues(
                        "kiln.database.username=" + KilnPostgres.superuserName(),
                        "kiln.database.password=" + KilnPostgres.superuserPassword())
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(SchemaViolationException.class)
                        .hasMessageContaining("is a superuser or bypasses row-level security"));
    }

    /** DB-3: the pool's connections are closed when Kiln stops, and when it refuses to start. */
    @Test
    void releasesItsConnections() {
        String stopped = KilnPostgres.newDatabase();
        runner.withPropertyValues(properties(KilnPostgres.properties(stopped)))
                .run(context -> assertThat(context).hasNotFailed());
        KilnPostgres.dropDatabase(stopped);

        String refused = KilnPostgres.newDatabase();
        MigrationRunner.migrate(refused, KilnPostgres.MIGRATOR, KilnPostgres.MIGRATOR_PASSWORD, "kiln");
        KilnPostgres.migrator(refused, "CREATE TABLE kiln.loose (id int)");
        runner.withPropertyValues(properties(KilnPostgres.properties(refused)))
                .run(context -> assertThat(context).hasFailed());
        KilnPostgres.dropDatabase(refused);
    }

    /** DB-8: settings are checked before any connection is made. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            kiln.database.url=                            | kiln.database.url must be a jdbc:postgresql:// URL
            kiln.database.url=jdbc:mysql://localhost/kiln | kiln.database.url must be a jdbc:postgresql:// URL
            kiln.database.username=                       | kiln.database.username must name the runtime role
            kiln.database.migrator-username=              | kiln.database.migrator-username must name the
            kiln.database.migrator-username=kiln_app      | must be different roles
            kiln.database.schema=Kiln                     | kiln.database.schema must be a lowercase SQL identifier
            kiln.database.schema=kiln;drop                | kiln.database.schema must be a lowercase SQL identifier
            kiln.database.maximum-pool-size=0             | kiln.database.maximum-pool-size must be at least 1
            """)
    void refusesBadSettings(String setting, String message) {
        runner.withPropertyValues(properties(KilnPostgres.properties("jdbc:postgresql://localhost:1/kiln")))
                .withPropertyValues(setting)
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining(message));
    }

    @Test
    void defaultsTheSchemaPoolAndMigrations() {
        DatabaseProperties defaults = new DatabaseProperties(null, null, null, null, null, null, null, null);

        assertThat(defaults.schemaOrDefault()).isEqualTo("kiln");
        assertThat(defaults.migrateOrDefault()).isTrue();
        assertThat(defaults.maximumPoolSizeOrDefault()).isEqualTo(10);
        DatabaseProperties smallest =
                new DatabaseProperties("jdbc:postgresql://localhost/kiln", "app", null, null, null, null, false, 1);
        smallest.validate();
        assertThat(smallest.maximumPoolSizeOrDefault()).isEqualTo(1);
    }

    private static String[] properties(Map<String, String> settings) {
        List<String> pairs = new ArrayList<>();
        settings.forEach((key, value) -> pairs.add(key + "=" + value));
        return pairs.toArray(String[]::new);
    }
}
