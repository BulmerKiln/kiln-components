/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

import java.util.EnumSet;
import java.util.Set;

/** The role-based policy (PERM-1 to PERM-7): roles from bindings, trusted-publisher grants, and public pulls. */
public final class RoleBasedPolicy implements PermissionPolicy {

    private final RoleBindingRepository bindings;

    public RoleBasedPolicy(RoleBindingRepository bindings) {
        this.bindings = bindings;
    }

    @Override
    public Set<Action> allowed(Principal principal, Resource resource) {
        Set<Action> allowed = EnumSet.noneOf(Action.class);
        if (resource.visibility() == Visibility.PUBLIC) {
            allowed.add(Action.PULL);
        }
        if (principal instanceof Principal.Workload workload) {
            for (Principal.Grant grant : workload.grants()) {
                if (grant.scope().covers(resource.scope())) {
                    allowed.add(grant.action());
                }
            }
            return Set.copyOf(allowed);
        }
        for (RoleBinding binding : bindings.bindingsFor(principal)) {
            // The repository may return more than applies; only bindings for this principal count.
            if (binding.appliesTo(principal) && binding.scope().covers(resource.scope())) {
                allowed.addAll(binding.role().actions());
            }
        }
        return Set.copyOf(allowed);
    }
}
