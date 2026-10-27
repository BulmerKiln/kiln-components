/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.domain;

import com.bulmerkiln.permissions.constant.Visibility;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A name an organization owns across formats: an OCI repository prefix ({@code acme} owns {@code
 * acme/*}), an npm scope ({@code @acme}), Maven groupId prefixes ({@code org.acme}) and Cargo crate
 * name prefixes ({@code acme-}). Permissions and visibility attach to it (REG-1).
 */
public record Namespace(
        UUID id,
        String name,
        Visibility visibility,
        @Nullable String ociPrefix,
        @Nullable String npmScope,
        List<String> mavenGroupPrefixes,
        List<String> cargoPrefixes) {

    public Namespace {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(visibility, "visibility");
        if (name.isBlank()) {
            throw new IllegalArgumentException("A namespace has a name");
        }
        mavenGroupPrefixes = List.copyOf(mavenGroupPrefixes);
        cargoPrefixes = List.copyOf(cargoPrefixes);
    }
}
