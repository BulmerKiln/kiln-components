/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.constant;

import java.util.Locale;

/** Where an instance admin grant came from. */
public enum InstanceAdminSource {
    /** {@code kiln.bootstrap}: reconciled with the config on every start, so removing it revokes the grant. */
    BOOTSTRAP,
    /** Granted by another instance admin. */
    ADMIN;

    public static InstanceAdminSource from(String stored) {
        return valueOf(stored.toUpperCase(Locale.ROOT));
    }

    public String stored() {
        return name().toLowerCase(Locale.ROOT);
    }
}
