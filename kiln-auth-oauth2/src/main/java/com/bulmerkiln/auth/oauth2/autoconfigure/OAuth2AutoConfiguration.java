/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oauth2.autoconfigure;

import com.bulmerkiln.auth.connector.SignInClientDirectory;
import com.bulmerkiln.auth.connector.domain.SignInClient;
import com.bulmerkiln.auth.connector.util.SecretResolver;
import com.bulmerkiln.auth.oauth2.OAuth2Connector;
import com.bulmerkiln.auth.oauth2.OAuth2Connectors;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Validates the configured OAuth2 connectors at startup and contributes their sign-in clients, so
 * Spring Security's OAuth2 login can drive the flow. A secret that can't be read stops the boot.
 */
@AutoConfiguration
@EnableConfigurationProperties(OAuth2Properties.class)
public class OAuth2AutoConfiguration {

    @Bean
    OAuth2Connectors oauth2Connectors(OAuth2Properties properties) {
        return OAuth2Connectors.from(properties);
    }

    @Bean
    SignInClientDirectory oauth2SignInClients(OAuth2Connectors connectors) {
        return SignInClientDirectory.of(connectors.getConnectors().stream()
                .map(OAuth2AutoConfiguration::signInClient)
                .toList());
    }

    static SignInClient signInClient(OAuth2Connector connector) {
        return new SignInClient(
                connector.getId(),
                connector.getClientId(),
                SecretResolver.resolveClientSecret(connector.getId(), connector.getClientSecret()),
                connector.getScopes(),
                connector.getUrls().getAuthorization(),
                connector.getUrls().getToken(),
                connector.getUrls().getUserInfo(),
                null,
                null);
    }
}
