/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.KilnPostgres;
import com.bulmerkiln.database.autoconfigure.DataJdbcAutoConfiguration;
import com.bulmerkiln.database.autoconfigure.DatabaseAutoConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** Repositories over an organization table, in Database's Spring transactions. */
class KilnRepositoryTest {

    private static final ApplicationContextRunner RUNNER = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DatabaseAutoConfiguration.class, DataJdbcAutoConfiguration.class))
            .withUserConfiguration(Gadgets.class);

    /** A gadget's key: the organization and its own ID, as every organization table has. */
    @Value
    static final class GadgetId {
        private final UUID organizationId;

        private final UUID id;
    }

    @Table("gadgets")
    @AllArgsConstructor
    @Value
    static final class Gadget implements Persistable<GadgetId> {
        @Id
        private final GadgetId id;

        private final String name;

        @Transient
        private final boolean fresh;

        /** How Spring Data reads a row: an existing gadget, so not new. */
        @PersistenceCreator
        Gadget(GadgetId id, String name) {
            this(id, name, false);
        }

        @Override
        public GadgetId getId() {
            return id;
        }

        @Override
        public boolean isNew() {
            return fresh;
        }
    }

    interface GadgetRepository extends KilnRepository<Gadget, GadgetId> {

        @Query("SELECT organization_id, id, name FROM gadgets ORDER BY name")
        List<Gadget> findAllVisible();

        @Modifying
        @Query("UPDATE gadgets SET name = :name WHERE organization_id = :organizationId AND id = :id")
        int rename(UUID organizationId, UUID id, String name);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJdbcRepositories(
            basePackageClasses = GadgetRepository.class,
            considerNestedRepositories = true,
            enableDefaultTransactions = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = GadgetRepository.class))
    static class Gadgets {}

    /** DB-4: each organization's transaction sees and writes only its own rows. */
    @Test
    void transactionsBindTheirOrganization() {
        withGadgets((database, gadgets) -> {
            Organization acme = KilnPostgres.organization(database, "acme");
            Organization globex = KilnPostgres.organization(database, "globex");
            Gadget anvil = gadget(acme, "anvil");
            OrganizationContext.run(acme, () -> database.run(() -> gadgets.save(anvil)));

            assertThat(OrganizationContext.call(acme, () -> database.transaction(gadgets::findAllVisible)))
                    .extracting(Gadget::getName)
                    .containsExactly("anvil");
            assertThat(OrganizationContext.call(globex, () -> database.transaction(gadgets::findAllVisible)))
                    .isEmpty();
            assertThat(OrganizationContext.call(
                            globex, () -> database.transaction(() -> gadgets.findById(anvil.getId()))))
                    .isEmpty();
        });
    }

    /** DB-4: writing another organization's row is refused by row-level security. */
    @Test
    void anotherOrganizationsRowIsRefused() {
        withGadgets((database, gadgets) -> {
            Organization acme = KilnPostgres.organization(database, "acme");
            Organization globex = KilnPostgres.organization(database, "globex");

            assertThatThrownBy(() -> OrganizationContext.run(
                            globex, () -> database.run(() -> gadgets.save(gadget(acme, "smuggled")))))
                    .rootCause()
                    .hasMessageContaining("row-level security");
        });
    }

    /** DB-4: a failed transaction rolls back everything it did. */
    @Test
    void failedTransactionsRollBack() {
        withGadgets((database, gadgets) -> {
            Organization acme = KilnPostgres.organization(database, "acme");

            assertThatThrownBy(() -> OrganizationContext.run(
                            acme,
                            () -> database.run(() -> {
                                gadgets.save(gadget(acme, "half"));
                                throw new IllegalStateException("fails after the insert");
                            })))
                    .hasMessage("fails after the insert");
            assertThat(OrganizationContext.call(acme, () -> database.transaction(gadgets::findAllVisible)))
                    .isEmpty();
        });
    }

    /**
     * DB-5: without an organization in context, an organization transaction fails before reaching
     * the database; an instance transaction, or a transaction that names no organization, is
     * refused by the organization table.
     */
    @Test
    void noOrganizationIsRefused() {
        withGadgets((database, gadgets) -> {
            assertThatThrownBy(() -> database.transaction(gadgets::findAllVisible))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No organization in context");
            assertThatThrownBy(() -> database.instanceTransaction(gadgets::findAllVisible))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            TransactionTemplate bare = new TransactionTemplate(database.transactionManager());
            assertThatThrownBy(() -> bare.execute(status -> gadgets.findAllVisible()))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
        });
    }

    /** DB-11: a repository never runs outside a transaction, so nothing runs in auto-commit. */
    @Test
    void repositoriesNeedATransaction() {
        withGadgets((database, gadgets) -> {
            Organization acme = KilnPostgres.organization(database, "acme");
            Gadget anvil = gadget(acme, "anvil");

            assertThatThrownBy(() -> gadgets.save(anvil)).isInstanceOf(IllegalTransactionStateException.class);
            assertThatThrownBy(() -> gadgets.findById(anvil.getId()))
                    .isInstanceOf(IllegalTransactionStateException.class);
            assertThatThrownBy(gadgets::findAllVisible).isInstanceOf(IllegalTransactionStateException.class);
            assertThatThrownBy(() -> gadgets.rename(acme.getId().getValue(), UUID.randomUUID(), "x"))
                    .isInstanceOf(IllegalTransactionStateException.class);
        });
    }

    /** DB-11: save() inserts; an update is a named query that sets only the columns it names. */
    @Test
    void saveInsertsAndNamedQueriesUpdate() {
        withGadgets((database, gadgets) -> {
            Organization acme = KilnPostgres.organization(database, "acme");
            Gadget anvil = gadget(acme, "anvil");
            OrganizationContext.run(acme, () -> database.run(() -> gadgets.save(anvil)));

            int renamed = OrganizationContext.call(
                    acme,
                    () -> database.transaction(() -> gadgets.rename(
                            acme.getId().getValue(), anvil.getId().getId(), "hammer")));

            assertThat(renamed).isEqualTo(1);
            Optional<Gadget> found =
                    OrganizationContext.call(acme, () -> database.transaction(() -> gadgets.findById(anvil.getId())));
            assertThat(found).map(Gadget::getName).contains("hammer");
        });
    }

    private static Gadget gadget(Organization organization, String name) {
        return new Gadget(new GadgetId(organization.getId().getValue(), UUID.randomUUID()), name, true);
    }

    /** A fresh database with an organization table of gadgets, and its repository. */
    private static void withGadgets(BiConsumer<Database, GadgetRepository> test) {
        String url = KilnPostgres.newDatabase();
        RUNNER.withPropertyValues(properties(url)).run(context -> {
            assertThat(context).hasNotFailed();
            KilnPostgres.migrator(url, """
                    SET search_path TO kiln;
                    CREATE TABLE gadgets (organization_id uuid NOT NULL REFERENCES organizations (id),
                                          id uuid NOT NULL, name text NOT NULL,
                                          PRIMARY KEY (organization_id, id));
                    ALTER TABLE gadgets ENABLE ROW LEVEL SECURITY;
                    ALTER TABLE gadgets FORCE ROW LEVEL SECURITY;
                    CREATE POLICY organization ON gadgets
                        USING (organization_id = current_setting('app.organization_id')::uuid)
                        WITH CHECK (organization_id = current_setting('app.organization_id')::uuid);
                    """);
            test.accept(context.getBean(Database.class), context.getBean(GadgetRepository.class));
        });
    }

    private static String[] properties(String url) {
        List<String> pairs = new ArrayList<>();
        KilnPostgres.properties(url).forEach((key, value) -> pairs.add(key + "=" + value));
        return pairs.toArray(String[]::new);
    }
}
