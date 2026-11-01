/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo.autoconfigure;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Cargo format's settings, under {@code kiln.format.cargo}.
 */
@ConfigurationProperties("kiln.format.cargo")
@Value
public final class CargoProperties {

    /** Whether the {@code /cargo/} routes exist; on unless set to {@code false}. */
    private final @Nullable Boolean enabled;

    /** The largest {@code .crate} in bytes; 10 MiB unless set, as on crates.io. */
    private final @Nullable Long maxCrateSize;

    public long maxCrateSizeOrDefault() {
        return maxCrateSize == null ? 10L * 1024 * 1024 : maxCrateSize;
    }
}
