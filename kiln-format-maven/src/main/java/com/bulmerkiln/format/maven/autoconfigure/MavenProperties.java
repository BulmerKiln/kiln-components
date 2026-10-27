/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.autoconfigure;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Maven format's settings, under {@code kiln.format.maven}.
 *
 * @param enabled whether the {@code /maven/} routes exist; on unless set to {@code false}
 * @param maxFileSize the largest file in bytes; 2 GiB unless set
 */
@ConfigurationProperties("kiln.format.maven")
public record MavenProperties(
        @Nullable Boolean enabled, @Nullable Long maxFileSize) {

    public long maxFileSizeOrDefault() {
        return maxFileSize == null ? 2L * 1024 * 1024 * 1024 : maxFileSize;
    }
}
