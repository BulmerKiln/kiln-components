/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import java.util.UUID;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code namespace_claims}: a name a namespace claims in one format. */
@Value
@Table("namespace_claims")
public final class NamespaceClaimEntity {

    @Id
    private final NamespaceClaimKey id;

    private final UUID namespaceId;

    private final @Nullable String separator;
}
