/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.autoconfigure;

import com.bulmerkiln.core.lookup.Lookup;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.autoconfigure.DatabaseAutoConfiguration;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.permissions.domain.Scope;
import com.bulmerkiln.registry.service.NamespaceVisibilityLookup;
import com.bulmerkiln.registry.service.RegistryPolicy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Answers the formats' policy port and the permissions' visibility port from the registry. */
@AutoConfiguration(after = DatabaseAutoConfiguration.class)
public class RegistryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ArtifactPolicy.class)
    ArtifactPolicy registryPolicy(Database database) {
        return new RegistryPolicy(database);
    }

    /** Declared as the generic lookup, so an assembly's own visibility lookup replaces it. */
    @Bean
    @ConditionalOnMissingBean
    Lookup<Scope, Visibility> namespaceVisibility(Database database) {
        return new NamespaceVisibilityLookup(database);
    }
}
