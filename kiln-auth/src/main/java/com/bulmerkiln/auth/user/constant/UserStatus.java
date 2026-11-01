/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.constant;

import java.util.Locale;

/** Whether a user, or a member, may act. A disabled one is refused everywhere it applies. */
public enum UserStatus {
    ACTIVE,
    DISABLED;

    public static UserStatus from(String stored) {
        return valueOf(stored.toUpperCase(Locale.ROOT));
    }

    public String stored() {
        return name().toLowerCase(Locale.ROOT);
    }
}
