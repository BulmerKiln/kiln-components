/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oauth2;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Validates the configured OAuth2 connectors at startup, so a bad one stops the boot. */
@AutoConfiguration
@EnableConfigurationProperties(OAuth2Properties.class)
public class OAuth2AutoConfiguration {

    @Bean
    OAuth2Connectors oauth2Connectors(OAuth2Properties properties) {
        return OAuth2Connectors.from(properties);
    }
}
