/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.dbclient.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbStatement;

/**
 * JDBC transactional statement base implementation.
 *
 * @param <S> type of subclass
 */
abstract class JdbcTransactionStatement<S extends DbStatement<S>> extends JdbcStatement<S> {

    private final TransactionContext transactionContext;

    /**
     * Create a new instance.
     *
     * @param connectionPool     connection pool
     * @param context            context
     * @param transactionContext transaction context
     */
    protected JdbcTransactionStatement(JdbcConnectionPool connectionPool,
                                       JdbcExecuteContext context,
                                       TransactionContext transactionContext) {
        super(connectionPool, context);
        this.transactionContext = transactionContext;
    }

    @Override
    protected PreparedStatement prepareStatement(String stmtName, String stmt) {
        Connection connection = transactionContext.connection();
        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new DbClientException("Failed to set autocommit to false", e);
        }
        return prepareStatement(connection, stmtName, stmt);
    }

}

