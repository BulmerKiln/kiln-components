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
 * An invite link, as stored: its token's hash stays in the table.
 */
@Value
public final class InvitationEntity {

    private final UUID id;

    /** The admin's label for who it's for; the link itself is the proof. */
    private final String email;

    /** The organization the user joins on claiming it, if any. */
    private final @Nullable UUID organizationId;

    private final UUID createdBy;

    private final Instant expiresAt;

    private final @Nullable UUID claimedBy;
}
