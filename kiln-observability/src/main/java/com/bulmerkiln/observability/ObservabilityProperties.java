/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Metrics settings.
 *
 * @param orgLabel adds an {@code org} label to per-organization metrics; kiln-cloud sets it
 */
@ConfigurationProperties("kiln.observability")
public record ObservabilityProperties(boolean orgLabel) {}
