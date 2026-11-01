/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.service;

import com.bulmerkiln.auth.user.entity.MemberEntity;
import com.bulmerkiln.auth.user.store.MemberStore;
import com.bulmerkiln.auth.user.store.UserStore;
import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.core.organization.OrganizationDirectory;
import com.bulmerkiln.database.Database;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Who a user is in each organization (AUTH-25). */
public final class MemberService {

    private final Database database;
    private final OrganizationDirectory organizations;

    public MemberService(Database database, OrganizationDirectory organizations) {
        this.database = database;
        this.organizations = organizations;
    }

    /**
     * The user as a member of the current organization: empty when they aren't one, or when the
     * user or the member is disabled. Callers answer empty as if the organization didn't exist.
     */
    public Optional<MemberEntity> activeMember(UUID userId) {
        return database.transaction(
                tx -> UserStore.byId(tx, userId).filter(user -> user.active()).isPresent()
                        ? MemberStore.byUser(tx, userId).filter(MemberEntity::active)
                        : Optional.empty());
    }

    /**
     * The organizations where the user is an active member, for the organization switcher. Each is
     * checked in its own transaction, as row-level security requires.
     */
    public List<Organization> organizationsOf(UUID userId) {
        return organizations.all().stream()
                .filter(organization -> OrganizationContext.call(organization, () -> activeMember(userId))
                        .isPresent())
                .toList();
    }
}
