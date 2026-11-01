/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.autoconfigure;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The npm format's settings, under {@code kiln.format.npm}.
 */
@ConfigurationProperties("kiln.format.npm")
@Value
public final class NpmProperties {

    /** Whether the {@code /npm/} routes exist; on unless set to {@code false}. */
    private final @Nullable Boolean enabled;

    /** The largest tarball in bytes; 256 MiB unless set. */
    private final @Nullable Long maxTarballSize;

    public long maxTarballSizeOrDefault() {
        return maxTarballSize == null ? 256L * 1024 * 1024 : maxTarballSize;
    }
}
