/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.autoconfigure;

import com.bulmerkiln.core.lookup.Lookup;
import com.bulmerkiln.format.ArtifactAccess;
import com.bulmerkiln.permissions.access.FormatAccess;
import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.permissions.domain.Scope;
import com.bulmerkiln.permissions.policy.PermissionPolicy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Implements the format subsystem's access port, when that subsystem is present. The visibility
 * {@link Lookup} has no default, so nothing becomes public by accident (PERM-11).
 */
@AutoConfiguration(after = PermissionsAutoConfiguration.class)
@ConditionalOnClass(ArtifactAccess.class)
public class FormatAccessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ArtifactAccess.class)
    ArtifactAccess artifactAccess(PermissionPolicy policy, Lookup<Scope, Visibility> visibility) {
        return new FormatAccess(policy, visibility);
    }
}
