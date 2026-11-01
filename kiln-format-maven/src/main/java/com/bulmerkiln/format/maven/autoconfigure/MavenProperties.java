/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.autoconfigure;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Maven format's settings, under {@code kiln.format.maven}.
 */
@ConfigurationProperties("kiln.format.maven")
@Value
public final class MavenProperties {

    /** Whether the {@code /maven/} routes exist; on unless set to {@code false}. */
    private final @Nullable Boolean enabled;

    /** The largest file in bytes; 2 GiB unless set. */
    private final @Nullable Long maxFileSize;

    public long maxFileSizeOrDefault() {
        return maxFileSize == null ? 2L * 1024 * 1024 * 1024 : maxFileSize;
    }
}
