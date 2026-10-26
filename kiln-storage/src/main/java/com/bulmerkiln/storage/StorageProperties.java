/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage settings.
 *
 * @param type the backend to use, such as {@code fs} or {@code s3}; required, so having several
 *     backends on the classpath is never ambiguous
 */
@ConfigurationProperties("kiln.storage")
public record StorageProperties(@Nullable String type) {}
