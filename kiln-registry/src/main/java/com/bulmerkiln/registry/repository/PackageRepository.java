/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.registry.entity.PackageEntity;
import com.bulmerkiln.registry.entity.RegistryKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code packages} table. */
public interface PackageRepository extends KilnRepository<PackageEntity, RegistryKey> {

    @Query("SELECT organization_id, id, namespace_id, format, name, normalized_name, updated_at FROM packages"
            + " WHERE organization_id = :organizationId AND format = :format AND normalized_name = :normalizedName")
    Optional<PackageEntity> findByName(UUID organizationId, String format, String normalizedName);

    /** Creates the package unless its normalized name exists; two first publishes share one. */
    @Modifying
    @Query("INSERT INTO packages (organization_id, id, namespace_id, format, name, normalized_name)"
            + " VALUES (:organizationId, :id, :namespaceId, :format, :name, :normalizedName)"
            + " ON CONFLICT (organization_id, format, normalized_name) DO NOTHING")
    int addIfAbsent(UUID organizationId, UUID id, UUID namespaceId, String format, String name, String normalizedName);

    /** Marks a change a format shows, so it reports the package as modified now. */
    @Modifying
    @Query("UPDATE packages SET updated_at = now() WHERE organization_id = :organizationId AND id = :id")
    int touch(UUID organizationId, UUID id);

    @Modifying
    @Query("UPDATE packages SET updated_at = now() WHERE organization_id = :organizationId AND id ="
            + " (SELECT package_id FROM package_versions WHERE organization_id = :organizationId AND id = :versionId)")
    int touchForVersion(UUID organizationId, UUID versionId);

    @Modifying
    @Query("DELETE FROM packages WHERE organization_id = :organizationId AND id = :id")
    int remove(UUID organizationId, UUID id);
}
