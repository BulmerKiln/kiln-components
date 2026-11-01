/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.Value;

@Value
public final class PackageVersionEntity {

    private final UUID id;

    private final String version;

    private final String metadata;

    private final boolean yanked;

    private final Instant publishedAt;
}
