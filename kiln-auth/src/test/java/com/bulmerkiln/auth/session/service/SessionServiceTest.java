/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.session.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.auth.session.dto.NewSessionResponse;
import com.bulmerkiln.auth.user.service.UserDatabase;
import com.bulmerkiln.auth.user.store.UserStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs against a real PostgreSQL with the three roles, as Kiln's runtime role. */
class SessionServiceTest {

    private UserDatabase db;
    private SessionService sessions;
    private UUID alice;

    @BeforeEach
    void setUp() {
        db = new UserDatabase();
        sessions = new SessionService(db.database(), db.clock());
        alice = db.database()
                .instanceTransaction(tx -> UserStore.create(
                                tx, UUID.randomUUID(), "alice", db.clock().instant())
                        .getId());
    }

    @AfterEach
    void close() {
        db.close();
    }

    /** AUTH-26: a session is found by its ID, and the table holds only the ID's SHA-256. */
    @Test
    void storesOnlyTheHash() {
        NewSessionResponse started = sessions.start(alice);

        assertThat(sessions.find(started.getId().reveal())).contains(started.getSession());
        byte[] stored = db.database()
                .instanceTransaction(
                        tx -> tx.fetchSingle("SELECT id_hash FROM web_sessions").get(0, byte[].class));
        assertThat(stored)
                .isEqualTo(started.getId().sha256())
                .isNotEqualTo(started.getId().reveal().getBytes(StandardCharsets.US_ASCII));
    }

    /** AUTH-26: each sign-in gets a new ID. */
    @Test
    void eachStartIsANewSession() {
        assertThat(sessions.start(alice).getId().reveal())
                .isNotEqualTo(sessions.start(alice).getId().reveal());
    }

    /** AUTH-26: a session lasts 12 hours from sign-in, however active, and is then removed. */
    @Test
    void expiresAfterTwelveHours() {
        NewSessionResponse started = sessions.start(alice);
        db.clock().advance(Duration.ofHours(11));
        assertThat(sessions.find(started.getId().reveal())).isPresent();

        db.clock().advance(Duration.ofHours(1));
        assertThat(sessions.find(started.getId().reveal())).isEmpty();
        assertThat(sessions.removeExpired()).isEqualTo(1);
        assertThat(db.count("web_sessions")).isZero();
    }

    /** AUTH-26: a disabled user's sessions stop working at once. */
    @Test
    void disabledUserHasNoSession() {
        NewSessionResponse started = sessions.start(alice);
        db.database().instanceTransaction(tx -> {
            UserStore.disable(tx, alice);
            return Boolean.TRUE;
        });

        assertThat(sessions.find(started.getId().reveal())).isEmpty();
    }

    /** AUTH-26: ending a session, or all of a user's, removes them. */
    @Test
    void endRemovesSessions() {
        NewSessionResponse first = sessions.start(alice);
        NewSessionResponse second = sessions.start(alice);
        sessions.end(first.getId().reveal());
        assertThat(sessions.find(first.getId().reveal())).isEmpty();
        assertThat(sessions.find(second.getId().reveal())).isPresent();

        sessions.endAll(alice);
        assertThat(sessions.find(second.getId().reveal())).isEmpty();
    }

    /** AUTH-26: malformed and unknown IDs find nothing, without a lookup error. */
    @Test
    void malformedIdsFindNothing() {
        sessions.start(alice);
        for (String presented : new String[] {"", "x", "A".repeat(43), "A".repeat(44), "../" + "A".repeat(40)}) {
            assertThat(sessions.find(presented)).as(presented).isEmpty();
            sessions.end(presented);
        }
        assertThat(db.count("web_sessions")).isEqualTo(1);
    }
}
