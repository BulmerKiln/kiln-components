/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.domain;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import java.util.Objects;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * A name a namespace claims in one format. A name matches the value exactly, or starts with the
 * value and then the separator; with no separator, any name starting with the value matches. Each
 * format says which separator its names use: {@code acme} with {@code /} owns OCI {@code acme/api},
 * {@code org.acme} with {@code .} owns Maven {@code org.acme.core}, and {@code acme-} with none owns
 * Cargo {@code acme-core} (REG-1).
 */
@Value
public final class NamespaceClaim {

    private final ArtifactFormat format;

    private final String value;

    private final @Nullable String separator;

    public NamespaceClaim(ArtifactFormat format, String value, @Nullable String separator) {
        Objects.requireNonNull(format, "format");
        if (value.isEmpty() || (separator != null && separator.isEmpty())) {
            throw new IllegalArgumentException("A claim has a value, and a separator if any is non-empty");
        }
        this.format = format;
        this.value = value;
        this.separator = separator;
    }
}
