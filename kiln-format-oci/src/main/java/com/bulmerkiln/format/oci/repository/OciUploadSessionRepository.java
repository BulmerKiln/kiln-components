/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.format.oci.entity.OciUploadSessionEntity;
import com.bulmerkiln.registry.entity.RegistryKey;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code oci_upload_sessions} table: a session can only go on through the repository that started it. */
public interface OciUploadSessionRepository extends KilnRepository<OciUploadSessionEntity, RegistryKey> {

    @Modifying
    @Query("INSERT INTO oci_upload_sessions (organization_id, id, package_id)"
            + " VALUES (:organizationId, :id, :packageId)")
    int start(UUID organizationId, UUID id, UUID packageId);

    @Query("SELECT EXISTS (SELECT 1 FROM oci_upload_sessions WHERE organization_id = :organizationId AND id = :id"
            + " AND package_id = :packageId)")
    boolean belongs(UUID organizationId, UUID id, UUID packageId);

    @Modifying
    @Query("DELETE FROM oci_upload_sessions WHERE organization_id = :organizationId AND id = :id")
    int end(UUID organizationId, UUID id);
}
