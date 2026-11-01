/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.store;

import com.bulmerkiln.auth.user.entity.InvitationEntity;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;

/** Invite links, kept as the SHA-256 of their tokens: an instance table, inside the caller's transaction. */
public final class InvitationStore {

    private static final String COLUMNS = "id, email, organization_id, created_by, expires_at, claimed_by";

    private InvitationStore() {}

    public static InvitationEntity create(
            DSLContext tx,
            byte[] tokenHash,
            String email,
            @Nullable UUID organizationId,
            UUID createdBy,
            Instant expiresAt) {
        UUID id = UUID.randomUUID();
        tx.execute(
                "INSERT INTO invitations (id, token_hash, email, organization_id, created_by, expires_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                id,
                tokenHash,
                email,
                organizationId,
                createdBy,
                Timestamp.from(expiresAt));
        return new InvitationEntity(id, email, organizationId, createdBy, expiresAt, null);
    }

    /** The unclaimed, unexpired invitation with this token hash, locked until the transaction ends. */
    public static Optional<InvitationEntity> claimable(DSLContext tx, byte[] tokenHash, Instant now) {
        return tx.fetchOptional(
                        "SELECT " + COLUMNS + " FROM invitations"
                                + " WHERE token_hash = ? AND claimed_by IS NULL AND expires_at > ? FOR UPDATE",
                        tokenHash,
                        Timestamp.from(now))
                .map(InvitationStore::invitation);
    }

    public static void markClaimed(DSLContext tx, UUID id, UUID userId, Instant now) {
        tx.execute(
                "UPDATE invitations SET claimed_by = ?, claimed_at = ? WHERE id = ? AND claimed_by IS NULL",
                userId,
                Timestamp.from(now),
                id);
    }

    static InvitationEntity invitation(Record row) {
        return new InvitationEntity(
                row.get("id", UUID.class),
                row.get("email", String.class),
                row.get("organization_id", UUID.class),
                row.get("created_by", UUID.class),
                row.get("expires_at", Instant.class),
                row.get("claimed_by", UUID.class));
    }
}
