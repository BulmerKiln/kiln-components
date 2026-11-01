/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.domain;

import com.bulmerkiln.permissions.constant.Visibility;
import java.util.Objects;
import java.util.UUID;
import lombok.Value;

/**
 * A name an organization owns across formats, through the {@link NamespaceClaim}s it holds in each.
 * Permissions and visibility attach to it (REG-1).
 */
@Value
public final class Namespace {

    private final UUID id;

    private final String name;

    private final Visibility visibility;

    public Namespace(UUID id, String name, Visibility visibility) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(visibility, "visibility");
        if (name.isBlank()) {
            throw new IllegalArgumentException("A namespace has a name");
        }
        this.id = id;
        this.name = name;
        this.visibility = visibility;
    }
}
