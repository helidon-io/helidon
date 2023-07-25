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

import java.sql.SQLException;

import io.helidon.dbclient.DbClientContext;
import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementGet;
import io.helidon.dbclient.DbStatementQuery;
import io.helidon.dbclient.DbTransaction;

import static io.helidon.dbclient.DbStatementType.DELETE;
import static io.helidon.dbclient.DbStatementType.DML;
import static io.helidon.dbclient.DbStatementType.INSERT;
import static io.helidon.dbclient.DbStatementType.UPDATE;

/**
 * JDBC implementation of {@link DbTransaction}.
 */
class JdbcTransaction extends JdbcExecute implements DbTransaction {

    private final TransactionContext transactionContext;

    /**
     * Create a new instance.
     *
     * @param context        context
     * @param connectionPool connection pool
     */
    JdbcTransaction(DbClientContext context, JdbcConnectionPool connectionPool) {
        super(context, connectionPool);
        this.transactionContext = new TransactionContext(connectionPool::connection);
    }

    @Override
    public DbStatementQuery createNamedQuery(String stmtName, String stmt) {
        return new JdbcTransactionStatementQuery(
                connectionPool(),
                JdbcExecuteContext.jdbcCreate(stmtName, stmt, jdbcContext()),
                transactionContext);
    }

    @Override
    public DbStatementGet createNamedGet(String statementName, String statement) {
        return new JdbcTransactionStatementGet(
                connectionPool(),
                JdbcExecuteContext.jdbcCreate(statementName, statement, jdbcContext()),
                transactionContext);
    }

    @Override
    public DbStatementDml createNamedDmlStatement(String statementName, String statement) {
        return new JdbcTransactionStatementDml(
                connectionPool(),
                JdbcExecuteContext.jdbcCreate(statementName, statement, jdbcContext()),
                transactionContext,
                DML);
    }

    @Override
    public DbStatementDml createNamedInsert(String statementName, String statement) {
        return new JdbcTransactionStatementDml(
                connectionPool(),
                JdbcExecuteContext.jdbcCreate(statementName, statement, jdbcContext()),
                transactionContext,
                INSERT);
    }

    @Override
    public DbStatementDml createNamedUpdate(String stmtName, String stmt) {
        return new JdbcTransactionStatementDml(
                connectionPool(),
                JdbcExecuteContext.jdbcCreate(stmtName, stmt, jdbcContext()),
                transactionContext,
                UPDATE);
    }

    @Override
    public DbStatementDml createNamedDelete(String statementName, String statement) {
        return new JdbcTransactionStatementDml(
                connectionPool(),
                JdbcExecuteContext.jdbcCreate(statementName, statement, jdbcContext()),
                transactionContext,
                DELETE);
    }

    @Override
    public void commit() {
        try {
            transactionContext.commit();
        } catch (SQLException ex) {
            throw new DbClientException("Failed to commit transaction", ex);
        }
    }

    @Override
    public void rollback() {
        try {
            transactionContext.rollback();
        } catch (SQLException ex) {
            throw new DbClientException("Failed to rollback transaction", ex);
        }
    }
}
