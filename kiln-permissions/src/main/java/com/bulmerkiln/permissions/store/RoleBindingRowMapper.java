/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.store;

import com.bulmerkiln.permissions.constant.Role;
import com.bulmerkiln.permissions.domain.Ids.MemberId;
import com.bulmerkiln.permissions.domain.Ids.ServiceAccountId;
import com.bulmerkiln.permissions.domain.Ids.TeamId;
import com.bulmerkiln.permissions.domain.RoleBinding;
import java.util.Locale;
import java.util.UUID;

/** How a role binding's subject and role are written in {@code role_bindings}. */
final class RoleBindingRowMapper {

    private RoleBindingRowMapper() {}

    static String kind(RoleBinding.Subject subject) {
        return switch (subject) {
            case RoleBinding.Subject.Team _ -> "team";
            case RoleBinding.Subject.Member _ -> "member";
            case RoleBinding.Subject.ServiceAccount _ -> "service_account";
        };
    }

    static UUID id(RoleBinding.Subject subject) {
        return switch (subject) {
            case RoleBinding.Subject.Team team -> team.id().value();
            case RoleBinding.Subject.Member member -> member.id().value();
            case RoleBinding.Subject.ServiceAccount account -> account.id().value();
        };
    }

    static RoleBinding.Subject subject(String kind, UUID id) {
        return switch (kind) {
            case "team" -> new RoleBinding.Subject.Team(new TeamId(id));
            case "member" -> new RoleBinding.Subject.Member(new MemberId(id));
            default -> new RoleBinding.Subject.ServiceAccount(new ServiceAccountId(id));
        };
    }

    static String role(Role role) {
        return role.name().toLowerCase(Locale.ROOT);
    }

    static Role role(String value) {
        return Role.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
