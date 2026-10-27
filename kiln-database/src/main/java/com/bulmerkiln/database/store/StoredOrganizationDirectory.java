/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.store;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationDirectory;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.database.Database;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Record;

/**
 * The organizations in the {@code organizations} table (DB-9). Only active ones are found: a
 * suspended or deleting organization looks like one that doesn't exist.
 */
public final class StoredOrganizationDirectory implements OrganizationDirectory {

    private static final String ACTIVE = "SELECT id, slug FROM organizations WHERE status = 'active'";

    private final Database database;

    public StoredOrganizationDirectory(Database database) {
        this.database = database;
    }

    @Override
    public Optional<Organization> byId(OrganizationId id) {
        return database.instanceTransaction(tx ->
                tx.fetchOptional(ACTIVE + " AND id = ?", id.value()).map(StoredOrganizationDirectory::organization));
    }

    @Override
    public Optional<Organization> bySlug(String slug) {
        if (!Organization.isValidSlug(slug)) {
            return Optional.empty();
        }
        return database.instanceTransaction(
                tx -> tx.fetchOptional(ACTIVE + " AND slug = ?", slug).map(StoredOrganizationDirectory::organization));
    }

    @Override
    public List<Organization> all() {
        return database.instanceTransaction(
                tx -> tx.fetch(ACTIVE + " ORDER BY slug").map(StoredOrganizationDirectory::organization));
    }

    /**
     * The organization with {@code slug}, created if there's none; an existing one, whatever its
     * status, is left as it is. Its ID never changes, since storage paths and keys carry it.
     */
    public Organization ensure(String slug) {
        Organization created = new Organization(new OrganizationId(UUID.randomUUID()), slug);
        return database.instanceTransaction(tx -> {
            tx.execute(
                    "INSERT INTO organizations (id, slug, display_name) VALUES (?, ?, ?) ON CONFLICT (slug) DO NOTHING",
                    created.id().value(),
                    slug,
                    slug);
            return organization(tx.fetchSingle("SELECT id, slug FROM organizations WHERE slug = ?", slug));
        });
    }

    private static Organization organization(Record row) {
        return new Organization(new OrganizationId(row.get("id", UUID.class)), row.get("slug", String.class));
    }
}
