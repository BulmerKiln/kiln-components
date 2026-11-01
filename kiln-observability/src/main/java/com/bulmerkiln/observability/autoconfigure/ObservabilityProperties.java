/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.observability.autoconfigure;

import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Metrics settings.
 */
@ConfigurationProperties("kiln.observability")
@Value
public final class ObservabilityProperties {

    /** Adds an {@code organization} label to per-organization metrics, for installations with several organizations. */
    private final boolean organizationLabel;
}
