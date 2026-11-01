/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.entity;

import com.bulmerkiln.auth.user.constant.UserStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Value;

/** A person, instance-wide. Membership of organizations is in {@link MemberEntity}. */
@Value
public final class UserEntity {

    private final UUID id;

    private final String displayName;

    private final UserStatus status;

    private final Instant createdAt;

    public boolean active() {
        return status == UserStatus.ACTIVE;
    }
}
