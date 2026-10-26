/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

import com.bulmerkiln.format.Access;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.format.ArtifactRef;
import com.bulmerkiln.format.Operation;

/** Answers the format subsystem's {@link ArtifactAccess} port for the current principal. */
public final class FormatAccess implements ArtifactAccess {

    private final PermissionPolicy policy;
    private final VisibilityLookup visibility;

    public FormatAccess(PermissionPolicy policy, VisibilityLookup visibility) {
        this.policy = policy;
        this.visibility = visibility;
    }

    @Override
    public Access check(ArtifactRef artifact, Operation operation) {
        Scope scope = Scope.repository(artifact.namespace(), artifact.name());
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
