/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.organization;

import com.bulmerkiln.core.annotation.UntrustedInput;
import java.util.Objects;
import java.util.UUID;
import lombok.Value;

/** The immutable identifier of an organization, the {@code organization_id} on every organization table. */
@Value
public final class OrganizationId {

    private final UUID value;

    public OrganizationId(UUID value) {
        Objects.requireNonNull(value, "value");
        this.value = value;
    }

    @UntrustedInput
    public static OrganizationId of(String value) {
        return new OrganizationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
