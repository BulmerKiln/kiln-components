/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * One sign-in, an issuer and subject through a connector, belonging to one user.
 */
@Value
public final class UserIdentityEntity {

    private final UUID id;

    private final UUID userId;

    private final String connectorId;

    private final String issuer;

    private final String subject;

    /**
     * The verified email that created the user, through an allowed domain or a bootstrap email; none
     * for an invitation, a bootstrap subject or a linked identity.
     */
    private final @Nullable String claimedEmail;

    private final @Nullable Instant lastLoginAt;
}
