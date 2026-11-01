/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.format.cargo.entity.CargoVersionEntity;
import com.bulmerkiln.format.cargo.entity.CargoVersionKey;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code cargo_versions} table. A version's row goes with the version. */
public interface CargoVersionRepository extends KilnRepository<CargoVersionEntity, CargoVersionKey> {

    @Modifying
    @Query("INSERT INTO cargo_versions (organization_id, version_id, index_entry, publish_metadata)"
            + " VALUES (:organizationId, :versionId, :indexEntry, :publishMetadata)")
    int add(UUID organizationId, UUID versionId, String indexEntry, String publishMetadata);

    /** Yanks or unyanks a version: the only thing about a published crate that changes. */
    @Modifying
    @Query("UPDATE cargo_versions SET yanked = :yanked WHERE organization_id = :organizationId"
            + " AND version_id = :versionId")
    int setYanked(UUID organizationId, UUID versionId, boolean yanked);

    @Query("SELECT c.organization_id, c.version_id, c.index_entry, c.publish_metadata, c.yanked FROM cargo_versions c"
            + " JOIN package_versions v ON v.organization_id = c.organization_id AND v.id = c.version_id"
            + " WHERE c.organization_id = :organizationId AND v.package_id = :packageId")
    List<CargoVersionEntity> findAll(UUID organizationId, UUID packageId);
}
