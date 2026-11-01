/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.service;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationDirectory;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.mapper.OrganizationMapper;
import com.bulmerkiln.database.repository.OrganizationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The organizations in the {@code organizations} table (DB-9). Only active ones are found: a
 * suspended or deleting organization looks like one that doesn't exist.
 */
public final class StoredOrganizationDirectory implements OrganizationDirectory {

    private final Database database;
    private final OrganizationRepository organizations;
    private final OrganizationMapper mapper;

    public StoredOrganizationDirectory(
            Database database, OrganizationRepository organizations, OrganizationMapper mapper) {
        this.database = database;
        this.organizations = organizations;
        this.mapper = mapper;
    }

    @Override
    public Optional<Organization> byId(OrganizationId id) {
        return database.instanceTransaction(
                () -> organizations.findActiveById(id.getValue()).map(mapper::toOrganization));
    }

    @Override
    public Optional<Organization> bySlug(String slug) {
        if (!Organization.isValidSlug(slug)) {
            return Optional.empty();
        }
        return database.instanceTransaction(
                () -> organizations.findActiveBySlug(slug).map(mapper::toOrganization));
    }

    @Override
    public List<Organization> all() {
        return database.instanceTransaction(() -> organizations.findAllActive().stream()
                .map(mapper::toOrganization)
                .toList());
    }

    /**
     * The organization with {@code slug}, created if there's none; an existing one, whatever its
     * status, is left as it is. Its ID never changes, since storage paths and keys carry it.
     */
    public Organization ensure(String slug) {
        return database.instanceTransaction(() -> {
            organizations.createIfAbsent(UUID.randomUUID(), slug);
            return mapper.toOrganization(organizations.findBySlug(slug).orElseThrow());
        });
    }
}
