/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.format.oci.entity.OciTagEntity;
import com.bulmerkiln.format.oci.entity.OciTagKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code oci_tags} table. A tag goes when its manifest does. */
public interface OciTagRepository extends KilnRepository<OciTagEntity, OciTagKey> {

    /** Points a tag at a manifest, moving it if it pointed elsewhere. */
    @Modifying
    @Query("INSERT INTO oci_tags (organization_id, package_id, name, version_id)"
            + " VALUES (:organizationId, :packageId, :name, :versionId)"
            + " ON CONFLICT (organization_id, package_id, name)"
            + " DO UPDATE SET version_id = excluded.version_id, updated_at = now()")
    int set(UUID organizationId, UUID packageId, String name, UUID versionId);

    /** The digest of the manifest a tag points at. */
    @Query("SELECT v.version FROM oci_tags t JOIN package_versions v"
            + " ON v.organization_id = t.organization_id AND v.id = t.version_id"
            + " WHERE t.organization_id = :organizationId AND t.package_id = :packageId AND t.name = :name")
    Optional<String> findDigest(UUID organizationId, UUID packageId, String name);

    @Modifying
    @Query("DELETE FROM oci_tags WHERE organization_id = :organizationId AND package_id = :packageId AND name = :name")
    int remove(UUID organizationId, UUID packageId, String name);

    /** Up to {@code limit} tags after {@code last}, in lexical order. */
    @Query("SELECT name FROM oci_tags WHERE organization_id = :organizationId AND package_id = :packageId"
            + " AND name > :last ORDER BY name COLLATE \"C\" LIMIT :limit")
    List<String> findNames(UUID organizationId, UUID packageId, String last, int limit);
}
