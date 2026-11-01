/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.policy;

import com.bulmerkiln.core.lookup.Lookup;
import com.bulmerkiln.permissions.constant.Action;
import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.permissions.domain.Resource;
import com.bulmerkiln.permissions.domain.RoleBinding;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** The role-based policy (PERM-1 to PERM-7): roles from bindings, trusted-publisher grants, and public pulls. */
public final class RoleBasedPolicy implements PermissionPolicy {

    private final Lookup<Principal, List<RoleBinding>> bindings;

    /**
     * {@code bindings} gives the stored bindings that may apply to a principal, in the current
     * organization. Extra ones are harmless: each is rechecked against the principal (PERM-8).
     */
    public RoleBasedPolicy(Lookup<Principal, List<RoleBinding>> bindings) {
        this.bindings = bindings;
    }

    @Override
    public Set<Action> allowed(Principal principal, Resource resource) {
        Set<Action> allowed = EnumSet.noneOf(Action.class);
        if (resource.getVisibility() == Visibility.PUBLIC) {
            allowed.add(Action.PULL);
        }
        if (principal instanceof Principal.Workload workload) {
            for (Principal.Grant grant : workload.getGrants()) {
                if (grant.getScope().covers(resource.getScope())) {
                    allowed.add(grant.getAction());
                }
            }
            return Set.copyOf(allowed);
        }
        for (RoleBinding binding : bindings.of(principal)) {
            // The repository may return more than applies; only bindings for this principal count.
            if (binding.appliesTo(principal) && binding.getScope().covers(resource.getScope())) {
                allowed.addAll(binding.getRole().actions());
            }
        }
        return Set.copyOf(allowed);
    }
}
