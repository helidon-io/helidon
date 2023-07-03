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
import java.sql.SQLException;

import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementGet;
import io.helidon.dbclient.DbStatementQuery;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.DbTransaction;
import io.helidon.dbclient.common.CommonClientContext;

class JdbcTransaction extends JdbcExecute implements DbTransaction {

    /** Local logger instance. */
    private static final System.Logger LOGGER = System.getLogger(JdbcTransaction.class.getName());

    private final TransactionContext transactionContext;


    private JdbcTransaction(CommonClientContext context, JdbcConnectionPool connectionPool) {
        super(context, connectionPool);
        this.transactionContext = new TransactionContext(this::createConnection);
    }

    @Override
    public DbStatementQuery createNamedQuery(String statementName, String statement) {
        return JdbcTransactionStatementQuery.create(
                StatementContext.create(statementName, statement, DbStatementType.QUERY, context(), connectionPool()),
                transactionContext);
    }

    @Override
    public DbStatementGet createNamedGet(String statementName, String statement) {
        return JdbcTransactionStatementGet.create(
                StatementContext.create(statementName, statement, DbStatementType.GET, context(), connectionPool()),
                transactionContext);
    }

    @Override
    public DbStatementDml createNamedDmlStatement(String statementName, String statement) {
        return JdbcTransactionStatementDml.create(
                StatementContext.create(statementName, statement, DbStatementType.DML, context(), connectionPool()),
                transactionContext);
    }

    @Override
    public DbStatementDml createNamedInsert(String statementName, String statement) {
        return JdbcTransactionStatementDml.create(
                StatementContext.create(statementName, statement, DbStatementType.INSERT, context(), connectionPool()),
                transactionContext);
    }

    @Override
    public DbStatementDml createNamedUpdate(String statementName, String statement) {
        return JdbcTransactionStatementDml.create(
                StatementContext.create(statementName, statement, DbStatementType.UPDATE, context(), connectionPool()),
                transactionContext);
    }

    @Override
    public DbStatementDml createNamedDelete(String statementName, String statement) {
        return JdbcTransactionStatementDml.create(
                StatementContext.create(statementName, statement, DbStatementType.DELETE, context(), connectionPool()),
                transactionContext);
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

    // Connection factory for TransactionContext
    /**
     * Create new database connection.
     * Returns new connection from {@link JdbcConnectionPool}. This connection must be closed
     * at the end of the transaction.
     *
     * @return database connection
     */
    private Connection createConnection() {
        return connectionPool().connection();
    }

    static JdbcTransaction create(CommonClientContext context, JdbcConnectionPool connectionPool) {
        return new JdbcTransaction(context, connectionPool);
    }

}
