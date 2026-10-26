/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

import java.util.Set;

/**
 * Decides what principals may do. Every implementation must pass {@code PermissionPolicyContract}.
 */
public interface PermissionPolicy {

    /** Everything {@code principal} may do to {@code resource}; tokens are scoped to a subset of it. */
    Set<Action> allowed(Principal principal, Resource resource);

    /**
     * The decision for one action. A principal who may do anything to the resource, or any member
     * at the organization level, may know it exists ({@code DENY}); anyone else signed in gets
     * {@code HIDE}, and anyone not signed in {@code AUTHENTICATE}.
     */
    default Decision decide(Principal principal, Action action, Resource resource) {
        Set<Action> allowed = allowed(principal, resource);
        if (allowed.contains(action)) {
            return Decision.ALLOW;
        }
        if (principal instanceof Principal.Anonymous) {
            return Decision.AUTHENTICATE;
        }
        boolean knowsIt =
                !allowed.isEmpty() || (resource.scope().isOrganization() && !(principal instanceof Principal.Workload));
        return knowsIt ? Decision.DENY : Decision.HIDE;
    }
}
