/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

import com.bulmerkiln.permissions.Ids.MemberId;
import com.bulmerkiln.permissions.Ids.ServiceAccountId;
import com.bulmerkiln.permissions.Ids.TeamId;
import java.util.Objects;
import java.util.Set;

/** Whoever acts, as authentication established. */
public sealed interface Principal {

    /** A person in this organization, with the teams their groups and explicit membership give. */
    record Member(MemberId id, Set<TeamId> teams) implements Principal {
        public Member {
            Objects.requireNonNull(id, "id");
            teams = Set.copyOf(teams);
        }
    }

    record ServiceAccount(ServiceAccountId id) implements Principal {
        public ServiceAccount {
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * A CI job or Kubernetes workload matched by a trusted publisher. It holds exactly the grants
     * that publisher's rule gives, never a role.
     */
    record Workload(String publisher, Set<Grant> grants) implements Principal {
        public Workload {
            if (publisher.isBlank()) {
                throw new IllegalArgumentException("A workload names its trusted publisher");
            }
            grants = Set.copyOf(grants);
        }
    }

    /** No credentials: only public pulls. */
    record Anonymous() implements Principal {}

    /** One action on one scope, as a trusted publisher grants it. */
    record Grant(Action action, Scope scope) {
        public Grant {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(scope, "scope");
        }
    }
}
