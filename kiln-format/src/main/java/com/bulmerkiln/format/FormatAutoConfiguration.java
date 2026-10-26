/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

/**
 * Collects every {@link FormatModule} on the classpath into the {@link FormatRegistry}, leaving
 * out those switched off with {@code kiln.format.<id>.enabled=false}, and every
 * {@link ArtifactEventListener} into {@link ArtifactEvents}.
 */
@AutoConfiguration
public class FormatAutoConfiguration {

    @Bean
    FormatRegistry formatRegistry(ObjectProvider<FormatModule> modules, Environment environment) {
        return new FormatRegistry(modules.orderedStream()
                .filter(module -> environment.getProperty(
                        "kiln.format." + module.format().id() + ".enabled", Boolean.class, true))
                .toList());
    }

    /**
     * Lazy, so listeners from every module are collected whatever order their auto-configurations
     * run in. With none, the first format to need events fails the boot.
     */
    @Bean
    @Lazy
    ArtifactEvents artifactEvents(ObjectProvider<ArtifactEventListener> listeners) {
        return new ArtifactEvents(listeners.orderedStream().toList());
    }
}
