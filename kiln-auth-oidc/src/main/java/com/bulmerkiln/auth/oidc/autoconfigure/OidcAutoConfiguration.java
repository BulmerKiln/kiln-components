/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc.autoconfigure;

import com.bulmerkiln.auth.connector.SignInClientDirectory;
import com.bulmerkiln.auth.oidc.OidcConnectors;
import com.bulmerkiln.auth.oidc.domain.OidcEndpoints;
import com.bulmerkiln.auth.oidc.service.OidcSignInClientDirectory;
import java.net.URI;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;

/**
 * Validates the configured OIDC connectors at startup and contributes their sign-in clients, whose
 * endpoints come from OpenID Connect discovery on first use.
 */
@AutoConfiguration
@EnableConfigurationProperties(OidcProperties.class)
public class OidcAutoConfiguration {

    @Bean
    OidcConnectors oidcConnectors(OidcProperties properties) {
        return OidcConnectors.from(properties);
    }

    @Bean
    SignInClientDirectory oidcSignInClients(OidcConnectors connectors) {
        return new OidcSignInClientDirectory(connectors.getConnectors(), OidcAutoConfiguration::discover);
    }

    /**
     * Fetches the issuer's discovery document with Spring Security, which refuses one whose
     * {@code issuer} isn't exactly the issuer asked for.
     */
    static OidcEndpoints discover(URI issuer) {
        ClientRegistration.ProviderDetails provider = ClientRegistrations.fromIssuerLocation(issuer.toString())
                .registrationId("discovery")
                .clientId("discovery")
                .redirectUri("{baseUrl}")
                .build()
                .getProviderDetails();
        String userInfo = provider.getUserInfoEndpoint().getUri();
        String jwkSet = provider.getJwkSetUri();
        if (jwkSet == null) {
            throw new IllegalStateException("The discovery document for " + issuer + " has no jwks_uri");
        }
        return new OidcEndpoints(
                URI.create(provider.getAuthorizationUri()),
                URI.create(provider.getTokenUri()),
                userInfo == null ? null : URI.create(userInfo),
                URI.create(jwkSet));
    }
}
