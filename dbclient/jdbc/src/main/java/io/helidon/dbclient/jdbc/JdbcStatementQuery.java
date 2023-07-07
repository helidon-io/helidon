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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementException;
import io.helidon.dbclient.DbStatementQuery;

class JdbcStatementQuery extends JdbcStatement<DbStatementQuery> implements DbStatementQuery {

    private JdbcStatementQuery(StatementContext context) {
        super(context);
    }

    @Override
    public Stream<DbRow> execute() {
        // Run interceptors before statement execution
        CompletableFuture<Void> statementFuture = new CompletableFuture<>();
        CompletableFuture<Long> queryFuture = new CompletableFuture<>();
        JdbcClientServiceContext serviceContext = prepare().createServiceContext()
                .statementFuture(statementFuture)
                .resultFuture(queryFuture);
        context().clientContext().clientServices().forEach(service -> service.statement(serviceContext));
        // Execute the statement
        Connection connection = context().connectionPool().connection();
        Statement statement;
        try {
            statement = prepare().createStatement(connection);
        } catch (SQLException ex) {
            closeConnection(connection, context().statement());
            throw new DbStatementException("Failed to create Statement", context().statement(), ex);
        }
        ResultSet rs;
        try {
            rs = prepare().executeQuery();
            statementFuture.complete(null);
        } catch (SQLException ex) {
            closeStatement(statement, context().statement());
            closeConnection(connection, context().statement());
            throw new DbStatementException("Failed to execute Statement", context().statement(), ex);
        }
        JdbcRow.DbRowSpliterator iterator = new JdbcRow.DbRowSpliterator(rs, context(), context().statement());
        return StreamSupport.stream(iterator, false)
                .onClose(new CloseResources(connection, statement, rs, context().statement(), iterator, queryFuture));
    }

    // Close Connection and wrap any SQLException with DbStatementException
    private static void closeConnection(Connection connection, String statementString) {
        try {
            connection.close();
        } catch (SQLException ex) {
            throw new DbStatementException("Failed to close connection", statementString, ex);
        }
    }

    // Close Statement and wrap any SQLException with DbStatementException
    static void closeStatement(Statement statement, String statementString) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException ex) {
                throw new DbStatementException("Failed to close Statement", statementString, ex);
            }
        }
    }

    // Close ResultSet and wrap any SQLException with DbStatementException
    static void closeResultSet(ResultSet rs, String statementString) {
        try {
            rs.close();
        } catch (SQLException ex) {
            throw new DbStatementException("Failed to close ResultSet", statementString, ex);
        }
    }

    static JdbcStatementQuery create(StatementContext context) {
        return new JdbcStatementQuery(context);
    }


    private record CloseResources(Connection connection,
                                  Statement statement,
                                  ResultSet rs,
                                  String statementString,
                                  JdbcRow.DbRowSpliterator iterator,
                                  CompletableFuture<Long> queryFuture) implements Runnable {
        @Override
        public void run() throws DbStatementException {
            queryFuture.complete(iterator.count());
            try {
                closeResultSet(rs, statementString);
            } finally {
                try {
                    closeStatement(statement, statementString);
                } finally {
                    closeConnection(connection, statementString);
                }
            }
        }

    }

}
