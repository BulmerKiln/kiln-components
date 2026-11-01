/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.registry.entity.PackageFileEntity;
import com.bulmerkiln.registry.entity.RegistryKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code package_files} table. */
public interface PackageFileRepository extends KilnRepository<PackageFileEntity, RegistryKey> {

    String COLUMNS = "organization_id, id, version_id, name, digest, size";

    @Query("SELECT " + COLUMNS + " FROM package_files WHERE organization_id = :organizationId"
            + " AND version_id = :versionId AND name = :name")
    Optional<PackageFileEntity> findByName(UUID organizationId, UUID versionId, String name);

    @Query("SELECT " + COLUMNS + " FROM package_files WHERE organization_id = :organizationId AND id = :id")
    Optional<PackageFileEntity> findOne(UUID organizationId, UUID id);

    @Query("SELECT " + COLUMNS + " FROM package_files WHERE organization_id = :organizationId"
            + " AND version_id = :versionId ORDER BY name")
    List<PackageFileEntity> findAll(UUID organizationId, UUID versionId);

    /** Adds the file unless its name is taken in the version; the caller compares the content. */
    @Modifying
    @Query("INSERT INTO package_files (organization_id, id, version_id, name, digest, size)"
            + " VALUES (:organizationId, :id, :versionId, :name, :digest, :size)"
            + " ON CONFLICT (organization_id, version_id, name) DO NOTHING")
    int addIfAbsent(UUID organizationId, UUID id, UUID versionId, String name, String digest, long size);
}
