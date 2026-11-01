/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.session.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.Value;

/**
 * One user's browser session. The ID itself is never stored, only its hash.
 */
@Value
public final class WebSessionEntity {

    private final UUID userId;

    /** When the user last signed in through a connector, for actions that need a recent sign-in. */
    private final Instant authTime;

    /** Absolute: 12 hours from sign-in, however active the session is. */
    private final Instant expiresAt;

    public boolean expired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
