/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.entity;

import java.util.UUID;
import lombok.Value;

/** A role binding's key: its organization and its own ID. */
@Value
public final class RoleBindingKey {

    private final UUID organizationId;

    private final UUID id;
}
