/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.autoconfigure;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationDirectory;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.SchemaViolationException;
import com.bulmerkiln.database.service.MigrationRunner;
import com.bulmerkiln.database.service.SchemaLinter;
import com.bulmerkiln.database.store.StoredOrganizationDirectory;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Migrates the schema as the migration role, then opens the runtime pool as the runtime role and
 * refuses to start if the schema isn't organization-safe (DB-6). Only {@link Database} is a bean;
 * the pool isn't, so nothing can query around it (DB-3).
 */
@AutoConfiguration
@EnableConfigurationProperties({DatabaseProperties.class, OrganizationBootstrapProperties.class})
public class DatabaseAutoConfiguration {

    @Bean
    Database database(DatabaseProperties properties) {
        properties.validate();
        String url = Objects.requireNonNull(properties.url(), "kiln.database.url");
        String username = Objects.requireNonNull(properties.username(), "kiln.database.username");
        String schema = properties.schemaOrDefault();
        String migrator = properties.migratorUsername();
        if (properties.migrateOrDefault() && migrator != null) {
            MigrationRunner.migrate(url, migrator, properties.migratorPassword(), schema);
        }

        Database database =
                Database.open(url, username, properties.password(), schema, properties.maximumPoolSizeOrDefault());
        List<String> problems;
        try {
            problems = database.instanceTransaction(SchemaLinter::problems);
        } catch (RuntimeException e) {
            database.close();
            throw e;
        }
        if (!problems.isEmpty()) {
            database.close();
            throw new SchemaViolationException(problems);
        }
        return database;
    }

    /**
     * The organizations in the database, after creating the ones boot config lists. An invalid
     * slug in the list stops the boot (DB-10).
     */
    @Bean
    @ConditionalOnMissingBean(OrganizationDirectory.class)
    StoredOrganizationDirectory organizationDirectory(Database database, OrganizationBootstrapProperties bootstrap) {
        StoredOrganizationDirectory directory = new StoredOrganizationDirectory(database);
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
