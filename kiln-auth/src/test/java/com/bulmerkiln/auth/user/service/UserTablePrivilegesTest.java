/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.service;

import static com.bulmerkiln.auth.user.service.UserDatabase.ACME;
import static com.bulmerkiln.auth.user.service.UserDatabase.GLOBEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.auth.session.service.SessionService;
import com.bulmerkiln.auth.user.constant.InstanceAdminSource;
import com.bulmerkiln.auth.user.store.InstanceAdminStore;
import com.bulmerkiln.auth.user.store.MemberStore;
import com.bulmerkiln.auth.user.store.UserStore;
import com.bulmerkiln.core.organization.OrganizationContext;
import java.util.UUID;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tries, as Kiln's runtime role, every change the application never makes, the way a bug or an
 * injection would, and expects the database to refuse each one.
 */
class UserTablePrivilegesTest {

    private UserDatabase db;
    private UUID alice;
    private UUID bob;
    private UUID invitation;

    @BeforeEach
    void setUp() {
        db = new UserDatabase();
        alice = user("alice");
        bob = user("bob");
        db.database().instanceTransaction(tx -> {
            UserStore.addIdentity(
                    tx,
                    alice,
                    "dex",
                    "https://dex.example.com",
                    "alice",
                    null,
                    db.clock().instant());
            InstanceAdminStore.grant(tx, alice, InstanceAdminSource.BOOTSTRAP, null);
            return Boolean.TRUE;
        });
        OrganizationContext.run(
                ACME,
                () -> db.database()
                        .run(tx -> MemberStore.add(tx, alice, db.clock().instant())));
        invitation = new InvitationService(db.database(), db.clock())
                .invite(alice, "carol@example.com", ACME.getId())
                .getInvitation()
                .getId();
        new SessionService(db.database(), db.clock()).start(alice);
    }

    @AfterEach
    void close() {
        db.close();
    }

    /** AUTH-27: an invitation's organization, email, token, creator and expiry can't change. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "organization_id = NULL",
                "email = 'mallory@example.com'",
                "token_hash = token_hash",
                "created_by = created_by",
                "expires_at = expires_at + interval '1 year'"
            })
    void invitationsAreFixed(String change) {
        assertRefused("UPDATE invitations SET " + change);
    }

    /** AUTH-27: an invitation is claimed once; the claim can't be redone or moved to someone else. */
    @Test
    void invitationsAreClaimedOnce() {
        String claim = "UPDATE invitations SET claimed_by = '%s', claimed_at = now() WHERE id = '" + invitation + "'";
        db.database().instanceTransaction(tx -> tx.execute(claim.formatted(bob)));

        assertRefused(claim.formatted(alice));
    }

    /** AUTH-27: an identity can't move to another user, or change what it names. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "user_id = '%s'",
                "issuer = 'https://evil.example.com'",
                "subject = 'root'",
                "claimed_email = 'root@example.com'"
            })
    void identitiesAreFixed(String change) {
        assertRefused("UPDATE user_identities SET " + change.formatted(bob));
    }

    /** AUTH-27: an instance admin grant can't change hands or kind; it's only granted or revoked. */
    @ParameterizedTest
    @ValueSource(strings = {"user_id = '%s'", "source = 'admin', granted_by = '%s'"})
    void instanceAdminGrantsAreFixed(String change) {
        assertRefused("UPDATE instance_admins SET " + change.formatted(bob, bob));
    }

    /** AUTH-27: a session can't move to another user or be extended. */
    @ParameterizedTest
    @ValueSource(strings = {"user_id = '%s'", "expires_at = expires_at + interval '1 year'", "auth_time = now()"})
    void sessionsAreFixed(String change) {
        assertRefused("UPDATE web_sessions SET " + change.formatted(bob));
    }

    /** AUTH-27: a user's ID can't change; only their name and status can. */
    @Test
    void usersKeepTheirId() {
        assertRefused("UPDATE users SET id = gen_random_uuid() WHERE id = '" + bob + "'");
        db.database().instanceTransaction(tx -> tx.execute("UPDATE users SET display_name = 'Bob' WHERE id = ?", bob));
    }

    /** AUTH-27: a membership can be disabled, never moved to another user or organization. */
    @Test
    void membershipsAreFixed() {
        for (String change : new String[] {"user_id = '" + bob + "'", "organization_id = '" + GLOBEX.getId() + "'"}) {
            assertThatThrownBy(() -> OrganizationContext.run(
                            ACME, () -> db.database().run(tx -> tx.execute("UPDATE members SET " + change))))
                    .as(change)
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("permission denied");
        }
        OrganizationContext.run(ACME, () -> db.database().run(tx -> MemberStore.disable(tx, alice)));
        assertThat(OrganizationContext.call(
                        ACME, () -> new MemberService(db.database(), db.organizations()).activeMember(alice)))
                .isEmpty();
    }

    private void assertRefused(String sql) {
        assertThatThrownBy(() -> db.database().instanceTransaction(tx -> tx.execute(sql)))
                .as(sql)
                .isInstanceOf(DataAccessException.class)
                .satisfies(e -> assertThat(e.getMessage()).containsAnyOf("permission denied", "already claimed"));
    }

    private UUID user(String name) {
        return db.database()
                .instanceTransaction(tx -> UserStore.create(
                                tx, UUID.randomUUID(), name, db.clock().instant())
                        .getId());
    }
}
