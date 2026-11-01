/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.entity;

import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code npm_versions}: the version's document as published, with any deprecation since. */
@Value
@Table("npm_versions")
public final class NpmVersionEntity {

    @Id
    private final NpmVersionKey id;

    private final String document;
}
