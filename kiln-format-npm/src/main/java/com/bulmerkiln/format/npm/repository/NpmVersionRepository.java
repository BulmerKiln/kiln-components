/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.format.npm.entity.NpmVersionEntity;
import com.bulmerkiln.format.npm.entity.NpmVersionKey;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code npm_versions} table. A version's document goes with the version. */
public interface NpmVersionRepository extends KilnRepository<NpmVersionEntity, NpmVersionKey> {

    @Modifying
    @Query("INSERT INTO npm_versions (organization_id, version_id, document)"
            + " VALUES (:organizationId, :versionId, :document)")
    int add(UUID organizationId, UUID versionId, String document);

    /** Replaces a document, for a deprecation: the one change npm makes to a published version. */
    @Modifying
    @Query("UPDATE npm_versions SET document = :document WHERE organization_id = :organizationId"
            + " AND version_id = :versionId")
    int setDocument(UUID organizationId, UUID versionId, String document);

    @Query("SELECT d.organization_id, d.version_id, d.document FROM npm_versions d JOIN package_versions v"
            + " ON v.organization_id = d.organization_id AND v.id = d.version_id"
            + " WHERE d.organization_id = :organizationId AND v.package_id = :packageId")
    List<NpmVersionEntity> findAll(UUID organizationId, UUID packageId);
}
