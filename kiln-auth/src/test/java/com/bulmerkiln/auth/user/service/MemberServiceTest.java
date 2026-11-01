/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.service;

import static com.bulmerkiln.auth.user.service.UserDatabase.ACME;
import static com.bulmerkiln.auth.user.service.UserDatabase.GLOBEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.auth.user.store.MemberStore;
import com.bulmerkiln.auth.user.store.UserStore;
import com.bulmerkiln.core.organization.OrganizationContext;
import java.util.UUID;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs against a real PostgreSQL with the three roles, as Kiln's runtime role. */
class MemberServiceTest {

    private UserDatabase db;
    private MemberService members;
    private UUID alice;

    @BeforeEach
    void setUp() {
        db = new UserDatabase();
        members = new MemberService(db.database(), db.organizations());
        alice = db.database()
                .instanceTransaction(tx -> UserStore.create(
                                tx, UUID.randomUUID(), "alice", db.clock().instant())
                        .getId());
        OrganizationContext.run(
                ACME,
                () -> db.database()
                        .run(tx -> MemberStore.add(tx, alice, db.clock().instant())));
    }

    @AfterEach
    void close() {
        db.close();
    }

    /** AUTH-25: a user is a member only where they were added. */
    @Test
    void memberOnlyWhereAdded() {
        assertThat(OrganizationContext.call(ACME, () -> members.activeMember(alice)))
                .isPresent();
        assertThat(OrganizationContext.call(GLOBEX, () -> members.activeMember(alice)))
                .isEmpty();
        assertThat(members.organizationsOf(alice)).containsExactly(ACME);
    }

    /** AUTH-25: a disabled member is no member there, and only there. */
    @Test
    void disabledMemberIsNoMember() {
        OrganizationContext.run(ACME, () -> db.database().run(tx -> MemberStore.disable(tx, alice)));

        assertThat(OrganizationContext.call(ACME, () -> members.activeMember(alice)))
                .isEmpty();
        assertThat(members.organizationsOf(alice)).isEmpty();
    }

    /** AUTH-25: a disabled user is no member anywhere. */
    @Test
    void disabledUserIsNoMember() {
        db.database().instanceTransaction(tx -> {
            UserStore.disable(tx, alice);
            return Boolean.TRUE;
        });

        assertThat(OrganizationContext.call(ACME, () -> members.activeMember(alice)))
                .isEmpty();
    }

    /** AUTH-25: members are an organization table: without an organization, the database refuses to read them. */
    @Test
    void membersNeedAnOrganization() {
        assertThatThrownBy(() -> members.activeMember(alice)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> db.count("members")).isInstanceOf(DataAccessException.class);
    }
}
