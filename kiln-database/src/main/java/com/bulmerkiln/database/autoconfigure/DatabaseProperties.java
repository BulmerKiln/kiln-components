/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database.autoconfigure;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The database settings, under {@code kiln.database}.
 *
 * @param url the JDBC URL; only PostgreSQL is supported
 * @param username the runtime role, {@code kiln_app}: no owner, no superuser, no {@code BYPASSRLS}
 * @param password the runtime role's password
 * @param migratorUsername the role that owns the schema and runs migrations, {@code kiln_migrator}
 * @param migratorPassword the migrator's password
 * @param schema the schema Kiln's tables live in; {@code kiln} unless set
 * @param migrate whether to run migrations at startup; on unless set to {@code false}
 * @param maximumPoolSize the runtime connection pool's size; 10 unless set
 */
@ConfigurationProperties("kiln.database")
public record DatabaseProperties(
        @Nullable String url,
        @Nullable String username,
        @Nullable String password,
        @Nullable String migratorUsername,
        @Nullable String migratorPassword,
        @Nullable String schema,
        @Nullable Boolean migrate,
        @Nullable Integer maximumPoolSize) {

    /** Lowercase, so it never needs quoting in SQL. */
    private static final Pattern SCHEMA = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    public String schemaOrDefault() {
        return schema == null ? "kiln" : schema;
    }

    public boolean migrateOrDefault() {
        return migrate == null || migrate;
    }

    public int maximumPoolSizeOrDefault() {
        return maximumPoolSize == null ? 10 : maximumPoolSize;
    }

    /** Checks the settings before any connection is made (DB-8). */
    public void validate() {
        validateRoles();
        if (!SCHEMA.matcher(schemaOrDefault()).matches()) {
            throw new IllegalArgumentException("kiln.database.schema must be a lowercase SQL identifier");
        }
        if (maximumPoolSizeOrDefault() < 1) {
            throw new IllegalArgumentException("kiln.database.maximum-pool-size must be at least 1");
        }
    }

    private void validateRoles() {
        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("kiln.database.url must be a jdbc:postgresql:// URL");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("kiln.database.username must name the runtime role");
        }
        if (migrateOrDefault() && (migratorUsername == null || migratorUsername.isBlank())) {
            throw new IllegalArgumentException(
                    "kiln.database.migrator-username must name the migration role, or set kiln.database.migrate=false");
        }
        if (username.equals(migratorUsername)) {
            throw new IllegalArgumentException("kiln.database.username and migrator-username must be different roles");
        }
    }
}
