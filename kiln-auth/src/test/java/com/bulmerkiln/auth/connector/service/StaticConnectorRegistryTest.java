/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.bulmerkiln.auth.autoconfigure.ConnectorAutoConfiguration;
import com.bulmerkiln.auth.connector.ConnectorRegistry;
import com.bulmerkiln.auth.connector.ConnectorSource;
import com.bulmerkiln.auth.connector.IdentityConnector;
import com.bulmerkiln.auth.connector.domain.ConnectorId;
import com.bulmerkiln.auth.connector.domain.ExternalIdentity;
import com.bulmerkiln.auth.connector.domain.IdpResponse;
import com.bulmerkiln.auth.connector.util.IdentityUtils;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StaticConnectorRegistryTest {

    static IdentityConnector connector(String id, String type, Set<String> domains, Map<String, URI> lookups) {
        return new IdentityConnector() {
            @Override
            public ConnectorId getId() {
                return new ConnectorId(id);
            }

            @Override
            public String type() {
                return type;
            }

            @Override
            public String getDisplayName() {
                return id;
            }

            @Override
            public Set<String> getDomains() {
                return domains;
            }

            @Override
            public boolean providesGroups() {
                return true;
            }

            @Override
            public Map<String, URI> lookups() {
                return lookups;
            }

            @Override
            public ExternalIdentity identify(IdpResponse response) {
                return new ExternalIdentity(
                        getId(),
                        "https://idp",
                        "1",
                        null,
                        IdentityUtils.groups(response.getClaims().get("groups")));
            }
        };
    }

    @Test
    void refusesOneIdAcrossProtocols() {
        // Each module checked only its own IDs; an OIDC and an OAuth2 "github" must not coexist.
        assertThatIllegalStateException()
                .isThrownBy(() -> new StaticConnectorRegistry(List.of(
                        connector("github", "oidc", Set.of(), Map.of()),
                        connector("github", "oauth2", Set.of(), Map.of()))))
                .withMessageContaining("oidc")
                .withMessageContaining("oauth2");
    }

    @Test
    void findsConnectorsByIdAndDomain() {
        StaticConnectorRegistry registry = new StaticConnectorRegistry(List.of(
                connector("okta", "oidc", Set.of("acme.com"), Map.of()),
                connector("github", "oauth2", Set.of("acme.com", "acme.io"), Map.of())));

        assertThat(registry.get(new ConnectorId("okta"))).isPresent();
        assertThat(registry.get(new ConnectorId("nope"))).isEmpty();
        assertThat(registry.forDomain("ACME.com"))
                .extracting(c -> c.getId().getValue())
                .containsExactly("okta", "github");
        assertThat(registry.forDomain("acme.io"))
                .extracting(c -> c.getId().getValue())
                .containsExactly("github");
        assertThat(registry.forDomain("evil.com")).isEmpty();
        assertThat(registry.forDomain("sub.acme.com")).isEmpty();
    }

    @Test
    void lookupsMustBeHttpsAndWellNamed() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StaticConnectorRegistry(List.of(connector(
                        "gh", "oauth2", Set.of(), Map.of("teams", URI.create("http://api.github.com/user/teams"))))));
        assertThatIllegalStateException()
                .isThrownBy(() -> new StaticConnectorRegistry(List.of(connector(
                        "gh", "oauth2", Set.of(), Map.of("Teams!", URI.create("https://api.github.com/user/teams"))))));
    }

    @Test
    void identityRulesKeepOnlyVerifiedEmails() {
        assertThat(IdentityUtils.verifiedEmail("a@acme.com", true)).isEqualTo("a@acme.com");
        assertThat(IdentityUtils.verifiedEmail("a@acme.com", "true")).isNull();
        assertThat(IdentityUtils.verifiedEmail("a@acme.com", null)).isNull();
        assertThat(IdentityUtils.verifiedEmail("not-an-email", true)).isNull();
        assertThat(IdentityUtils.verifiedEmail("@acme.com", true)).isNull();
        assertThat(IdentityUtils.verifiedEmail("a@acme.com\r\nBcc: x", true)).isNull();
        assertThat(IdentityUtils.verifiedEmail(42, true)).isNull();
    }

    @Test
    void autoConfigurationCombinesEverySource() {
        ConnectorSource oidc = () -> List.of(connector("okta", "oidc", Set.of(), Map.of()));
        ConnectorSource oauth2 = () -> List.of(connector("github", "oauth2", Set.of(), Map.of()));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConnectorAutoConfiguration.class))
                .withBean("oidc", ConnectorSource.class, () -> oidc)
                .withBean("oauth2", ConnectorSource.class, () -> oauth2)
                .run(context -> assertThat(
                                context.getBean(ConnectorRegistry.class).connectors())
                        .hasSize(2));
    }

    @Test
    void refusesToStartWithoutAConnector() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConnectorAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("at least one sign-in connector"));
    }

    @Test
    void backsOffForAnotherRegistry() {
        ConnectorRegistry own = new StaticConnectorRegistry(List.of());
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConnectorAutoConfiguration.class))
                .withBean(ConnectorRegistry.class, () -> own)
                .run(context ->
                        assertThat(context.getBean(ConnectorRegistry.class)).isSameAs(own));
    }
}
