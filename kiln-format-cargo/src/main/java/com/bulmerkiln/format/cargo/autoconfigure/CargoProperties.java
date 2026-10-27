/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.autoconfigure;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Cargo format's settings, under {@code kiln.format.cargo}.
 *
 * @param enabled whether the {@code /cargo/} routes exist; on unless set to {@code false}
 * @param maxCrateSize the largest {@code .crate} in bytes; 10 MiB unless set, as on crates.io
 */
@ConfigurationProperties("kiln.format.cargo")
public record CargoProperties(
        @Nullable Boolean enabled, @Nullable Long maxCrateSize) {

    public long maxCrateSizeOrDefault() {
        return maxCrateSize == null ? 10L * 1024 * 1024 : maxCrateSize;
    }
}
