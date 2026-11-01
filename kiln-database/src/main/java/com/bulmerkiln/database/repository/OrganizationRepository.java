/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.repository;

import com.bulmerkiln.database.entity.OrganizationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code organizations} table: an instance table. Only active organizations are ever found. */
public interface OrganizationRepository extends KilnRepository<OrganizationEntity, UUID> {

    @Query("SELECT id, slug FROM organizations WHERE status = 'active' AND id = :id")
    Optional<OrganizationEntity> findActiveById(UUID id);

    @Query("SELECT id, slug FROM organizations WHERE status = 'active' AND slug = :slug")
    Optional<OrganizationEntity> findActiveBySlug(String slug);

    @Query("SELECT id, slug FROM organizations WHERE status = 'active' ORDER BY slug")
    List<OrganizationEntity> findAllActive();

    /** Whatever its status, so an existing slug is never created again. */
    @Query("SELECT id, slug FROM organizations WHERE slug = :slug")
    Optional<OrganizationEntity> findBySlug(String slug);

    /** Creates the organization unless the slug exists; an existing one is left as it is. */
    @Modifying
    @Query("INSERT INTO organizations (id, slug, display_name) VALUES (:id, :slug, :slug)"
            + " ON CONFLICT (slug) DO NOTHING")
    int createIfAbsent(UUID id, String slug);
}
