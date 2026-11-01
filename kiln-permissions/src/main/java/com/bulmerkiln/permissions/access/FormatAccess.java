/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.access;

import com.bulmerkiln.core.lookup.Lookup;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.constant.Access;
import com.bulmerkiln.format.constant.Operation;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.permissions.CurrentPrincipal;
import com.bulmerkiln.permissions.constant.Action;
import com.bulmerkiln.permissions.constant.Decision;
import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.permissions.domain.Resource;
import com.bulmerkiln.permissions.domain.Scope;
import com.bulmerkiln.permissions.policy.PermissionPolicy;

/** Answers the format subsystem's {@link ArtifactAccess} port for the current principal. */
public final class FormatAccess implements ArtifactAccess {

    private final PermissionPolicy policy;
    private final Lookup<Scope, Visibility> visibility;

    /** {@code visibility} says whether a namespace or repository is public; unknown ones are private. */
    public FormatAccess(PermissionPolicy policy, Lookup<Scope, Visibility> visibility) {
        this.policy = policy;
        this.visibility = visibility;
    }

    @Override
    public Access check(ArtifactRef artifact, Operation operation) {
        Scope scope = Scope.repository(artifact.getNamespace(), artifact.getName());
        Action action = switch (operation) {
            case PULL -> Action.PULL;
            case PUSH -> Action.PUSH;
            case DELETE -> Action.DELETE;
        };
        Decision decision = policy.decide(CurrentPrincipal.get(), action, new Resource(scope, visibility.of(scope)));
        return switch (decision) {
            case ALLOW -> Access.ALLOW;
            case AUTHENTICATE -> Access.AUTHENTICATE;
            case DENY -> Access.DENY;
            case HIDE -> Access.HIDE;
        };
    }
}
