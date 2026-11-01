/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.domain;

import java.util.Objects;
import java.util.UUID;
import lombok.Value;

/** Typed IDs, so a team can never be passed where a member is meant. */
public final class Ids {

    private Ids() {}

    @Value
    public static final class MemberId {

        private final UUID value;

        public MemberId(UUID value) {
            Objects.requireNonNull(value, "value");
            this.value = value;
        }
    }

    @Value
    public static final class TeamId {

        private final UUID value;

        public TeamId(UUID value) {
            Objects.requireNonNull(value, "value");
            this.value = value;
        }
    }

    @Value
    public static final class ServiceAccountId {

        private final UUID value;

        public ServiceAccountId(UUID value) {
            Objects.requireNonNull(value, "value");
            this.value = value;
        }
    }
}
