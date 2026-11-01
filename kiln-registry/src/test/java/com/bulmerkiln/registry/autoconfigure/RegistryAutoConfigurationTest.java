/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.core.lookup.Lookup;
import com.bulmerkiln.database.KilnPostgres;
import com.bulmerkiln.database.autoconfigure.DataJdbcAutoConfiguration;
import com.bulmerkiln.database.autoconfigure.DatabaseAutoConfiguration;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.permissions.domain.Scope;
import com.bulmerkiln.registry.lookup.NamespaceVisibilityLookup;
import com.bulmerkiln.registry.policy.RegistryPolicy;
import com.bulmerkiln.registry.service.BlobRecordService;
import com.bulmerkiln.registry.service.BlockService;
import com.bulmerkiln.registry.service.NamespaceService;
import com.bulmerkiln.registry.service.PackageService;
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

    /** Each run gets its own database, since the context closes the pool when it ends. */
    private static ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DatabaseAutoConfiguration.class,
                        DataJdbcAutoConfiguration.class,
                        RegistryAutoConfiguration.class,
                        RegistryPortsAutoConfiguration.class))
                .withPropertyValues(KilnPostgres.propertyValues(KilnPostgres.newDatabase()));
    }

    /** REG-7: the registry answers the policy and visibility ports, and gives formats its services. */
    @Test
    void answersThePolicyAndVisibilityPorts() {
        runner().run(context -> {
            assertThat(context.getBean(ArtifactPolicy.class)).isInstanceOf(RegistryPolicy.class);
            assertThat(context.getBeanProvider(VISIBILITY_LOOKUP).getObject())
                    .isInstanceOf(NamespaceVisibilityLookup.class);
            assertThat(context)
                    .hasSingleBean(NamespaceService.class)
                    .hasSingleBean(PackageService.class)
                    .hasSingleBean(BlobRecordService.class)
                    .hasSingleBean(BlockService.class);
        });
    }

    /** REG-7: an assembly's own policy and visibility lookup replace the registry's. */
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
        runner().withBean(ArtifactPolicy.class, () -> policy)
                .withUserConfiguration(OwnVisibility.class)
                .run(context -> {
                    assertThat(context.getBean(ArtifactPolicy.class)).isSameAs(policy);
                    assertThat(context.getBeanProvider(VISIBILITY_LOOKUP).getObject())
                            .isSameAs(OWN_VISIBILITY);
                });
    }
}
