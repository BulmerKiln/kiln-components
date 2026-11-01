/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.format.oci.entity.OciManifestRefEntity;
import com.bulmerkiln.format.oci.entity.OciManifestRefKey;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code oci_manifest_refs} table: what each manifest references. */
public interface OciManifestRefRepository extends KilnRepository<OciManifestRefEntity, OciManifestRefKey> {

    @Modifying
    @Query("INSERT INTO oci_manifest_refs (organization_id, version_id, child, kind)"
            + " VALUES (:organizationId, :versionId, :child, :kind) ON CONFLICT DO NOTHING")
    int addIfAbsent(UUID organizationId, UUID versionId, String child, String kind);
}
