/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

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
            public ConnectorId id() {
                return new ConnectorId(id);
            }

            @Override
            public String type() {
                return type;
            }

            @Override
            public String displayName() {
                return id;
            }

            @Override
            public Set<String> domains() {
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
                        id(),
                        "https://idp",
                        "1",
                        null,
                        IdentityRules.groups(response.claims().get("groups")));
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
                .extracting(c -> c.id().value())
                .containsExactly("okta", "github");
        assertThat(registry.forDomain("acme.io"))
                .extracting(c -> c.id().value())
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
        assertThat(IdentityRules.verifiedEmail("a@acme.com", true)).isEqualTo("a@acme.com");
        assertThat(IdentityRules.verifiedEmail("a@acme.com", "true")).isNull();
        assertThat(IdentityRules.verifiedEmail("a@acme.com", null)).isNull();
        assertThat(IdentityRules.verifiedEmail("not-an-email", true)).isNull();
        assertThat(IdentityRules.verifiedEmail("@acme.com", true)).isNull();
        assertThat(IdentityRules.verifiedEmail("a@acme.com\r\nBcc: x", true)).isNull();
        assertThat(IdentityRules.verifiedEmail(42, true)).isNull();
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
        ConnectorRegistry cloud = new StaticConnectorRegistry(List.of());
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConnectorAutoConfiguration.class))
                .withBean(ConnectorRegistry.class, () -> cloud)
                .run(context ->
                        assertThat(context.getBean(ConnectorRegistry.class)).isSameAs(cloud));
    }
}
