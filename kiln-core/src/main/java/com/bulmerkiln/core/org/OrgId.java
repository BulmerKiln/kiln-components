/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.org;

import java.util.Objects;
import java.util.UUID;

/** The immutable identifier of an organization, the {@code org_id} on every organization table. */
public record OrgId(UUID value) {

    public OrgId {
        Objects.requireNonNull(value, "value");
    }

    public static OrgId of(String value) {
        return new OrgId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
