/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

import java.util.Objects;

/** What an action targets, in the current organization. */
public record Resource(Scope scope, Visibility visibility) {

    public Resource {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(visibility, "visibility");
    }

    public static Resource organization() {
        return new Resource(Scope.organization(), Visibility.PRIVATE);
    }
}
