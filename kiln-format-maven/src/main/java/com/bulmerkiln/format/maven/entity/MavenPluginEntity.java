/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.entity;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code maven_plugins}: a plugin's goal prefix, for its group's metadata (FMT-MVN-17). */
@Value
@Table("maven_plugins")
public final class MavenPluginEntity {

    @Id
    private final MavenPluginKey id;

    private final String groupId;

    private final String artifactId;

    private final String prefix;

    private final @Nullable String name;
}
