/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.store;

import com.bulmerkiln.auth.user.constant.UserStatus;
import com.bulmerkiln.auth.user.entity.MemberEntity;
import com.bulmerkiln.core.organization.OrganizationContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;

/** Members of the current organization, inside the caller's transaction. */
public final class MemberStore {

    private static final String COLUMNS = "user_id, status, created_at";

    private MemberStore() {}

    /** Makes the user a member, active, unless they already are one; an existing row is left as it is. */
    public static void add(DSLContext tx, UUID userId, Instant now) {
        tx.execute(
                "INSERT INTO members (organization_id, user_id, created_at) VALUES (?, ?, ?)"
                        + " ON CONFLICT DO NOTHING",
                organization(),
                userId,
                Timestamp.from(now));
    }

    public static Optional<MemberEntity> byUser(DSLContext tx, UUID userId) {
        return tx.fetchOptional(
                        "SELECT " + COLUMNS + " FROM members WHERE organization_id = ? AND user_id = ?",
                        organization(),
                        userId)
                .map(MemberStore::member);
    }

    public static List<MemberEntity> all(DSLContext tx) {
        return tx.fetch("SELECT " + COLUMNS + " FROM members WHERE organization_id = ?", organization())
                .map(MemberStore::member);
    }

    public static void disable(DSLContext tx, UUID userId) {
        tx.execute(
                "UPDATE members SET status = 'disabled', disabled_at = now() WHERE organization_id = ? AND user_id = ?",
                organization(),
                userId);
    }

    static MemberEntity member(Record row) {
        return new MemberEntity(
                organization(),
                row.get("user_id", UUID.class),
                UserStatus.from(row.get("status", String.class)),
                row.get("created_at", Instant.class));
    }

    private static UUID organization() {
        return OrganizationContext.current().getId().getValue();
    }
}
