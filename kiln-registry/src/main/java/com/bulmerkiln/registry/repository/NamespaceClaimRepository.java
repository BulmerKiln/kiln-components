/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.repository;

import com.bulmerkiln.database.repository.KilnRepository;
import com.bulmerkiln.registry.entity.NamespaceClaimEntity;
import com.bulmerkiln.registry.entity.NamespaceClaimKey;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;

/** The {@code namespace_claims} table: a value is claimed once per format in an organization. */
public interface NamespaceClaimRepository extends KilnRepository<NamespaceClaimEntity, NamespaceClaimKey> {

    @Modifying
    @Query("INSERT INTO namespace_claims (organization_id, namespace_id, format, value, separator)"
            + " VALUES (:organizationId, :namespaceId, :format, :value, :separator)")
    int add(UUID organizationId, UUID namespaceId, String format, String value, @Nullable String separator);
}
