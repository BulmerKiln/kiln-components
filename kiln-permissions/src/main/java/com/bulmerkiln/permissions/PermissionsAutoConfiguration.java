/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Provides the role-based policy over the assembly's {@link RoleBindingRepository}. Without a
 * repository the boot fails, by design.
 */
@AutoConfiguration
public class PermissionsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PermissionPolicy.class)
    PermissionPolicy permissionPolicy(RoleBindingRepository bindings) {
        return new RoleBasedPolicy(bindings);
    }
}
