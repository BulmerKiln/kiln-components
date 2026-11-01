/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.entity;

import java.util.UUID;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code npm_dist_tags}: a tag and the version of its package it points at. */
@Value
@Table("npm_dist_tags")
public final class NpmDistTagEntity {

    @Id
    private final NpmDistTagKey id;

    private final UUID versionId;
}
