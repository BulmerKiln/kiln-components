/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.observability.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Metrics settings.
 *
 * @param organizationLabel adds an {@code organization} label to per-organization metrics, for
 *     installations with several organizations
 */
@ConfigurationProperties("kiln.observability")
public record ObservabilityProperties(boolean organizationLabel) {}
