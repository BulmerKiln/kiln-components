/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.database;

import com.bulmerkiln.core.organization.Organization;
import java.io.Serial;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * Spring's JDBC transaction manager, binding the organization to each transaction that names one
 * (DB-4). Only {@link Database#transaction} names one; every other transaction, including a bare
 * {@code @Transactional}, binds nothing, so organization tables refuse it (DB-5).
 */
final class OrganizationTransactionManager extends JdbcTransactionManager {

    @Serial
    private static final long serialVersionUID = 1L;

    OrganizationTransactionManager(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected void prepareTransactionalConnection(Connection connection, TransactionDefinition definition)
            throws SQLException {
        super.prepareTransactionalConnection(connection, definition);
        if (definition instanceof OrganizationTransaction transaction) {
            bind(connection, transaction.organization());
        }
    }

    /** Transaction-local, so the setting ends with the transaction and never reaches the next borrower. */
    private static void bind(Connection connection, Organization organization) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT set_config('app.organization_id', ?, true)")) {
            statement.setString(1, organization.getId().toString());
            statement.execute();
        }
    }
}
