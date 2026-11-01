/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.entity;

import java.util.UUID;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code organizations}, as far as the directory reads it. */
@Value
@Table("organizations")
public final class OrganizationEntity {

    @Id
    private final UUID id;

    private final String slug;
}
