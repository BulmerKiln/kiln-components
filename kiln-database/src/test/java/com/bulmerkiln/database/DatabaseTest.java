/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.database.service.MigrationRunner;
import com.bulmerkiln.database.service.SchemaLinter;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs against a real PostgreSQL with the three roles, as Kiln's runtime role. */
class DatabaseTest {

    private static final Organization ACME = new Organization(new OrganizationId(UUID.randomUUID()), "acme");
    private static final Organization GLOBEX = new Organization(new OrganizationId(UUID.randomUUID()), "globex");

    private Database database;

    @BeforeEach
    void migrate() {
        String url = KilnPostgres.newDatabase();
        MigrationRunner.migrate(url, KilnPostgres.MIGRATOR, KilnPostgres.MIGRATOR_PASSWORD, "kiln");
        // One connection, so every transaction reuses the one before it.
        database = Database.open(url, KilnPostgres.APP, KilnPostgres.APP_PASSWORD, "kiln", 1);
        database.instanceTransaction(db -> {
            for (Organization organization : List.of(ACME, GLOBEX)) {
                db.insertInto(table("organizations"), field("id"), field("slug"), field("display_name"))
                        .values(organization.id().value(), organization.slug(), organization.slug())
                        .execute();
            }
            return null;
        });
    }

    @AfterEach
    void close() {
        database.close();
    }

    /** DB-4: each organization's transactions see only its own rows. */
    @Test
    void organizationsSeeOnlyTheirOwnRows() {
        addWidget(ACME, "anvil");
        addWidget(GLOBEX, "gizmo");

        assertThat(widgets(ACME)).containsExactly("anvil");
        assertThat(widgets(GLOBEX)).containsExactly("gizmo");
    }

    /** DB-4: row-level security refuses a row for another organization, even when written explicitly. */
    @Test
    void refusesWritingAnotherOrganizationsRow() {
        assertThatThrownBy(() -> OrganizationContext.call(
                        ACME,
                        () -> database.transaction(db -> db.insertInto(
                                        table("widgets"), field("organization_id"), field("id"), field("name"))
                                .values(GLOBEX.id().value(), UUID.randomUUID(), "planted")
                                .execute())))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("row-level security");

        assertThat(widgets(GLOBEX)).isEmpty();
    }

    /** DB-5: no organization in context fails before reaching the database. */
    @Test
    void refusesATransactionOutsideAnOrganization() {
        assertThatThrownBy(() -> database.transaction(db -> db.fetch("select 1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No organization");
    }

    /** DB-5: an instance transaction can't read organization tables, on a fresh connection or not. */
    @Test
    void instanceTransactionsCantReadOrganizationTables() {
        assertThatThrownBy(() -> database.instanceTransaction(db -> db.fetch("select * from widgets")))
                .isInstanceOf(DataAccessException.class);

        addWidget(ACME, "anvil");

        // The same pooled connection just ran ACME's transaction; the setting must not carry over.
        assertThatThrownBy(() -> database.instanceTransaction(db -> db.fetch("select * from widgets")))
                .isInstanceOf(DataAccessException.class);
    }

    /** DB-4: the organization is transaction-local, so it never outlives its transaction. */
    @Test
    void theOrganizationEndsWithItsTransaction() {
        addWidget(ACME, "anvil");

        String after =
                database.instanceTransaction(db -> db.fetchSingle("select current_setting('app.organization_id', true)")
                        .get(0, String.class));
        assertThat(after).isNullOrEmpty();
    }

    /** DB-4: a failed transaction rolls back, and the next one starts clean. */
    @Test
    void aFailedTransactionRollsBack() {
        assertThatThrownBy(() -> OrganizationContext.call(
                        ACME,
                        () -> database.transaction(db -> {
                            insertWidget(db, ACME, "half");
                            throw new IllegalStateException("boom");
                        })))
                .hasMessage("boom");

        assertThat(widgets(ACME)).isEmpty();
    }

    /** DB-4: work that returns nothing runs the same way, for the current organization. */
    @Test
    void runsWorkThatReturnsNothing() {
        OrganizationContext.run(ACME, () -> database.run(db -> insertWidget(db, ACME, "bolt")));

        assertThat(widgets(ACME)).containsExactly("bolt");
        assertThat(widgets(GLOBEX)).isEmpty();
    }

    /** DB-6: the migrated schema passes the linter as the runtime role. */
    @Test
    void theMigratedSchemaIsOrganizationSafe() {
        assertThat(database.instanceTransaction(SchemaLinter::problems)).isEmpty();
    }

    private void addWidget(Organization organization, String name) {
        OrganizationContext.call(organization, () -> database.transaction(db -> insertWidget(db, organization, name)));
    }

    private static int insertWidget(DSLContext db, Organization organization, String name) {
        return db.insertInto(table("widgets"), field("organization_id"), field("id"), field("name"))
                .values(organization.id().value(), UUID.randomUUID(), name)
                .execute();
    }

    private List<String> widgets(Organization organization) {
        return OrganizationContext.call(
                organization,
                () -> database.transaction(db -> db.select(field("name", String.class))
                        .from(table("widgets"))
                        .fetch(0, String.class)));
    }
}
