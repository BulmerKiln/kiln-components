/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions.autoconfigure;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.autoconfigure.DatabaseAutoConfiguration;
import com.bulmerkiln.permissions.RoleBindingRepository;
import com.bulmerkiln.permissions.store.RoleBindingStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Keeps role bindings in the database when the database module is present (PERM-13). */
@AutoConfiguration(after = DatabaseAutoConfiguration.class, before = PermissionsAutoConfiguration.class)
@ConditionalOnClass(Database.class)
public class StoredPermissionsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RoleBindingRepository.class)
    RoleBindingStore storedRoleBindings(Database database) {
        return new RoleBindingStore(database);
    }
}
