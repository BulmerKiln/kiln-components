/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.service;

import com.bulmerkiln.auth.connector.domain.ExternalIdentity;
import com.bulmerkiln.auth.user.constant.LinkOutcome;
import com.bulmerkiln.auth.user.entity.UserIdentityEntity;
import com.bulmerkiln.auth.user.store.UserStore;
import com.bulmerkiln.database.Database;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Links a further identity to a signed-in user, such as Google beside the company's SSO (AUTH-22).
 * Only explicitly: the user's session must have signed in within 10 minutes, and the identity
 * arrives through a fresh sign-in at its own connector. Never by email or name, and an identity
 * another user holds is never moved.
 */
public final class IdentityLinkService {

    public static final Duration RECENT_SIGN_IN = Duration.ofMinutes(10);

    private final Database database;
    private final Clock clock;

    public IdentityLinkService(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    /**
     * Links {@code identity} to {@code userId}.
     *
     * @param authTime when the session doing the linking last signed in
     */
    public LinkOutcome link(UUID userId, Instant authTime, ExternalIdentity identity) {
        Instant now = clock.instant();
        if (authTime.plus(RECENT_SIGN_IN).isBefore(now)) {
            return LinkOutcome.SIGN_IN_TOO_OLD;
        }
        return database.instanceTransaction(tx -> {
            if (!UserStore.byId(tx, userId).map(user -> user.active()).orElse(false)) {
                return LinkOutcome.USER_DISABLED;
            }
            Optional<UserIdentityEntity> held = UserStore.identity(tx, identity.getIssuer(), identity.getSubject());
            if (held.isPresent()) {
                return held.get().getUserId().equals(userId)
                        ? LinkOutcome.ALREADY_LINKED
                        : LinkOutcome.HELD_BY_ANOTHER_USER;
            }
            UserStore.addIdentity(
                    tx,
                    userId,
                    identity.getConnector().getValue(),
                    identity.getIssuer(),
                    identity.getSubject(),
                    null,
                    now);
            return LinkOutcome.LINKED;
        });
    }
}
