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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementException;
import io.helidon.dbclient.DbStatementQuery;

class JdbcTransactionStatementQuery extends JdbcTransactionStatement<DbStatementQuery> implements DbStatementQuery {

    private JdbcTransactionStatementQuery(StatementContext context, TransactionContext transactionContext) {
        super(context, transactionContext);
    }

    @Override
    public Stream<DbRow> execute() {
        Connection connection = transactionContext().connection();
        Statement statement;
        try {
            statement = prepare().createStatement(connection);
        } catch (SQLException ex) {
            throw new DbStatementException("Failed to create Statement", context().statement(), ex);
        }
        ResultSet rs;
        try {
            rs = prepare().executeQuery();
        } catch (SQLException ex) {
            JdbcStatementQuery.closeStatement(statement, context().statement());
            throw new DbStatementException("Failed to execute Statement", context().statement(), ex);
        }
        return StreamSupport.stream(new JdbcRow.DbRowSpliterator(rs, context(), context().statement()), false)
                .onClose(new CloseResources(statement, rs, context().statement()));
    }

    static JdbcTransactionStatementQuery create(StatementContext context, TransactionContext transactionContext) {
        return new JdbcTransactionStatementQuery(context, transactionContext);
    }

    private record CloseResources(Statement statement, ResultSet rs, String statementString) implements Runnable {
        @Override
        public void run() throws DbStatementException {
            try {
                JdbcStatementQuery.closeResultSet(rs, statementString);
            } finally {
                JdbcStatementQuery.closeStatement(statement, statementString);
            }
        }

    }

}
