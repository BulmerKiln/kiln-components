/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.oidc.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.auth.connector.SignInClientDirectory;
import com.bulmerkiln.auth.oidc.OidcConnector;
import com.bulmerkiln.auth.oidc.OidcConnectors;
import com.bulmerkiln.auth.oidc.constant.OidcPreset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OidcAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(OidcAutoConfiguration.class));

    /** AUTH-OIDC-7: the boot binds connectors without fetching anything from the IdP. */
    @Test
    void bindsAndValidatesConnectors(@TempDir Path dir) throws Exception {
        runner.withPropertyValues(
                        "kiln.auth.oidc.connectors[0].id=acme-sso",
                        "kiln.auth.oidc.connectors[0].preset=okta",
                        "kiln.auth.oidc.connectors[0].issuer=https://acme.okta.com",
                        "kiln.auth.oidc.connectors[0].client-id=kiln",
                        "kiln.auth.oidc.connectors[0].client-secret=" + secretFile(dir),
                        "kiln.auth.oidc.connectors[0].domains=acme.com,acme.io")
                .run(context -> {
                    OidcConnector connector = context.getBean(OidcConnectors.class)
                            .getConnectors()
                            .getFirst();
                    assertThat(connector.getPreset()).isEqualTo(OidcPreset.OKTA);
                    assertThat(connector.getDomains()).containsExactlyInAnyOrder("acme.com", "acme.io");
                    assertThat(context).hasSingleBean(SignInClientDirectory.class);
                });
    }

    @Test
    void startsWithNoConnectors() {
        runner.run(context -> assertThat(context.getBean(OidcConnectors.class).getConnectors())
                .isEmpty());
    }

    /** AUTH-OIDC-6: a bad setting stops the boot. */
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

    /** AUTH-OIDC-6: a secret that can't be read stops the boot, naming the connector and the setting. */
    @Test
    void stopsTheBootOnAnUnreadableSecret() {
        runner.withPropertyValues(
                        "kiln.auth.oidc.connectors[0].id=acme-sso",
                        "kiln.auth.oidc.connectors[0].issuer=https://acme.okta.com",
                        "kiln.auth.oidc.connectors[0].client-id=kiln",
                        "kiln.auth.oidc.connectors[0].client-secret=env:KILN_DEFINITELY_NOT_SET_VAR_XYZ")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("Connector acme-sso: client-secret"));
    }

    private static String secretFile(Path dir) throws Exception {
        Path file = dir.resolve("secret");
        Files.writeString(file, "s3cret");
        return "file:" + file;
    }
}
