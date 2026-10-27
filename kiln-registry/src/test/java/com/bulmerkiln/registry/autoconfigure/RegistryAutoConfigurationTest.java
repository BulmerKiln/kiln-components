/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.KilnPostgres;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.permissions.VisibilityLookup;
import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.registry.service.NamespaceVisibilityLookup;
import com.bulmerkiln.registry.service.RegistryPolicy;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RegistryAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(RegistryAutoConfiguration.class));

    /** REG-4, REG-5: the registry answers the policy and visibility ports. */
    @Test
    void answersThePolicyAndVisibilityPorts() {
        try (Database database = KilnPostgres.database()) {
            runner.withBean(Database.class, () -> database).run(context -> {
                assertThat(context.getBean(ArtifactPolicy.class)).isInstanceOf(RegistryPolicy.class);
                assertThat(context.getBean(VisibilityLookup.class)).isInstanceOf(NamespaceVisibilityLookup.class);
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
        VisibilityLookup visibility = scope -> Visibility.PRIVATE;
        try (Database database = KilnPostgres.database()) {
            runner.withBean(Database.class, () -> database)
                    .withBean(ArtifactPolicy.class, () -> policy)
                    .withBean(VisibilityLookup.class, () -> visibility)
                    .run(context -> {
                        assertThat(context.getBean(ArtifactPolicy.class)).isSameAs(policy);
                        assertThat(context.getBean(VisibilityLookup.class)).isSameAs(visibility);
                    });
        }
    }
}
