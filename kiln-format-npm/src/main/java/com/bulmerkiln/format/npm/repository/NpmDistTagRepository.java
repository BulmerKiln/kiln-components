/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.format.npm.entity.NpmDistTagEntity;
import com.bulmerkiln.format.npm.entity.NpmDistTagKey;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code npm_dist_tags} table. A tag goes with the version it points at. */
public interface NpmDistTagRepository extends KilnRepository<NpmDistTagEntity, NpmDistTagKey> {

    @Query("SELECT organization_id, package_id, tag, version_id FROM npm_dist_tags"
            + " WHERE organization_id = :organizationId AND package_id = :packageId")
    List<NpmDistTagEntity> findAll(UUID organizationId, UUID packageId);

    /** Points a tag at a version of its package, moving it if it pointed elsewhere. */
    @Modifying
    @Query("INSERT INTO npm_dist_tags (organization_id, package_id, tag, version_id)"
            + " VALUES (:organizationId, :packageId, :tag, :versionId)"
            + " ON CONFLICT (organization_id, package_id, tag) DO UPDATE SET version_id = excluded.version_id")
    int set(UUID organizationId, UUID packageId, String tag, UUID versionId);

    @Modifying
    @Query("DELETE FROM npm_dist_tags WHERE organization_id = :organizationId AND package_id = :packageId"
            + " AND tag = :tag")
    int remove(UUID organizationId, UUID packageId, String tag);
}
