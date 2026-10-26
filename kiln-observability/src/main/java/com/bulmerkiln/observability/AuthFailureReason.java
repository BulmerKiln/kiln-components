/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.observability;

import java.util.Locale;

/** Why a credential was refused: the {@code reason} label of {@code kiln_auth_failures_total}. */
public enum AuthFailureReason {
    INVALID,
    EXPIRED,
    REVOKED,
    REPLAYED,
    WRONG_AUDIENCE,
    UNTRUSTED_ISSUER,
    RATE_LIMITED;

    String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
