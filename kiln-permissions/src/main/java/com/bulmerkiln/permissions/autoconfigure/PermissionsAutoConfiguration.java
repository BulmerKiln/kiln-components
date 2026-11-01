/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.autoconfigure;

import com.bulmerkiln.core.lookup.Lookup;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.permissions.domain.RoleBinding;
import com.bulmerkiln.permissions.policy.PermissionPolicy;
import com.bulmerkiln.permissions.policy.RoleBasedPolicy;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Provides the role-based policy over the assembly's role-binding {@link Lookup}. There's no default:
 * without one the boot fails rather than granting nothing silently or everything by mistake (PERM-11).
 */
@AutoConfiguration
public class PermissionsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PermissionPolicy.class)
    PermissionPolicy permissionPolicy(Lookup<Principal, List<RoleBinding>> bindings) {
        return new RoleBasedPolicy(bindings);
    }
}
