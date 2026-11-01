/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.domain;

import com.bulmerkiln.permissions.constant.Visibility;
import java.util.Objects;
import lombok.Value;

/** What an action targets, in the current organization. */
@Value
public final class Resource {

    private final Scope scope;

    private final Visibility visibility;

    public Resource(Scope scope, Visibility visibility) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(visibility, "visibility");
        this.scope = scope;
        this.visibility = visibility;
    }

    public static Resource organization() {
        return new Resource(Scope.organization(), Visibility.PRIVATE);
    }
}
