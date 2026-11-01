/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import java.util.UUID;
import lombok.Value;

/** The key of a registry row with an ID of its own: the organization first, as every key starts. */
@Value
public final class RegistryKey {

    private final UUID organizationId;

    private final UUID id;
}
