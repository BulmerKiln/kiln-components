/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.service;

import com.bulmerkiln.auth.connector.util.IdentityUtils;
import com.bulmerkiln.auth.token.OpaqueSecret;
import com.bulmerkiln.auth.user.dto.NewInvitationResponse;
import com.bulmerkiln.auth.user.store.InvitationStore;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.database.Database;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Invite links: the default way a person becomes a user (AUTH-21). A link is single-use, lasts 7
 * days, and only its token's SHA-256 is kept. The admin passes it on, so Kiln needs no mail server.
 * Who may invite is the admin API's decision.
 */
public final class InvitationService {

    public static final Duration LIFETIME = Duration.ofDays(7);

    private final Database database;
    private final Clock clock;

    public InvitationService(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    /**
     * Creates an invitation.
     *
     * @param email who it's for, as a label; the link is the proof, so it isn't checked at sign-in
     * @param organization the organization the new user joins, if any
     */
    public NewInvitationResponse invite(UUID createdBy, String email, @Nullable OrganizationId organization) {
        if (IdentityUtils.verifiedEmail(email, true) == null) {
            throw new IllegalArgumentException("Not an email address");
        }
        OpaqueSecret token = OpaqueSecret.generate();
        return database.instanceTransaction(tx -> new NewInvitationResponse(
                token,
                InvitationStore.create(
                        tx,
                        token.sha256(),
                        email,
                        organization == null ? null : organization.getValue(),
                        createdBy,
                        clock.instant().plus(LIFETIME))));
    }
}
