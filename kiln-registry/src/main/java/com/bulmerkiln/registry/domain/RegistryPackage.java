/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Value;

/** A package, by the name it was first published under; {@code updatedAt} is its last change. */
@Value
public final class RegistryPackage {

    private final UUID id;

    private final UUID namespaceId;

    private final String name;

    private final String normalizedName;

    private final Instant updatedAt;
}
