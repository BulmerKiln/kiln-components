/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.service;

import com.bulmerkiln.auth.user.constant.InstanceAdminSource;
import com.bulmerkiln.auth.user.domain.BootstrapAdmins;
import com.bulmerkiln.auth.user.entity.UserIdentityEntity;
import com.bulmerkiln.auth.user.store.AllowedDomainStore;
import com.bulmerkiln.auth.user.store.InstanceAdminStore;
import com.bulmerkiln.auth.user.store.UserStore;
import com.bulmerkiln.auth.user.util.EmailUtils;
import com.bulmerkiln.database.Database;
import java.util.UUID;
import org.jooq.DSLContext;

/**
 * Instance admins: grants in their own table, never a flag on the user (AUTH-24). Bootstrap
 * grants follow {@code kiln.bootstrap} and are reconciled with it on every start, so removing an
 * entry revokes the grant; other grants come from an instance admin.
 */
public final class InstanceAdminService {

    private final Database database;
    private final BootstrapAdmins bootstrap;

    public InstanceAdminService(Database database, BootstrapAdmins bootstrap) {
        this.database = database;
        this.bootstrap = bootstrap;
    }

    public boolean isInstanceAdmin(UUID userId) {
        return database.instanceTransaction(tx -> InstanceAdminStore.isAdmin(tx, userId)
                && UserStore.byId(tx, userId).map(user -> user.active()).orElse(false));
    }

    /**
     * Grants instance admin to {@code userId} on behalf of {@code grantedBy}.
     *
     * @return false, granting nothing, unless {@code grantedBy} is an active instance admin
     */
    public boolean grant(UUID grantedBy, UUID userId) {
        return database.instanceTransaction(tx -> {
            boolean granterIsAdmin = InstanceAdminStore.isAdmin(tx, grantedBy)
                    && UserStore.byId(tx, grantedBy).map(user -> user.active()).orElse(false);
            if (!granterIsAdmin || UserStore.byId(tx, userId).isEmpty()) {
                return false;
            }
            InstanceAdminStore.grant(tx, userId, InstanceAdminSource.ADMIN, grantedBy);
            return true;
        });
    }

    /** Whether boot config lists this issuer and subject; used before the user exists. */
    boolean listsSubject(String issuer, String subject) {
        return bootstrap.listsSubject(issuer, subject);
    }

    /** Whether boot config lists this email; used before the user exists. */
    boolean listsEmail(String email) {
        return bootstrap.listsEmail(email);
    }

    /** Grants bootstrap instance admin to the identity's user if boot config lists the identity. */
    void grantIfListed(DSLContext tx, UserIdentityEntity identity) {
        if (listed(tx, identity)) {
            InstanceAdminStore.grant(tx, identity.getUserId(), InstanceAdminSource.BOOTSTRAP, null);
        }
    }

    /**
     * Revokes bootstrap grants the config no longer lists, and grants them to existing users it
     * newly lists. Runs on every start.
     */
    public void reconcile() {
        database.instanceTransaction(tx -> {
            for (UUID userId : InstanceAdminStore.bootstrapped(tx)) {
                if (UserStore.identitiesOf(tx, userId).stream().noneMatch(identity -> listed(tx, identity))) {
                    InstanceAdminStore.revokeBootstrapped(tx, userId);
                }
            }
            for (UserIdentityEntity identity : UserStore.listedIdentities(tx, bootstrap)) {
                grantIfListed(tx, identity);
            }
            return Boolean.TRUE;
        });
    }

    /**
     * A listed subject, or a listed email that this identity claimed through a connector that
     * still allows its domain.
     */
    private boolean listed(DSLContext tx, UserIdentityEntity identity) {
        if (bootstrap.listsSubject(identity.getIssuer(), identity.getSubject())) {
            return true;
        }
        String email = identity.getClaimedEmail();
        return email != null
                && bootstrap.listsEmail(email)
                && AllowedDomainStore.find(tx, identity.getConnectorId(), EmailUtils.domainOf(email))
                        .isPresent();
    }
}
