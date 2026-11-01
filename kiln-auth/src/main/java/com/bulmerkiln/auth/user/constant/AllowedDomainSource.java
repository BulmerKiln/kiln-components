/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.constant;

import java.util.Locale;

/** Where an allowed domain came from. Config rows follow {@code kiln.auth.allowed-domains} and are read-only. */
public enum AllowedDomainSource {
    CONFIG,
    ADMIN;

    public static AllowedDomainSource from(String stored) {
        return valueOf(stored.toUpperCase(Locale.ROOT));
    }

    public String stored() {
        return name().toLowerCase(Locale.ROOT);
    }
}
