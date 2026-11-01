/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.entity;

import java.util.UUID;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A row of {@code role_bindings}: the subject and role as their column values, and the scope's names. */
@Value
@Table("role_bindings")
public final class RoleBindingEntity {

    @Id
    private final RoleBindingKey id;

    private final String subjectKind;

    private final UUID subjectId;

    private final String role;

    private final @Nullable String namespace;

    private final @Nullable String repository;
}
