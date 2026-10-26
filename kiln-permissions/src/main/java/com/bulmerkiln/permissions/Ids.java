/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

import java.util.Objects;
import java.util.UUID;

/** Typed IDs, so a team can never be passed where a member is meant. */
public final class Ids {

    private Ids() {}

    public record MemberId(UUID value) {
        public MemberId {
            Objects.requireNonNull(value, "value");
        }
    }

    public record TeamId(UUID value) {
        public TeamId {
            Objects.requireNonNull(value, "value");
        }
    }

    public record ServiceAccountId(UUID value) {
        public ServiceAccountId {
            Objects.requireNonNull(value, "value");
        }
    }
}
