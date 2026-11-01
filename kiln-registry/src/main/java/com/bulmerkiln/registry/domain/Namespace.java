/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.domain;

import com.bulmerkiln.permissions.constant.Visibility;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * A name an organization owns across formats: an OCI repository prefix ({@code acme} owns {@code
 * acme/*}), an npm scope ({@code @acme}), Maven groupId prefixes ({@code org.acme}) and Cargo crate
 * name prefixes ({@code acme-}). Permissions and visibility attach to it (REG-1).
 */
@Value
public final class Namespace {

    private final UUID id;

    private final String name;

    private final Visibility visibility;

    private final @Nullable String ociPrefix;

    private final @Nullable String npmScope;

    private final List<String> npmPrefixes;

    private final List<String> mavenGroupPrefixes;

    private final List<String> cargoPrefixes;

    public Namespace(
            UUID id,
            String name,
            Visibility visibility,
            @Nullable String ociPrefix,
            @Nullable String npmScope,
            List<String> npmPrefixes,
            List<String> mavenGroupPrefixes,
            List<String> cargoPrefixes) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(visibility, "visibility");
        if (name.isBlank()) {
            throw new IllegalArgumentException("A namespace has a name");
        }
        this.npmPrefixes = List.copyOf(npmPrefixes);
        this.mavenGroupPrefixes = List.copyOf(mavenGroupPrefixes);
        this.cargoPrefixes = List.copyOf(cargoPrefixes);
        this.id = id;
        this.name = name;
        this.visibility = visibility;
        this.ociPrefix = ociPrefix;
        this.npmScope = npmScope;
    }
}
