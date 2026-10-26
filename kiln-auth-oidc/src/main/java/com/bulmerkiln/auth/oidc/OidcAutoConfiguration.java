/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Validates the configured OIDC connectors at startup, so a bad one stops the boot. */
@AutoConfiguration
@EnableConfigurationProperties(OidcProperties.class)
public class OidcAutoConfiguration {

    @Bean
    OidcConnectors oidcConnectors(OidcProperties properties) {
        return OidcConnectors.from(properties);
    }
}
