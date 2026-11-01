/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.service;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationDirectory;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.KilnPostgres;
import com.bulmerkiln.database.service.MigrationRunner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A fresh database migrated by the migrator and used as Kiln's runtime role, with organizations
 * acme and globex, and a clock tests move by hand.
 */
public final class UserDatabase implements AutoCloseable {

    public static final Organization ACME = new Organization(new OrganizationId(UUID.randomUUID()), "acme");
    public static final Organization GLOBEX = new Organization(new OrganizationId(UUID.randomUUID()), "globex");

    private final Database database;
    private final TestClock clock = new TestClock();
    private final OrganizationDirectory organizations = new OrganizationDirectory() {
        @Override
        public Optional<Organization> byId(OrganizationId id) {
            return all().stream().filter(o -> o.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<Organization> bySlug(String slug) {
            return all().stream().filter(o -> o.getSlug().equals(slug)).findFirst();
        }

        @Override
        public List<Organization> all() {
            return List.of(ACME, GLOBEX);
        }
    };

    public UserDatabase() {
        String url = KilnPostgres.newDatabase();
        MigrationRunner.migrate(url, KilnPostgres.MIGRATOR, KilnPostgres.MIGRATOR_PASSWORD, "kiln");
        database = Database.open(url, KilnPostgres.APP, KilnPostgres.APP_PASSWORD, "kiln", 2);
        database.instanceTransaction(db -> {
            for (Organization organization : organizations.all()) {
                db.execute(
                        "INSERT INTO organizations (id, slug, display_name) VALUES (?, ?, ?)",
                        organization.getId().getValue(),
                        organization.getSlug(),
                        organization.getSlug());
            }
            return Boolean.TRUE;
        });
    }

    public Database database() {
        return database;
    }

    public TestClock clock() {
        return clock;
    }

    public OrganizationDirectory organizations() {
        return organizations;
    }

    /** Counts rows in an instance table. */
    public int count(String table) {
        return database.instanceTransaction(db -> db.fetchCount(db.selectFrom(table)));
    }

    @Override
    public void close() {
        database.close();
    }

    /** A clock that stands still until a test moves it. */
    public static final class TestClock extends Clock {

        private Instant now = Instant.parse("2026-10-30T12:00:00Z");

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
