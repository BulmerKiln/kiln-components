/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code package_versions}; the publisher is a member or service account, or no one. */
@Value
@Table("package_versions")
public final class PackageVersionEntity {

    @Id
    private final RegistryKey id;

    private final UUID packageId;

    private final String version;

    private final Instant publishedAt;

    private final @Nullable String publishedByKind;

    private final @Nullable UUID publishedById;
}
