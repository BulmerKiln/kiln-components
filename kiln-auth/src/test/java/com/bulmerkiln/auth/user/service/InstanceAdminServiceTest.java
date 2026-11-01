/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.auth.connector.domain.ConnectorId;
import com.bulmerkiln.auth.connector.domain.ExternalIdentity;
import com.bulmerkiln.auth.user.domain.BootstrapAdmins;
import com.bulmerkiln.auth.user.domain.IssuerSubject;
import com.bulmerkiln.auth.user.dto.SignedInResponse;
import com.bulmerkiln.auth.user.store.UserStore;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs against a real PostgreSQL with the three roles, as Kiln's runtime role. */
class InstanceAdminServiceTest {

    private static final String ISSUER = "https://dex.example.com";
    private static final BootstrapAdmins ROOT =
            BootstrapAdmins.of(List.of(), List.of(new IssuerSubject(ISSUER, "root")));

    private UserDatabase db;
    private UUID root;

    @BeforeEach
    void setUp() {
        db = new UserDatabase();
        InstanceAdminService admins = new InstanceAdminService(db.database(), ROOT);
        var signedIn = new SignInService(db.database(), db.organizations(), admins, db.clock())
                .signIn(new ExternalIdentity(new ConnectorId("dex"), ISSUER, "root", null, Set.of()), null);
        root = ((SignedInResponse) signedIn).getUser().getId();
    }

    @AfterEach
    void close() {
        db.close();
    }

    /** AUTH-24: removing a bootstrap entry revokes its grant on the next start. */
    @Test
    void reconcileRevokesRemovedBootstrapGrants() {
        new InstanceAdminService(db.database(), BootstrapAdmins.NONE).reconcile();

        assertThat(new InstanceAdminService(db.database(), ROOT).isInstanceAdmin(root))
                .isFalse();
    }

    /** AUTH-24: a start with the entry still listed keeps the grant, and restores a revoked one. */
    @Test
    void reconcileKeepsAndRestoresListedGrants() {
        InstanceAdminService admins = new InstanceAdminService(db.database(), ROOT);
        admins.reconcile();
        assertThat(admins.isInstanceAdmin(root)).isTrue();

        new InstanceAdminService(db.database(), BootstrapAdmins.NONE).reconcile();
        admins.reconcile();
        assertThat(admins.isInstanceAdmin(root)).isTrue();
    }

    /** AUTH-24: an instance admin can grant instance admin; reconciling the config never revokes it. */
    @Test
    void adminGrantsSurviveReconcile() {
        UUID alice = user();
        InstanceAdminService admins = new InstanceAdminService(db.database(), ROOT);

        assertThat(admins.grant(root, alice)).isTrue();
        new InstanceAdminService(db.database(), BootstrapAdmins.NONE).reconcile();

        assertThat(admins.isInstanceAdmin(alice)).isTrue();
    }

    /** AUTH-24: someone who isn't an instance admin can't grant it, not even to themselves. */
    @Test
    void onlyInstanceAdminsGrant() {
        UUID alice = user();
        UUID bob = user();
        InstanceAdminService admins = new InstanceAdminService(db.database(), ROOT);

        assertThat(admins.grant(alice, bob)).isFalse();
        assertThat(admins.grant(alice, alice)).isFalse();
        assertThat(admins.isInstanceAdmin(bob)).isFalse();
        assertThat(admins.isInstanceAdmin(alice)).isFalse();
    }

    /** AUTH-24: a disabled user is no instance admin, grant or not. */
    @Test
    void disabledAdminIsNoAdmin() {
        db.database().instanceTransaction(tx -> {
            UserStore.disable(tx, root);
            return Boolean.TRUE;
        });
        InstanceAdminService admins = new InstanceAdminService(db.database(), ROOT);

        assertThat(admins.isInstanceAdmin(root)).isFalse();
        assertThat(admins.grant(root, user())).isFalse();
    }

    private UUID user() {
        return db.database()
                .instanceTransaction(tx -> UserStore.create(
                                tx, UUID.randomUUID(), "someone", db.clock().instant())
                        .getId());
    }
}
