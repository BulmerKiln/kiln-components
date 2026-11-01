/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code packages}. */
@Value
@Table("packages")
public final class PackageEntity {

    @Id
    private final RegistryKey id;

    private final UUID namespaceId;

    private final String format;

    private final String name;

    private final String normalizedName;

    private final Instant updatedAt;
}
