/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.registry.entity.BlockEntity;
import com.bulmerkiln.registry.entity.RegistryKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code blocks} table. A block is lifted, never deleted, so nothing here deletes one. */
public interface BlockRepository extends KilnRepository<BlockEntity, RegistryKey> {

    /** Adds a block unless the same target already has an active one. */
    @Modifying
    @Query("INSERT INTO blocks (organization_id, id, format, name, selector_kind, selector, reason, source,"
            + " created_by_kind, created_by_id) VALUES (:#{#block.id.organizationId}, :#{#block.id.id},"
            + " :#{#block.format}, :#{#block.name}, :#{#block.selectorKind}, :#{#block.selector},"
            + " :#{#block.reason}, :#{#block.source}, :#{#block.createdByKind}, :#{#block.createdById})"
            + " ON CONFLICT (organization_id, format, (coalesce(name, '')), selector_kind, selector)"
            + " WHERE lifted_at IS NULL DO NOTHING")
    int addIfAbsent(BlockEntity block);

    /**
     * Why {@code name} is blocked, if an active block covers the whole package or {@code version}
     * exactly; a block on the whole package comes first.
     */
    @Query("SELECT reason FROM blocks WHERE organization_id = :organizationId AND format = :format AND name = :name"
            + " AND lifted_at IS NULL AND (selector_kind = 'package'"
            + " OR (selector_kind = 'version' AND selector = :version)) ORDER BY selector_kind LIMIT 1")
    Optional<String> findReason(UUID organizationId, String format, String name, String version);
}
