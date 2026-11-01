/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.registry.entity.BlobEntity;
import com.bulmerkiln.registry.entity.BlobKey;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code blobs} table. */
public interface BlobRepository extends KilnRepository<BlobEntity, BlobKey> {

    /**
     * Records a blob, or marks one already recorded as referenced again, keeping the checksums it
     * has and adding any it lacks (REG-2).
     */
    @Modifying
    @Query("INSERT INTO blobs (organization_id, digest, size, md5, sha1, sha256, sha512)"
            + " VALUES (:organizationId, :digest, :size, :md5, :sha1, :sha256, :sha512)"
            + " ON CONFLICT (organization_id, digest) DO UPDATE SET last_referenced_at = now(),"
            + " md5 = coalesce(blobs.md5, excluded.md5), sha1 = coalesce(blobs.sha1, excluded.sha1),"
            + " sha256 = coalesce(blobs.sha256, excluded.sha256), sha512 = coalesce(blobs.sha512, excluded.sha512)")
    int record(
            UUID organizationId,
            String digest,
            long size,
            @Nullable String md5,
            @Nullable String sha1,
            @Nullable String sha256,
            @Nullable String sha512);

    @Query("SELECT organization_id, digest, size, md5, sha1, sha256, sha512 FROM blobs"
            + " WHERE organization_id = :organizationId AND digest = :digest")
    Optional<BlobEntity> findOne(UUID organizationId, String digest);
}
