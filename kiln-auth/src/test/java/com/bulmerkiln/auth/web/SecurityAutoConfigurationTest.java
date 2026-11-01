/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.auth.connector.domain.ConnectorId;
import com.bulmerkiln.auth.connector.domain.SignInClient;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

class SecurityAutoConfigurationTest {

    /** AUTH-18: every connector's client runs the authorization code flow back to its own callback. */
    @Test
    void registersTheAuthorizationCodeFlowAtTheConnectorsCallback() {
        ClientRegistration registration = SecurityAutoConfiguration.registration(new SignInClient(
                new ConnectorId("dex"),
                "kiln",
                "s3cret",
                List.of("openid", "email"),
                URI.create("https://dex.example/dex/auth"),
                URI.create("https://dex.example/dex/token"),
                URI.create("https://dex.example/dex/userinfo"),
                URI.create("https://dex.example/dex/keys"),
                URI.create("https://dex.example/dex")));

        assertThat(registration.getRegistrationId()).isEqualTo("dex");
        assertThat(registration.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(registration.getRedirectUri()).isEqualTo("{baseUrl}/auth/{registrationId}/callback");
        assertThat(registration.getScopes()).containsExactlyInAnyOrder("openid", "email");
        assertThat(registration.getProviderDetails().getAuthorizationUri()).isEqualTo("https://dex.example/dex/auth");
        assertThat(registration.getProviderDetails().getJwkSetUri()).isEqualTo("https://dex.example/dex/keys");
        assertThat(registration.getProviderDetails().getIssuerUri()).isEqualTo("https://dex.example/dex");
    }

    /** AUTH-18: a plain OAuth2 client has no keys or issuer to set. */
    @Test
    void leavesOutWhatAPlainOAuth2ClientDoesntHave() {
        ClientRegistration registration = SecurityAutoConfiguration.registration(new SignInClient(
                new ConnectorId("github"),
                "Iv1.abc",
                "s3cret",
                List.of("read:user"),
                URI.create("https://github.com/login/oauth/authorize"),
                URI.create("https://github.com/login/oauth/access_token"),
                null,
                null,
                null));

        assertThat(registration.getProviderDetails().getUserInfoEndpoint().getUri())
                .isNull();
        assertThat(registration.getProviderDetails().getJwkSetUri()).isNull();
        assertThat(registration.getProviderDetails().getIssuerUri()).isNull();
    }
}
