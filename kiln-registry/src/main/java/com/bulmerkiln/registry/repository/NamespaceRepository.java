/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.registry.entity.NamespaceEntity;
import com.bulmerkiln.registry.entity.RegistryKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code namespaces} table, and the namespace owning a name through its claims (REG-1). */
public interface NamespaceRepository extends KilnRepository<NamespaceEntity, RegistryKey> {

    String COLUMNS = "n.organization_id, n.id, n.name, n.visibility";

    @Modifying
    @Query("INSERT INTO namespaces (organization_id, id, name, visibility) VALUES (:organizationId, :id, :name,"
            + " :visibility)")
    int add(UUID organizationId, UUID id, String name, String visibility);

    @Query("SELECT " + COLUMNS + " FROM namespaces n WHERE n.organization_id = :organizationId AND n.name = :name")
    Optional<NamespaceEntity> findByName(UUID organizationId, String name);

    @Query("SELECT " + COLUMNS + " FROM namespaces n WHERE n.organization_id = :organizationId AND n.id = :id")
    Optional<NamespaceEntity> findOne(UUID organizationId, UUID id);

    /**
     * The namespace whose claim in {@code format} matches {@code name}: the claim exactly, or the
     * claim followed by its separator (any continuation when it has none). The longest claim wins.
     */
    @Query("SELECT " + COLUMNS + " FROM namespace_claims c JOIN namespaces n"
            + " ON n.organization_id = c.organization_id AND n.id = c.namespace_id"
            + " WHERE c.organization_id = :organizationId AND c.format = :format"
            + " AND (c.value = :name OR starts_with(:name, c.value || coalesce(c.separator, '')))"
            + " ORDER BY length(c.value) DESC LIMIT 1")
    Optional<NamespaceEntity> findOwner(UUID organizationId, String format, String name);
}
