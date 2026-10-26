/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Collects every module's connectors into the {@link ConnectorRegistry}. Kiln refuses to start
 * without one (AUTH-13); kiln-cloud provides its own per-organization registry.
 */
@AutoConfiguration
public class ConnectorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ConnectorRegistry.class)
    ConnectorRegistry connectorRegistry(ObjectProvider<ConnectorSource> sources) {
        StaticConnectorRegistry registry = new StaticConnectorRegistry(sources.orderedStream()
                .flatMap(source -> source.connectors().stream())
                .toList());
        if (registry.connectors().isEmpty()) {
            throw new IllegalStateException("Kiln needs at least one sign-in connector; "
                    + "configure one under kiln.auth.oidc or kiln.auth.oauth2");
        }
        return registry;
    }
}
