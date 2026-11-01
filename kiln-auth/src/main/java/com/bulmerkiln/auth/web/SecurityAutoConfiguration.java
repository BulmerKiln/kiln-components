/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.web;

import com.bulmerkiln.auth.connector.ConnectorRegistry;
import com.bulmerkiln.auth.connector.SignInClientDirectory;
import com.bulmerkiln.auth.connector.domain.ExternalIdentity;
import com.bulmerkiln.auth.connector.domain.IdpResponse;
import com.bulmerkiln.auth.connector.domain.SignInClient;
import com.bulmerkiln.auth.user.dto.SignedInResponse;
import com.bulmerkiln.auth.user.service.SignInService;
import com.bulmerkiln.web.PrincipalResolver;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Browser sign-in on Spring Security's OAuth2 login (AUTH-18). Each connector module contributes
 * its {@link ClientRegistration} from its own auto-configuration; this chain wires them together
 * at {@code /auth/<connector-id>/callback}. A 12-hour session cookie, session fixation protection
 * and CSRF are enabled. Disabling a connector module removes its protocol.
 */
@AutoConfiguration
@EnableWebSecurity
@ConditionalOnClass(SecurityFilterChain.class)
@ConditionalOnBean(ConnectorRegistry.class)
public class SecurityAutoConfiguration {

    static final String SESSION_USER_KEY = "kiln.userId";

    @Bean
    @ConditionalOnMissingBean(ClientRegistrationRepository.class)
    ClientRegistrationRepository clientRegistrationRepository(List<SignInClientDirectory> directories) {
        return registrationId -> directories.stream()
                .map(directory -> directory.find(registrationId))
                .flatMap(Optional::stream)
                .findFirst()
                .map(SecurityAutoConfiguration::registration)
                .orElse(null);
    }

    /** The connector's client, with the callback every connector shares. */
    static ClientRegistration registration(SignInClient client) {
        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(
                        client.getId().getValue())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId(client.getClientId())
                .clientSecret(client.getClientSecret())
                .scope(client.getScopes())
                .authorizationUri(client.getAuthorizationUri().toString())
                .tokenUri(client.getTokenUri().toString())
                .redirectUri("{baseUrl}/auth/{registrationId}/callback");
        URI userInfo = client.getUserInfoUri();
        if (userInfo != null) {
            builder.userInfoUri(userInfo.toString());
        }
        URI jwkSet = client.getJwkSetUri();
        if (jwkSet != null) {
            builder.jwkSetUri(jwkSet.toString());
        }
        URI issuer = client.getIssuer();
        if (issuer != null) {
            builder.issuerUri(issuer.toString());
        }
        return builder.build();
    }

    @Bean
    @SuppressFBWarnings(
            value = "SPRING_CSRF_PROTECTION_DISABLED",
            justification = "Data plane takes no cookies; the OAuth2 flow has its own state/nonce CSRF protection")
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository registrations,
            ConnectorRegistry connectors,
            SignInService signInService) {
        http.oauth2Login(oauth2 -> oauth2.clientRegistrationRepository(registrations)
                .authorizationEndpoint(endpoint -> endpoint.baseUri("/oauth2/authorization"))
                .redirectionEndpoint(endpoint -> endpoint.baseUri("/auth/{registrationId}/callback"))
                .successHandler((request, response, authentication) -> {
                    var name = authentication.getName();
                    connectors.connectors().stream()
                            .filter(c -> c.getId().getValue().equals(name))
                            .findFirst()
                            .ifPresent(connector -> {
                                ExternalIdentity identity = connector.identify(new IdpResponse(Map.of(), Map.of()));
                                if (signInService.signIn(identity, null) instanceof SignedInResponse signedIn) {
                                    request.getSession()
                                            .setAttribute(
                                                    SESSION_USER_KEY,
                                                    signedIn.getUser().getId().toString());
                                }
                            });
                    response.setStatus(302);
                    response.setHeader("Location", "/");
                }));
        http.csrf(csrf -> csrf.disable());
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean(PrincipalResolver.class)
    SessionPrincipalResolver sessionPrincipalResolver() {
        return new SessionPrincipalResolver();
    }
}
