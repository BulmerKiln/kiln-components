/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.observability;

import com.bulmerkiln.format.ArtifactEventListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/** Listens to the format subsystem's events, when it's present. */
@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
@ConditionalOnClass(ArtifactEventListener.class)
@ConditionalOnBean(KilnMetrics.class)
public class FormatMetricsAutoConfiguration {

    @Bean
    MetricsEventListener metricsEventListener(KilnMetrics metrics) {
        return new MetricsEventListener(metrics);
    }
}
