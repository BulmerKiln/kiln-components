/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.registry.entity.PackageVersionEntity;
import com.bulmerkiln.registry.entity.RegistryKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code package_versions} table; versions are found by their normalized form. */
public interface PackageVersionRepository extends KilnRepository<PackageVersionEntity, RegistryKey> {

    String COLUMNS = "organization_id, id, package_id, version, published_at, published_by_kind, published_by_id";

    /** Oldest first; formats order versions their own way when they need to. */
    @Query("SELECT " + COLUMNS + " FROM package_versions WHERE organization_id = :organizationId"
            + " AND package_id = :packageId ORDER BY published_at, version")
    List<PackageVersionEntity> findAll(UUID organizationId, UUID packageId);

    @Query("SELECT " + COLUMNS + " FROM package_versions WHERE organization_id = :organizationId"
            + " AND package_id = :packageId AND normalized_version = :normalizedVersion")
    Optional<PackageVersionEntity> findByVersion(UUID organizationId, UUID packageId, String normalizedVersion);

    @Query("SELECT " + COLUMNS + " FROM package_versions WHERE organization_id = :organizationId AND id = :id")
    Optional<PackageVersionEntity> findOne(UUID organizationId, UUID id);

    /** Adds the version unless it exists; the caller decides whether that's a conflict (REG-3). */
    @Modifying
    @Query("INSERT INTO package_versions (organization_id, id, package_id, version, normalized_version,"
            + " published_by_kind, published_by_id) VALUES (:organizationId, :id, :packageId, :version,"
            + " :normalizedVersion, :publishedByKind, :publishedById)"
            + " ON CONFLICT (organization_id, package_id, normalized_version) DO NOTHING")
    int addIfAbsent(
            UUID organizationId,
            UUID id,
            UUID packageId,
            String version,
            String normalizedVersion,
            @Nullable String publishedByKind,
            @Nullable UUID publishedById);

    /** Removes a version; its files and every format's detail rows go with it (REG-8). */
    @Modifying
    @Query("DELETE FROM package_versions WHERE organization_id = :organizationId AND id = :id")
    int remove(UUID organizationId, UUID id);
}
