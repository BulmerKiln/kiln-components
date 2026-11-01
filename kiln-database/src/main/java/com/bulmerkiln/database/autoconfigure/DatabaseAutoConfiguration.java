/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.autoconfigure;

import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.SchemaViolationException;
import com.bulmerkiln.database.service.MigrationRunner;
import com.bulmerkiln.database.service.SchemaLinter;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Migrates the schema as the migration role, then opens the runtime pool as the runtime role and
 * refuses to start if the schema isn't organization-safe (DB-6). The pool, the organization-binding
 * transaction manager and the JDBC template are beans for the data layer only (DB-3, ARCH-12).
 */
@AutoConfiguration
@EnableConfigurationProperties({DatabaseProperties.class, OrganizationBootstrapProperties.class})
public class DatabaseAutoConfiguration {

    @Bean
    Database database(DatabaseProperties properties) {
        properties.validate();
        String url = Objects.requireNonNull(properties.getUrl(), "kiln.database.url");
        String username = Objects.requireNonNull(properties.getUsername(), "kiln.database.username");
        String schema = properties.schemaOrDefault();
        String migrator = properties.getMigratorUsername();
        if (properties.migrateOrDefault() && migrator != null) {
            MigrationRunner.migrate(url, migrator, properties.getMigratorPassword(), schema);
        }

        Database database =
                Database.open(url, username, properties.getPassword(), schema, properties.maximumPoolSizeOrDefault());
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

    /** The runtime pool. {@link Database} closes it, so the context doesn't. */
    @Bean(destroyMethod = "")
    DataSource dataSource(Database database) {
        return database.dataSource();
    }

    /** Binds the organization to every transaction {@link Database#transaction} starts (DB-4). */
    @Bean
    PlatformTransactionManager transactionManager(Database database) {
        return database.transactionManager();
    }

    @Bean
    NamedParameterJdbcOperations namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
