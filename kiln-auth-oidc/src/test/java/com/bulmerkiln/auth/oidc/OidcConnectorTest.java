/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.bulmerkiln.auth.connector.SecretRef;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class OidcConnectorTest {

    @Test
    void appliesPresetDefaults() {
        OidcConnector connector = OidcConnector.from(config("okta", OidcPreset.OKTA, "https://acme.okta.com", null));

        assertThat(connector.displayName()).isEqualTo("okta");
        assertThat(connector.type()).isEqualTo("oidc");
        assertThat(connector.scopes()).containsExactly("openid", "profile", "email", "groups");
        assertThat(connector.groupsClaim()).isEqualTo("groups");
        assertThat(connector.clientSecret()).isEqualTo(new SecretRef.Env("KILN_OIDC_SECRET"));
    }

    @Test
    void defaultsToTheGenericPreset() {
        OidcConnector connector = OidcConnector.from(config("sso", null, "https://sso.acme.com", null));
        assertThat(connector.preset()).isEqualTo(OidcPreset.GENERIC);
    }

    @Test
    void overridesPresetScopesAndClaim() {
        OidcProperties.Connector config = new OidcProperties.Connector(
                "kc",
                "Acme SSO",
                OidcPreset.KEYCLOAK,
                URI.create("https://kc.acme.com/realms/acme"),
                "kiln",
                "file:/run/secrets/kc",
                List.of("openid", "roles"),
                "realm_groups",
                List.of("Acme.com"),
                false);
        OidcConnector connector = OidcConnector.from(config);

        assertThat(connector.displayName()).isEqualTo("Acme SSO");
        assertThat(connector.scopes()).containsExactly("openid", "roles");
        assertThat(connector.groupsClaim()).isEqualTo("realm_groups");
        assertThat(connector.domains()).containsExactly("acme.com");
    }

    @ParameterizedTest
    @EnumSource(OidcPreset.class)
    void everyPresetRequestsOpenid(OidcPreset preset) {
        assertThat(preset.scopes()).contains("openid");
    }

    @Test
    void googleWorkspaceNeedsDomains() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OidcConnector.from(
                        config("google", OidcPreset.GOOGLE_WORKSPACE, "https://accounts.google.com", null)))
                .withMessageContaining("domains");
        assertThat(OidcConnector.from(config(
                                "google",
                                OidcPreset.GOOGLE_WORKSPACE,
                                "https://accounts.google.com",
                                List.of("acme.com")))
                        .groupsClaim())
                .isNull();
    }

    @Test
    void refusesScopesWithoutOpenid() {
        OidcProperties.Connector config = new OidcProperties.Connector(
                "sso",
                null,
                null,
                URI.create("https://sso.acme.com"),
                "kiln",
                "env:S",
                List.of("profile"),
                null,
                null,
                false);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OidcConnector.from(config))
                .withMessageContaining("openid");
    }

    @Test
    void namesTheMissingSetting() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OidcConnector.from(new OidcProperties.Connector(
                        "sso", null, null, null, "kiln", "env:S", null, null, null, false)))
                .withMessage("Connector sso: issuer is required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OidcConnector.from(new OidcProperties.Connector(
                        "sso", null, null, URI.create("https://sso.acme.com"), null, "env:S", null, null, null, false)))
                .withMessageContaining("client-id");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OidcConnector.from(new OidcProperties.Connector(
                        null,
                        null,
                        null,
                        URI.create("https://sso.acme.com"),
                        "kiln",
                        "env:S",
                        null,
                        null,
                        null,
                        false)))
                .withMessageContaining("id is required");
    }

    @Test
    void refusesAnInlineClientSecret() {
        OidcProperties.Connector config = new OidcProperties.Connector(
                "sso",
                null,
                null,
                URI.create("https://sso.acme.com"),
                "kiln",
                "s3cr3t-value-123",
                null,
                null,
                null,
                false);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OidcConnector.from(config))
                .withMessageNotContaining("s3cr3t-value-123");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://sso.acme.com",
                "https://sso.acme.com?x=1",
                "https://u:p@sso.acme.com",
                "https://sso.acme.com#a"
            })
    void refusesUnsafeIssuers(String issuer) {
        assertThatIllegalArgumentException().isThrownBy(() -> OidcConnector.from(config("sso", null, issuer, null)));
    }

    @Test
    void allowsHttpOnlyWhenExplicitlyEnabled() {
        OidcProperties.Connector config = new OidcProperties.Connector(
                "dex",
                null,
                OidcPreset.DEX,
                URI.create("http://dex:5556/dex"),
                "kiln",
                "env:S",
                null,
                null,
                null,
                true);
        assertThat(OidcConnector.from(config).issuer()).hasToString("http://dex:5556/dex");
    }

    @Test
    void refusesDuplicateConnectorIds() {
        OidcConnector a = OidcConnector.from(config("sso", null, "https://a.acme.com", null));
        OidcConnector b = OidcConnector.from(config("sso", null, "https://b.acme.com", null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OidcConnectors(List.of(a, b)))
                .withMessageContaining("sso");
    }

    static OidcProperties.Connector config(String id, OidcPreset preset, String issuer, List<String> domains) {
        return new OidcProperties.Connector(
                id, null, preset, URI.create(issuer), "kiln", "env:KILN_OIDC_SECRET", null, null, domains, false);
    }
}
