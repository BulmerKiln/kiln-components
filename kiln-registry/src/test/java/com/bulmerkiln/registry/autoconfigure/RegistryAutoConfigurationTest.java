/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.core.lookup.Lookup;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.KilnPostgres;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.permissions.domain.Scope;
import com.bulmerkiln.registry.service.NamespaceVisibilityLookup;
import com.bulmerkiln.registry.service.RegistryPolicy;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;

class RegistryAutoConfigurationTest {

    private static final ResolvableType VISIBILITY_LOOKUP =
            ResolvableType.forClassWithGenerics(Lookup.class, Scope.class, Visibility.class);

    private static final Lookup<Scope, Visibility> OWN_VISIBILITY = scope -> Visibility.PRIVATE;

    /** An assembly's own visibility lookup, declared with its type arguments as a real one would be. */
    @Configuration(proxyBeanMethods = false)
    static class OwnVisibility {
        @Bean
        Lookup<Scope, Visibility> ownVisibility() {
            return OWN_VISIBILITY;
        }
    }

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(RegistryAutoConfiguration.class));

    /** REG-4, REG-5: the registry answers the policy and visibility ports. */
    @Test
    void answersThePolicyAndVisibilityPorts() {
        try (Database database = KilnPostgres.database()) {
            runner.withBean(Database.class, () -> database).run(context -> {
                assertThat(context.getBean(ArtifactPolicy.class)).isInstanceOf(RegistryPolicy.class);
                assertThat(context.getBeanProvider(VISIBILITY_LOOKUP).getObject())
                        .isInstanceOf(NamespaceVisibilityLookup.class);
            });
        }
    }

    @Test
    void stepsAsideForTheAssemblysOwn() {
        ArtifactPolicy policy = new ArtifactPolicy() {
            @Override
            public Optional<Refusal> checkRead(ArtifactRef artifact) {
                return Optional.empty();
            }

            @Override
            public Optional<Refusal> checkWrite(ArtifactRef artifact, long bytes) {
                return Optional.empty();
            }
        };
        try (Database database = KilnPostgres.database()) {
            runner.withBean(Database.class, () -> database)
                    .withBean(ArtifactPolicy.class, () -> policy)
                    .withUserConfiguration(OwnVisibility.class)
                    .run(context -> {
                        assertThat(context.getBean(ArtifactPolicy.class)).isSameAs(policy);
                        assertThat(context.getBeanProvider(VISIBILITY_LOOKUP).getObject())
                                .isSameAs(OWN_VISIBILITY);
                    });
        }
    }
}
