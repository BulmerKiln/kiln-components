/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.session.service;

import com.bulmerkiln.auth.session.dto.NewSessionResponse;
import com.bulmerkiln.auth.session.entity.WebSessionEntity;
import com.bulmerkiln.auth.session.store.SessionStore;
import com.bulmerkiln.auth.token.OpaqueSecret;
import com.bulmerkiln.auth.user.store.UserStore;
import com.bulmerkiln.database.Database;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Web sessions, kept as the SHA-256 of their IDs (AUTH-26). A session lasts 12 hours from sign-in
 * however active it is, belongs to one user, and stops working when that user is disabled.
 */
public final class SessionService {

    public static final Duration LIFETIME = Duration.ofHours(12);

    private final Database database;
    private final Clock clock;

    public SessionService(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    /** Starts a session for a user who just signed in. Each sign-in gets a new ID. */
    public NewSessionResponse start(UUID userId) {
        Instant now = clock.instant();
        OpaqueSecret id = OpaqueSecret.generate();
        WebSessionEntity session = new WebSessionEntity(userId, now, now.plus(LIFETIME));
        database.instanceTransaction(tx -> {
            SessionStore.create(tx, id.sha256(), session);
            return Boolean.TRUE;
        });
        return new NewSessionResponse(id, session);
    }

    /** The live session with this ID: empty if the ID is malformed, unknown or expired, or its user is disabled. */
    public Optional<WebSessionEntity> find(String presented) {
        Optional<OpaqueSecret> id = OpaqueSecret.parse(presented);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return database.instanceTransaction(tx -> SessionStore.find(tx, id.get().sha256())
                .filter(session -> !session.expired(now))
                .filter(session -> UserStore.byId(tx, session.getUserId())
                        .map(user -> user.active())
                        .orElse(false)));
    }

    /** Ends the session with this ID, if there is one. */
    public void end(String presented) {
        OpaqueSecret.parse(presented)
                .ifPresent(id -> database.instanceTransaction(tx -> {
                    SessionStore.delete(tx, id.sha256());
                    return Boolean.TRUE;
                }));
    }

    /** Ends every session the user has, such as when they're disabled. */
    public void endAll(UUID userId) {
        database.instanceTransaction(tx -> {
            SessionStore.deleteForUser(tx, userId);
            return Boolean.TRUE;
        });
    }

    /** Removes expired sessions, returning how many. */
    public int removeExpired() {
        Instant now = clock.instant();
        return database.instanceTransaction(tx -> SessionStore.deleteExpired(tx, now));
    }
}
