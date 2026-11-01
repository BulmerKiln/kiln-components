/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationDirectory;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.KilnPostgres;
import com.bulmerkiln.database.autoconfigure.DataJdbcAutoConfiguration;
import com.bulmerkiln.database.autoconfigure.DatabaseAutoConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** DB-9, DB-10: organizations from the database, and the ones boot config creates. */
class StoredOrganizationDirectoryTest {

    private static final ApplicationContextRunner RUNNER = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DatabaseAutoConfiguration.class, DataJdbcAutoConfiguration.class));

    /** DB-9: ensuring is idempotent, and an organization keeps its ID. */
    @Test
    void createsEachOrganizationOnce() {
        withDirectory((directory, database) -> {
            Organization first = directory.ensure("homelab");
            Organization again = directory.ensure("homelab");

            assertThat(again).isEqualTo(first);
            assertThat(directory.bySlug("homelab")).contains(first);
            assertThat(directory.byId(first.getId())).contains(first);
        });
    }

    /** DB-9: only active organizations are found, so a suspended one looks like none. */
    @Test
    void findsOnlyActiveOrganizations() {
        withDirectory((directory, database) -> {
            Organization paused = directory.ensure("paused");
            database.instanceTransaction(
                    db -> db.execute("UPDATE organizations SET status = 'suspended' WHERE slug = ?", "paused"));

            assertThat(directory.bySlug("paused")).isEmpty();
            assertThat(directory.byId(paused.getId())).isEmpty();
            assertThat(directory.all()).doesNotContain(paused);
            assertThat(directory.ensure("paused")).isEqualTo(paused);
        });
    }

    /** DB-9: an unknown, invalid or reserved slug, or an unknown ID, finds nothing. */
    @Test
    void findsNothingForUnknownOrInvalidSlugs() {
        withDirectory((directory, database) -> {
            assertThat(directory.bySlug("nobody")).isEmpty();
            assertThat(directory.bySlug("Not A Slug")).isEmpty();
            assertThat(directory.bySlug("v2")).isEmpty();
            assertThat(directory.byId(new OrganizationId(UUID.randomUUID()))).isEmpty();
        });
    }

    /** DB-9: all active organizations, in slug order. */
    @Test
    void listsActiveOrganizationsBySlug() {
        withDirectory((directory, database) -> {
            directory.ensure("zeta");
            directory.ensure("alpha");

            List<String> slugs =
                    directory.all().stream().map(Organization::getSlug).toList();
            assertThat(slugs).containsExactly("alpha", "zeta");
        });
    }

    /** DB-10: boot config's organizations exist after start; an invalid slug stops the boot. */
    @Test
    void createsTheOrganizationsBootConfigLists() {
        String url = KilnPostgres.newDatabase();
        ApplicationContextRunner runner = RUNNER.withPropertyValues(properties(KilnPostgres.properties(url)));

        runner.withPropertyValues("kiln.bootstrap.organizations=acme,globex")
                .run(context -> assertThat(
                                context.getBean(OrganizationDirectory.class).all())
                        .extracting(Organization::getSlug)
                        .containsExactly("acme", "globex"));
        runner.withPropertyValues("kiln.bootstrap.organizations=acme,metrics")
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("invalid or reserved slug: metrics"));
        runner.run(context -> assertThat(
                        context.getBean(OrganizationDirectory.class).all())
                .extracting(Organization::getSlug)
                .containsExactly("acme", "globex"));
    }

    private static void withDirectory(BiConsumer<StoredOrganizationDirectory, Database> test) {
        RUNNER.withPropertyValues(properties(KilnPostgres.properties(KilnPostgres.newDatabase())))
                .run(context -> test.accept(
                        context.getBean(StoredOrganizationDirectory.class), context.getBean(Database.class)));
    }

    private static String[] properties(Map<String, String> settings) {
        List<String> pairs = new ArrayList<>();
        settings.forEach((key, value) -> pairs.add(key + "=" + value));
        return pairs.toArray(String[]::new);
    }
}
