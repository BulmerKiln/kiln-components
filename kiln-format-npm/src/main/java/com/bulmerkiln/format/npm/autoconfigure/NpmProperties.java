/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm.autoconfigure;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The npm format's settings, under {@code kiln.format.npm}.
 *
 * @param enabled whether the {@code /npm/} routes exist; on unless set to {@code false}
 * @param maxTarballSize the largest tarball in bytes; 256 MiB unless set
 */
@ConfigurationProperties("kiln.format.npm")
public record NpmProperties(
        @Nullable Boolean enabled, @Nullable Long maxTarballSize) {

    public long maxTarballSizeOrDefault() {
        return maxTarballSize == null ? 256L * 1024 * 1024 : maxTarballSize;
    }
}
