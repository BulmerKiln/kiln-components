/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry.autoconfigure;

import com.bulmerkiln.core.lookup.Lookup;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.format.ArtifactPolicy;
import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.permissions.domain.Scope;
import com.bulmerkiln.registry.lookup.NamespaceVisibilityLookup;
import com.bulmerkiln.registry.policy.RegistryPolicy;
import com.bulmerkiln.registry.service.BlockService;
import com.bulmerkiln.registry.service.NamespaceService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** The formats' policy port and the permissions' visibility port, answered from the registry. */
@AutoConfiguration(after = RegistryAutoConfiguration.class)
public class RegistryPortsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ArtifactPolicy.class)
    ArtifactPolicy registryPolicy(Database database, BlockService blocks) {
        return new RegistryPolicy(database, blocks);
    }

    /** Declared as the generic lookup, so an assembly's own visibility lookup replaces it. */
    @Bean
    @ConditionalOnMissingBean
    Lookup<Scope, Visibility> namespaceVisibility(Database database, NamespaceService namespaces) {
        return new NamespaceVisibilityLookup(database, namespaces);
    }
}
