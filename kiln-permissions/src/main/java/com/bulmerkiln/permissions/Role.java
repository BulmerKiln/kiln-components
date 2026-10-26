/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

import java.util.EnumSet;
import java.util.Set;

/** The four roles and what each grants (PERM-1). */
public enum Role {
    READER(EnumSet.of(Action.PULL)),
    WRITER(EnumSet.of(Action.PULL, Action.PUSH)),
    SECURITY(EnumSet.of(Action.PULL, Action.BLOCK, Action.UNBLOCK, Action.VIEW_SECURITY)),
    ADMIN(EnumSet.allOf(Action.class));

    @SuppressWarnings("ImmutableEnumChecker") // Copied into an unmodifiable set below.
    private final Set<Action> actions;

    Role(Set<Action> actions) {
        this.actions = Set.copyOf(actions);
    }

    public Set<Action> actions() {
        return actions;
    }
}
