/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.domain;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import java.util.Objects;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * An artifact as the cross-format rules see it, in the current organization.
 */
@Value
public final class ArtifactRef {

    private final ArtifactFormat format;

    /** The namespace it belongs to, such as {@code acme}, {@code @acme} or {@code com.acme}. */
    private final String namespace;

    /** The repository or package name within it. */
    private final String name;

    /** The tag, version or digest; absent when the whole repository or package is meant. */
    private final @Nullable String version;

    public ArtifactRef(ArtifactFormat format, String namespace, String name, @Nullable String version) {
        Objects.requireNonNull(format, "format");
        if (namespace.isBlank() || name.isBlank() || (version != null && version.isBlank())) {
            throw new IllegalArgumentException("Artifact references have no blank parts");
        }
        this.format = format;
        this.namespace = namespace;
        this.name = name;
        this.version = version;
    }
}
