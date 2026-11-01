/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code tombstones}. */
@Value
@Table("tombstones")
public final class TombstoneEntity {

    @Id
    private final TombstoneKey id;
}
