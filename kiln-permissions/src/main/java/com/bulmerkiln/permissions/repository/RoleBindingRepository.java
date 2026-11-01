/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.permissions.entity.RoleBindingEntity;
import com.bulmerkiln.permissions.entity.RoleBindingKey;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code role_bindings} table: an organization table. */
public interface RoleBindingRepository extends KilnRepository<RoleBindingEntity, RoleBindingKey> {

    @Query("SELECT organization_id, id, subject_kind, subject_id, role, namespace, repository FROM role_bindings"
            + " WHERE organization_id = :organizationId AND subject_id IN (:subjects)")
    List<RoleBindingEntity> findBySubjects(UUID organizationId, Collection<UUID> subjects);

    /** Adds the binding unless the same one is held; a held one is left as it is. */
    @Modifying
    @Query("INSERT INTO role_bindings (organization_id, id, subject_kind, subject_id, role, namespace, repository)"
            + " VALUES (:organizationId, :id, :subjectKind, :subjectId, :role, :namespace, :repository)"
            + " ON CONFLICT DO NOTHING")
    int addIfAbsent(
            UUID organizationId,
            UUID id,
            String subjectKind,
            UUID subjectId,
            String role,
            @Nullable String namespace,
            @Nullable String repository);
}
