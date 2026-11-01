/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.session.store;

import com.bulmerkiln.auth.session.entity.WebSessionEntity;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/** Web sessions, keyed by the SHA-256 of their IDs: an instance table, inside the caller's transaction. */
public final class SessionStore {

    private SessionStore() {}

    public static void create(DSLContext tx, byte[] idHash, WebSessionEntity session) {
        tx.execute(
                "INSERT INTO web_sessions (id_hash, user_id, auth_time, expires_at) VALUES (?, ?, ?, ?)",
                idHash,
                session.getUserId(),
                Timestamp.from(session.getAuthTime()),
                Timestamp.from(session.getExpiresAt()));
    }

    public static Optional<WebSessionEntity> find(DSLContext tx, byte[] idHash) {
        return tx.fetchOptional("SELECT user_id, auth_time, expires_at FROM web_sessions WHERE id_hash = ?", idHash)
                .map(row -> new WebSessionEntity(
                        row.get("user_id", UUID.class),
                        row.get("auth_time", Instant.class),
                        row.get("expires_at", Instant.class)));
    }

    public static void delete(DSLContext tx, byte[] idHash) {
        tx.execute("DELETE FROM web_sessions WHERE id_hash = ?", idHash);
    }

    public static void deleteForUser(DSLContext tx, UUID userId) {
        tx.execute("DELETE FROM web_sessions WHERE user_id = ?", userId);
    }

    /** Removes every session that has expired, returning how many. */
    public static int deleteExpired(DSLContext tx, Instant now) {
        return tx.execute("DELETE FROM web_sessions WHERE expires_at <= ?", Timestamp.from(now));
    }
}
