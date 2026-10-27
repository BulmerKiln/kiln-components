/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.organization;

import java.util.Objects;
import java.util.UUID;

/** The immutable identifier of an organization, the {@code organization_id} on every organization table. */
public record OrganizationId(UUID value) {

    public OrganizationId {
        Objects.requireNonNull(value, "value");
    }

    public static OrganizationId of(String value) {
        return new OrganizationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
