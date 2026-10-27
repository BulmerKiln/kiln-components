/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database;

import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.core.organization.OrganizationId;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

/**
 * The only way to the database. Every query runs inside one of its transactions: {@link
 * #transaction} for the organization in {@link OrganizationContext}, which it binds to Postgres for
 * row-level security, and {@link #instanceTransaction} for instance tables. The connection pool
 * isn't reachable any other way, so nothing can query without going through them (DB-3).
 */
public final class Database implements AutoCloseable {

    private final HikariDataSource pool;
    private final DSLContext dsl;

    private Database(HikariDataSource pool) {
        this.pool = pool;
        this.dsl = DSL.using(pool, SQLDialect.POSTGRES);
    }

    /**
     * Opens a pool of at most {@code maximumPoolSize} connections as {@code username}, with
     * {@code schema} as the search path. The pool stays inside the returned {@code Database} (DB-3).
     */
    public static Database open(
            String url, String username, @Nullable String password, String schema, int maximumPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("kiln");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setSchema(schema);
        config.setMaximumPoolSize(maximumPoolSize);
        return new Database(new HikariDataSource(config));
    }

    /**
     * Runs {@code work} in one transaction for the current organization. The organization is set
     * with {@code set_config('app.organization_id', id, true)}, so it lasts only until the
     * transaction ends and a pooled connection never carries it into another (DB-4). Outside an
     * organization scope this throws before touching the database (DB-5).
     */
    public <T> T transaction(Function<DSLContext, T> work) {
        OrganizationId organization = OrganizationContext.current().id();
        return dsl.transactionResult(configuration -> {
            DSLContext tx = configuration.dsl();
            tx.select(DSL.function(
                            "set_config",
                            String.class,
                            DSL.inline("app.organization_id"),
                            DSL.val(organization.toString()),
                            DSL.inline(true)))
                    .fetch();
            return work.apply(tx);
        });
    }

    /** As {@link #transaction}, for work that returns nothing. */
    public void run(Consumer<DSLContext> work) {
        transaction(tx -> {
            work.accept(tx);
            return Boolean.TRUE;
        });
    }

    /**
     * Runs {@code work} in one transaction with no organization, for instance tables only. Every
     * organization table refuses it, since its row-level security policy has no organization to
     * compare with (DB-5).
     */
    public <T> T instanceTransaction(Function<DSLContext, T> work) {
        return dsl.transactionResult(configuration -> work.apply(configuration.dsl()));
    }

    @Override
    public void close() {
        pool.close();
    }
}
