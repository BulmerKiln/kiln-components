/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.format.maven.entity.MavenPluginEntity;
import com.bulmerkiln.format.maven.entity.MavenPluginKey;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code maven_plugins} table. A plugin goes with its package. */
public interface MavenPluginRepository extends KilnRepository<MavenPluginEntity, MavenPluginKey> {

    /** Records or replaces the plugin entry of an artifact already deployed. */
    @Modifying
    @Query("INSERT INTO maven_plugins (organization_id, package_id, group_id, artifact_id, prefix, name)"
            + " VALUES (:organizationId, :packageId, :groupId, :artifactId, :prefix, :name)"
            + " ON CONFLICT (organization_id, package_id) DO UPDATE SET prefix = excluded.prefix, name = excluded.name")
    int save(
            UUID organizationId,
            UUID packageId,
            String groupId,
            String artifactId,
            String prefix,
            @Nullable String name);

    /** The plugins of a group, by artifactId. */
    @Query("SELECT organization_id, package_id, group_id, artifact_id, prefix, name FROM maven_plugins"
            + " WHERE organization_id = :organizationId AND group_id = :groupId ORDER BY artifact_id")
    List<MavenPluginEntity> findByGroup(UUID organizationId, String groupId);
}
