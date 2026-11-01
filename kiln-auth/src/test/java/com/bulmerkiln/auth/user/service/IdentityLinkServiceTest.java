/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.auth.connector.domain.ConnectorId;
import com.bulmerkiln.auth.connector.domain.ExternalIdentity;
import com.bulmerkiln.auth.user.constant.LinkOutcome;
import com.bulmerkiln.auth.user.entity.UserIdentityEntity;
import com.bulmerkiln.auth.user.store.UserStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs against a real PostgreSQL with the three roles, as Kiln's runtime role. */
class IdentityLinkServiceTest {

    private static final ExternalIdentity GITHUB =
            new ExternalIdentity(new ConnectorId("github"), "https://github.com", "octocat", "o@example.com", Set.of());

    private UserDatabase db;
    private IdentityLinkService links;
    private UUID alice;

    @BeforeEach
    void setUp() {
        db = new UserDatabase();
        links = new IdentityLinkService(db.database(), db.clock());
        alice = user();
    }

    @AfterEach
    void close() {
        db.close();
    }

    /** AUTH-22: a session that signed in within 10 minutes links a further identity. */
    @Test
    void recentSignInLinks() {
        Instant authTime = db.clock().instant();
        db.clock().advance(Duration.ofMinutes(9));

        assertThat(links.link(alice, authTime, GITHUB)).isEqualTo(LinkOutcome.LINKED);
        assertThat(links.link(alice, authTime, GITHUB)).isEqualTo(LinkOutcome.ALREADY_LINKED);
        assertThat(identitiesOfAlice())
                .singleElement()
                .satisfies(identity -> assertThat(identity.getClaimedEmail()).isNull());
    }

    /** AUTH-22: an older sign-in must be repeated before linking. */
    @Test
    void oldSignInMustBeRepeated() {
        Instant authTime = db.clock().instant();
        db.clock().advance(Duration.ofMinutes(11));

        assertThat(links.link(alice, authTime, GITHUB)).isEqualTo(LinkOutcome.SIGN_IN_TOO_OLD);
        assertThat(db.count("user_identities")).isZero();
    }

    /** AUTH-22: an identity another user holds is never moved. */
    @Test
    void identityOfAnotherUserIsNeverMoved() {
        UUID bob = user();
        Instant authTime = db.clock().instant();
        links.link(bob, authTime, GITHUB);

        assertThat(links.link(alice, authTime, GITHUB)).isEqualTo(LinkOutcome.HELD_BY_ANOTHER_USER);
        assertThat(identitiesOfAlice()).isEmpty();
    }

    /** AUTH-22: a disabled user links nothing. */
    @Test
    void disabledUserLinksNothing() {
        db.database().instanceTransaction(tx -> {
            UserStore.disable(tx, alice);
            return Boolean.TRUE;
        });

        assertThat(links.link(alice, db.clock().instant(), GITHUB)).isEqualTo(LinkOutcome.USER_DISABLED);
    }

    private List<UserIdentityEntity> identitiesOfAlice() {
        return db.database().instanceTransaction(tx -> UserStore.identitiesOf(tx, alice));
    }

    private UUID user() {
        return db.database()
                .instanceTransaction(tx -> UserStore.create(
                                tx, UUID.randomUUID(), "someone", db.clock().instant())
                        .getId());
    }
}
