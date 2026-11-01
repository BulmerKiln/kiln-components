/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.format.oci.entity.OciRepositoryBlobEntity;
import com.bulmerkiln.format.oci.entity.OciRepositoryBlobKey;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code oci_repository_blobs} table: which repositories may read a blob. */
public interface OciRepositoryBlobRepository extends KilnRepository<OciRepositoryBlobEntity, OciRepositoryBlobKey> {

    /** Lets the repository read a blob the organization holds; linking twice is harmless. */
    @Modifying
    @Query("INSERT INTO oci_repository_blobs (organization_id, package_id, digest)"
            + " VALUES (:organizationId, :packageId, :digest) ON CONFLICT DO NOTHING")
    int link(UUID organizationId, UUID packageId, String digest);

    @Query("SELECT EXISTS (SELECT 1 FROM oci_repository_blobs WHERE organization_id = :organizationId"
            + " AND package_id = :packageId AND digest = :digest)")
    boolean linked(UUID organizationId, UUID packageId, String digest);
}
