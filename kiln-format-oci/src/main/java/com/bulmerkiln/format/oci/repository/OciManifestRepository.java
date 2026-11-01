/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.format.oci.entity.OciManifestEntity;
import com.bulmerkiln.format.oci.entity.OciVersionKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code oci_manifests} table. A manifest goes when its version does. */
public interface OciManifestRepository extends KilnRepository<OciManifestEntity, OciVersionKey> {

    String COLUMNS = "organization_id, version_id, package_id, media_type, artifact_type, subject, file_id";

    /** Records the manifest of a version unless it's recorded; storing the same manifest again is harmless. */
    @Modifying
    @Query("INSERT INTO oci_manifests (organization_id, package_id, version_id, media_type, artifact_type, subject,"
            + " file_id) VALUES (:organizationId, :packageId, :versionId, :mediaType, :artifactType, :subject, :fileId)"
            + " ON CONFLICT DO NOTHING")
    int addIfAbsent(
            UUID organizationId,
            UUID packageId,
            UUID versionId,
            String mediaType,
            @Nullable String artifactType,
            @Nullable String subject,
            UUID fileId);

    @Query("SELECT " + COLUMNS + " FROM oci_manifests WHERE organization_id = :organizationId"
            + " AND version_id = :versionId")
    Optional<OciManifestEntity> findOne(UUID organizationId, UUID versionId);

    /** The manifests of a repository whose subject is {@code subject}, for the referrers API. */
    @Query("SELECT " + COLUMNS + " FROM oci_manifests WHERE organization_id = :organizationId"
            + " AND package_id = :packageId AND subject = :subject")
    List<OciManifestEntity> findBySubject(UUID organizationId, UUID packageId, String subject);

    /** Whether an index in the repository references the manifest {@code digest}. */
    @Query("SELECT EXISTS (SELECT 1 FROM oci_manifest_refs r JOIN oci_manifests m"
            + " ON m.organization_id = r.organization_id AND m.version_id = r.version_id"
            + " WHERE r.organization_id = :organizationId AND m.package_id = :packageId AND r.child = :digest"
            + " AND r.kind = 'manifest')")
    boolean referencedByIndex(UUID organizationId, UUID packageId, String digest);
}
