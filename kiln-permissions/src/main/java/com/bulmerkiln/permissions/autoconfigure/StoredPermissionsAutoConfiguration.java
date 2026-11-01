/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.autoconfigure;

import com.bulmerkiln.core.lookup.Lookup;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.autoconfigure.DataJdbcAutoConfiguration;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.permissions.domain.RoleBinding;
import com.bulmerkiln.permissions.lookup.StoredRoleBindingLookup;
import com.bulmerkiln.permissions.mapper.RoleBindingMapperImpl;
import com.bulmerkiln.permissions.repository.RoleBindingRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

/** Keeps role bindings in the database when the database module is present (PERM-13). */
@AutoConfiguration(after = DataJdbcAutoConfiguration.class, before = PermissionsAutoConfiguration.class)
@ConditionalOnClass(Database.class)
@EnableJdbcRepositories(basePackageClasses = RoleBindingRepository.class, enableDefaultTransactions = false)
public class StoredPermissionsAutoConfiguration {

    /** Declared as the generic lookup, so an assembly's own role-binding lookup replaces it. */
    @Bean
    @ConditionalOnMissingBean
    Lookup<Principal, List<RoleBinding>> storedRoleBindings(Database database, RoleBindingRepository bindings) {
        return new StoredRoleBindingLookup(database, bindings, new RoleBindingMapperImpl());
    }
}
