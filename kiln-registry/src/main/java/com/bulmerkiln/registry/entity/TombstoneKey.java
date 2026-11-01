/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import java.util.UUID;
import lombok.Value;

/** A tombstone is its whole key: a version or tag of a name, reserved forever. */
@Value
public final class TombstoneKey {

    private final UUID organizationId;

    private final String format;

    private final String name;

    private final String kind;

    private final String value;
}
