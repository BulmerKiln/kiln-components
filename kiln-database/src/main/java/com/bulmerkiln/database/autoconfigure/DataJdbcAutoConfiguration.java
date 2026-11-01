/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.autoconfigure;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationDirectory;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.mapper.OrganizationMapperImpl;
import com.bulmerkiln.database.repository.OrganizationRepository;
import com.bulmerkiln.database.service.StoredOrganizationDirectory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

/**
 * Spring Data JDBC over the runtime pool, for every module's repositories, and this module's own.
 * A module enables its repositories with {@code @EnableJdbcRepositories} on an auto-configuration
 * that runs after this one, with {@code enableDefaultTransactions = false}: repositories never open
 * a transaction of their own, and {@code KilnRepository} requires the caller's (DB-11).
 */
@AutoConfiguration(after = DatabaseAutoConfiguration.class)
@EnableJdbcRepositories(basePackageClasses = OrganizationRepository.class, enableDefaultTransactions = false)
public class DataJdbcAutoConfiguration extends AbstractJdbcConfiguration {

    /**
     * The organizations in the database, after creating the ones boot config lists. An invalid
     * slug in the list stops the boot (DB-10).
     */
    @Bean
    @ConditionalOnMissingBean(OrganizationDirectory.class)
    StoredOrganizationDirectory organizationDirectory(
            Database database, OrganizationRepository organizations, OrganizationBootstrapProperties bootstrap) {
        StoredOrganizationDirectory directory =
                new StoredOrganizationDirectory(database, organizations, new OrganizationMapperImpl());
        for (String slug : bootstrap.organizationsOrNone()) {
            if (!Organization.isValidSlug(slug)) {
                throw new IllegalArgumentException(
                        "kiln.bootstrap.organizations lists an invalid or reserved slug: " + slug);
            }
            directory.ensure(slug);
        }
        return directory;
    }
}
