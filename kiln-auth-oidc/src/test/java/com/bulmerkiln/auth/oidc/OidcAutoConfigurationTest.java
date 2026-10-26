/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OidcAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(OidcAutoConfiguration.class));

    @Test
    void bindsAndValidatesConnectors() {
        runner.withPropertyValues(
                        "kiln.auth.oidc.connectors[0].id=acme-sso",
                        "kiln.auth.oidc.connectors[0].preset=okta",
                        "kiln.auth.oidc.connectors[0].issuer=https://acme.okta.com",
                        "kiln.auth.oidc.connectors[0].client-id=kiln",
                        "kiln.auth.oidc.connectors[0].client-secret=env:KILN_OKTA_SECRET",
                        "kiln.auth.oidc.connectors[0].domains=acme.com,acme.io")
                .run(context -> {
                    OidcConnector connector =
                            context.getBean(OidcConnectors.class).connectors().getFirst();
                    assertThat(connector.preset()).isEqualTo(OidcPreset.OKTA);
                    assertThat(connector.domains()).containsExactlyInAnyOrder("acme.com", "acme.io");
                });
    }

    @Test
    void startsWithNoConnectors() {
        runner.run(context ->
                assertThat(context.getBean(OidcConnectors.class).connectors()).isEmpty());
    }

    @Test
    void stopsTheBootOnABadConnector() {
        runner.withPropertyValues(
                        "kiln.auth.oidc.connectors[0].id=acme-sso",
                        "kiln.auth.oidc.connectors[0].issuer=http://acme.okta.com",
                        "kiln.auth.oidc.connectors[0].client-id=kiln",
                        "kiln.auth.oidc.connectors[0].client-secret=env:S")
                .run(context ->
                        assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("https"));
    }
}
