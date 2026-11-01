/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.domain;

import com.bulmerkiln.permissions.constant.Action;
import com.bulmerkiln.permissions.domain.Ids.MemberId;
import com.bulmerkiln.permissions.domain.Ids.ServiceAccountId;
import com.bulmerkiln.permissions.domain.Ids.TeamId;
import java.util.Objects;
import java.util.Set;
import lombok.Value;

/** Whoever acts, as authentication established. */
public sealed interface Principal {

    /** A person in this organization, with the teams their groups and explicit membership give. */
    @Value
    static final class Member implements Principal {

        private final MemberId id;

        private final Set<TeamId> teams;

        public Member(MemberId id, Set<TeamId> teams) {
            Objects.requireNonNull(id, "id");
            this.teams = Set.copyOf(teams);
            this.id = id;
        }
    }

    @Value
    static final class ServiceAccount implements Principal {

        private final ServiceAccountId id;

        public ServiceAccount(ServiceAccountId id) {
            Objects.requireNonNull(id, "id");
            this.id = id;
        }
    }

    /**
     * A CI job or Kubernetes workload matched by a trusted publisher. It holds exactly the grants
     * that publisher's rule gives, never a role.
     */
    @Value
    static final class Workload implements Principal {

        private final String publisher;

        private final Set<Grant> grants;

        public Workload(String publisher, Set<Grant> grants) {
            if (publisher.isBlank()) {
                throw new IllegalArgumentException("A workload names its trusted publisher");
            }
            this.grants = Set.copyOf(grants);
            this.publisher = publisher;
        }
    }

    /** No credentials: only public pulls. */
    @Value
    static final class Anonymous implements Principal {}

    /** One action on one scope, as a trusted publisher grants it. */
    @Value
    static final class Grant {

        private final Action action;

        private final Scope scope;

        public Grant(Action action, Scope scope) {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(scope, "scope");
            this.action = action;
            this.scope = scope;
        }
    }
}
