/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.service;

import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;

/**
 * Runs every module's migrations as the migration role. Each module ships its own under {@code
 * db/kiln/<module>/}, versioned by timestamp (such as {@code V202610271200__organizations.sql}), so
 * modules share one history without their versions colliding (DB-1).
 */
public final class MigrationRunner {

    /** Where every module's migrations live on the classpath. */
    public static final String LOCATION = "classpath:db/kiln";

    private MigrationRunner() {}

    /**
     * Applies pending migrations to {@code schema}, creating it if needed, and returns how many ran.
     * MigrationRunner can use {@code ${flyway:defaultSchema}} for the schema's name.
     */
    public static int migrate(String url, String username, @Nullable String password, String schema) {
        return Flyway.configure()
                .dataSource(url, username, password)
                .locations(LOCATION)
                .schemas(schema)
                .createSchemas(true)
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate()
                .migrationsExecuted;
    }
}
