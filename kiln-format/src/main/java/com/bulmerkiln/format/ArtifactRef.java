/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * An artifact as the cross-format rules see it, in the current organization.
 *
 * @param namespace the namespace it belongs to, such as {@code acme}, {@code @acme} or {@code com.acme}
 * @param name the repository or package name within it
 * @param version the tag, version or digest; absent when the whole repository or package is meant
 */
public record ArtifactRef(
        ArtifactFormat format,
        String namespace,
        String name,
        @Nullable String version) {

    public ArtifactRef {
        Objects.requireNonNull(format, "format");
        if (namespace.isBlank() || name.isBlank() || (version != null && version.isBlank())) {
            throw new IllegalArgumentException("Artifact references have no blank parts");
        }
    }
}
