/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

import com.bulmerkiln.permissions.Ids.MemberId;
import com.bulmerkiln.permissions.Ids.ServiceAccountId;
import com.bulmerkiln.permissions.Ids.TeamId;
import java.util.Objects;

/** A role granted to a team, a member or a service account, at a scope. */
public record RoleBinding(Subject subject, Role role, Scope scope) {

    public RoleBinding {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(scope, "scope");
    }

    /** Who a binding is for. Roles are granted to teams, members or service accounts only. */
    public sealed interface Subject {
        record Team(TeamId id) implements Subject {}

        record Member(MemberId id) implements Subject {}

        record ServiceAccount(ServiceAccountId id) implements Subject {}
    }

    /** Whether this binding applies to {@code principal}. */
    public boolean appliesTo(Principal principal) {
        return switch (principal) {
            case Principal.Member member ->
                switch (subject) {
                    case Subject.Member m -> m.id().equals(member.id());
                    case Subject.Team t -> member.teams().contains(t.id());
                    case Subject.ServiceAccount _ -> false;
                };
            case Principal.ServiceAccount account ->
                subject instanceof Subject.ServiceAccount s && s.id().equals(account.id());
            case Principal.Workload _ -> false;
            case Principal.Anonymous _ -> false;
        };
    }
}
