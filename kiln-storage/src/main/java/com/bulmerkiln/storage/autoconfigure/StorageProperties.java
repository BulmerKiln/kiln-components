/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.autoconfigure;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage settings.
 */
@ConfigurationProperties("kiln.storage")
@Value
public final class StorageProperties {

    /**
     * The backend to use, such as {@code fs} or {@code s3}; required, so having several backends on
     * the classpath is never ambiguous.
     */
    private final @Nullable String type;
}
