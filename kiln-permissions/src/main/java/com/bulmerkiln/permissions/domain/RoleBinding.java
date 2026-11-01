/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.domain;

import com.bulmerkiln.permissions.constant.Role;
import com.bulmerkiln.permissions.domain.Ids.MemberId;
import com.bulmerkiln.permissions.domain.Ids.ServiceAccountId;
import com.bulmerkiln.permissions.domain.Ids.TeamId;
import java.util.Objects;
import lombok.Value;

/** A role granted to a team, a member or a service account, at a scope. */
@Value
public final class RoleBinding {

    private final Subject subject;

    private final Role role;

    private final Scope scope;

    public RoleBinding(Subject subject, Role role, Scope scope) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(scope, "scope");
        this.subject = subject;
        this.role = role;
        this.scope = scope;
    }

    /** Who a binding is for. Roles are granted to teams, members or service accounts only. */
    public sealed interface Subject {
        @Value
        static final class Team implements Subject {

            private final TeamId id;
        }

        @Value
        static final class Member implements Subject {

            private final MemberId id;
        }

        @Value
        static final class ServiceAccount implements Subject {

            private final ServiceAccountId id;
        }
    }

    /** Whether this binding applies to {@code principal}. */
    public boolean appliesTo(Principal principal) {
        return switch (principal) {
            case Principal.Member member ->
                switch (subject) {
                    case Subject.Member m -> m.getId().equals(member.getId());
                    case Subject.Team t -> member.getTeams().contains(t.getId());
                    case Subject.ServiceAccount _ -> false;
                };
            case Principal.ServiceAccount account ->
                subject instanceof Subject.ServiceAccount s && s.getId().equals(account.getId());
            case Principal.Workload _ -> false;
            case Principal.Anonymous _ -> false;
        };
    }
}
