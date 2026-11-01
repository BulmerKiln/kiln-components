/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.entity;

import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code namespaces}; {@code visibility} is {@code private} or {@code public}. */
@Value
@Table("namespaces")
public final class NamespaceEntity {

    @Id
    private final RegistryKey id;

    private final String name;

    private final String visibility;
}
