/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.entity;

import com.bulmerkiln.auth.user.constant.UserStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Value;

/** A user in one organization. What they may do there comes from role bindings. */
@Value
public final class MemberEntity {

    private final UUID organizationId;

    private final UUID userId;

    private final UserStatus status;

    private final Instant createdAt;

    public boolean active() {
        return status == UserStatus.ACTIVE;
    }
}
