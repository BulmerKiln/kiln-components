/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.core.organization.OrganizationId;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * Kiln's transactions. Every query runs inside one of them: {@link #transaction} for the
 * organization in {@link OrganizationContext}, which it binds to Postgres for row-level security,
 * and {@link #instanceTransaction} for instance tables (DB-3).
 *
 * <p>Repositories run in the {@code Supplier} forms, through the Spring transaction manager that
 * binds the organization; the forms taking a jOOQ {@code DSLContext} remain while modules move to
 * repositories, and go when the last one has.
 */
public final class Database implements AutoCloseable {

    private final HikariDataSource pool;
    private final DSLContext dsl;
    private final OrganizationTransactionManager transactions;

    private Database(HikariDataSource pool) {
        this.pool = pool;
        this.dsl = DSL.using(pool, SQLDialect.POSTGRES);
        this.transactions = new OrganizationTransactionManager(pool);
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
        OrganizationId organization = OrganizationContext.current().getId();
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

    /**
     * Runs {@code work} in one Spring transaction for the current organization, which the
     * transaction manager binds as {@link #transaction(Function)} does (DB-4). Outside an
     * organization scope this throws before touching the database (DB-5). Joins the caller's
     * transaction if there is one.
     */
    public <T> T transaction(Supplier<T> work) {
        Organization organization = OrganizationContext.current();
        return execute(new OrganizationTransaction(organization), work);
    }

    /** As {@link #transaction(Supplier)}, for work that returns nothing. */
    public void run(Runnable work) {
        transaction(() -> {
            work.run();
            return Boolean.TRUE;
        });
    }

    /**
     * Runs {@code work} in one Spring transaction with no organization, for instance tables only;
     * every organization table refuses it (DB-5). Joins the caller's transaction if there is one.
     */
    public <T> T instanceTransaction(Supplier<T> work) {
        return execute(new DefaultTransactionDefinition(), work);
    }

    /** The pool, for the data layer's beans only (ARCH-12). */
    public DataSource dataSource() {
        return pool;
    }

    /** The transaction manager that binds the organization, for the data layer's beans only. */
    public PlatformTransactionManager transactionManager() {
        return transactions;
    }

    /**
     * Begins, commits or rolls back exactly as {@code TransactionTemplate} does, but hands the manager
     * this definition itself: the template copies a definition's settings and passes itself, which
     * would hide the organization from the manager.
     */
    private <T> T execute(TransactionDefinition definition, Supplier<T> work) {
        TransactionStatus status = transactions.getTransaction(definition);
        T result;
        try {
            result = work.get();
        } catch (RuntimeException | Error e) {
            transactions.rollback(status);
            throw e;
        }
        transactions.commit(status);
        if (result == null) {
            throw new IllegalStateException("A transaction's work must return a value; use run for none");
        }
        return result;
    }

    @Override
    public void close() {
        pool.close();
    }
}
