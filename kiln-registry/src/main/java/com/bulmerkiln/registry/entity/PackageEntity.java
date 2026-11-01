/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import java.util.UUID;
import lombok.Value;

/** A package, by the name it was first published under. */
@Value
public final class PackageEntity {

    private final UUID id;

    private final UUID namespaceId;

    private final String name;

    private final String metadata;
}
