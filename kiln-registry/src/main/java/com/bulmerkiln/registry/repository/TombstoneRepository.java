/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.registry.entity.TombstoneEntity;
import com.bulmerkiln.registry.entity.TombstoneKey;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code tombstones} table: rows are added, never removed. */
public interface TombstoneRepository extends KilnRepository<TombstoneEntity, TombstoneKey> {

    @Modifying
    @Query("INSERT INTO tombstones (organization_id, format, name, kind, value)"
            + " VALUES (:organizationId, :format, :name, :kind, :value) ON CONFLICT DO NOTHING")
    int addIfAbsent(UUID organizationId, String format, String name, String kind, String value);

    @Query("SELECT EXISTS (SELECT 1 FROM tombstones WHERE organization_id = :organizationId AND format = :format"
            + " AND name = :name AND kind = :kind AND value = :value)")
    boolean exists(UUID organizationId, String format, String name, String kind, String value);
}
