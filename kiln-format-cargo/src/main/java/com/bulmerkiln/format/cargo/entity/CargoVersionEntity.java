/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.entity;

import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code cargo_versions}: the index line, the publish metadata as received, and the yank. */
@Value
@Table("cargo_versions")
public final class CargoVersionEntity {

    @Id
    private final CargoVersionKey id;

    private final String indexEntry;

    private final String publishMetadata;

    private final boolean yanked;
}
