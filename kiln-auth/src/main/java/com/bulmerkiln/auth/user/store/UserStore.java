/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.store;

import com.bulmerkiln.auth.user.constant.UserStatus;
import com.bulmerkiln.auth.user.domain.BootstrapAdmins;
import com.bulmerkiln.auth.user.domain.IssuerSubject;
import com.bulmerkiln.auth.user.entity.UserEntity;
import com.bulmerkiln.auth.user.entity.UserIdentityEntity;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;

/** Users and their identities: instance tables, inside the caller's transaction. */
public final class UserStore {

    private static final String IDENTITY_COLUMNS =
            "id, user_id, connector_id, issuer, subject, claimed_email, last_login_at";

    private UserStore() {}

    public static UserEntity create(DSLContext tx, UUID id, String displayName, Instant now) {
        tx.execute(
                "INSERT INTO users (id, display_name, created_at) VALUES (?, ?, ?)",
                id,
                displayName,
                Timestamp.from(now));
        return new UserEntity(id, displayName, UserStatus.ACTIVE, now);
    }

    public static Optional<UserEntity> byId(DSLContext tx, UUID id) {
        return tx.fetchOptional("SELECT id, display_name, status, created_at FROM users WHERE id = ?", id)
                .map(UserStore::user);
    }

    public static void disable(DSLContext tx, UUID id) {
        tx.execute("UPDATE users SET status = 'disabled', disabled_at = now() WHERE id = ?", id);
    }

    public static UserIdentityEntity addIdentity(
            DSLContext tx,
            UUID userId,
            String connectorId,
            String issuer,
            String subject,
            @Nullable String claimedEmail,
            Instant now) {
        UUID id = UUID.randomUUID();
        tx.execute(
                "INSERT INTO user_identities (id, user_id, connector_id, issuer, subject, claimed_email,"
                        + " last_login_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                userId,
                connectorId,
                issuer,
                subject,
                claimedEmail,
                Timestamp.from(now),
                Timestamp.from(now));
        return new UserIdentityEntity(id, userId, connectorId, issuer, subject, claimedEmail, now);
    }

    /** The identity with this issuer and subject, locked until the transaction ends. */
    public static Optional<UserIdentityEntity> identity(DSLContext tx, String issuer, String subject) {
        return tx.fetchOptional(
                        "SELECT " + IDENTITY_COLUMNS + " FROM user_identities WHERE issuer = ? AND subject = ?"
                                + " FOR UPDATE",
                        issuer,
                        subject)
                .map(UserStore::identity);
    }

    public static List<UserIdentityEntity> identitiesOf(DSLContext tx, UUID userId) {
        return tx.fetch("SELECT " + IDENTITY_COLUMNS + " FROM user_identities WHERE user_id = ?", userId)
                .map(UserStore::identity);
    }

    /** The identities whose issuer and subject, or claimed email, boot config lists as an instance admin. */
    public static List<UserIdentityEntity> listedIdentities(DSLContext tx, BootstrapAdmins bootstrap) {
        List<UserIdentityEntity> listed = new ArrayList<>();
        for (IssuerSubject subject : bootstrap.getSubjects()) {
            identity(tx, subject.getIssuer(), subject.getSubject()).ifPresent(listed::add);
        }
        if (!bootstrap.getEmails().isEmpty()) {
            listed.addAll(tx.fetch(
                            "SELECT " + IDENTITY_COLUMNS + " FROM user_identities"
                                    + " WHERE lower(claimed_email) = ANY (?)",
                            (Object) bootstrap.getEmails().toArray(String[]::new))
                    .map(UserStore::identity));
        }
        return listed;
    }

    /** Whether this email, in any case, has already created a user. */
    public static boolean emailClaimed(DSLContext tx, String email) {
        return tx.fetchExists(tx.selectOne()
                .from("user_identities")
                .where("lower(claimed_email) = ?", email.toLowerCase(Locale.ROOT)));
    }

    public static void touch(DSLContext tx, UUID identityId, Instant now) {
        tx.execute("UPDATE user_identities SET last_login_at = ? WHERE id = ?", Timestamp.from(now), identityId);
    }

    static UserEntity user(Record row) {
        return new UserEntity(
                row.get("id", UUID.class),
                row.get("display_name", String.class),
                UserStatus.from(row.get("status", String.class)),
                row.get("created_at", Instant.class));
    }

    static UserIdentityEntity identity(Record row) {
        return new UserIdentityEntity(
                row.get("id", UUID.class),
                row.get("user_id", UUID.class),
                row.get("connector_id", String.class),
                row.get("issuer", String.class),
                row.get("subject", String.class),
                row.get("claimed_email", String.class),
                row.get("last_login_at", Instant.class));
    }
}
