/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementException;
import io.helidon.dbclient.DbStatementQuery;
import io.helidon.dbclient.DbStatementType;

/**
 * JDBC implementation of {@link DbStatementDml}.
 */
class JdbcStatementQuery extends JdbcStatement<DbStatementQuery> implements DbStatementQuery {

    /**
     * Create a new instance.
     *
     * @param connectionPool connection pool
     * @param context        context
     */
    JdbcStatementQuery(JdbcConnectionPool connectionPool, JdbcExecuteContext context) {
        super(connectionPool, context);
    }

    @Override
    public DbStatementType statementType() {
        return DbStatementType.GET;
    }

    @Override
    public Stream<DbRow> execute() {
        return doExecute((future, context) -> doExecute(this, future, context, this::closeConnection));
    }

    /**
     * Execute the given statement.
     *
     * @param dbStmt  db statement
     * @param future  query future
     * @param context service context
     * @param onClose onClose handler, may be {@code null}
     * @return query result
     */
    static Stream<DbRow> doExecute(JdbcStatement<? extends DbStatementQuery> dbStmt,
                                   CompletableFuture<Long> future,
                                   DbClientServiceContext context,
                                   Runnable onClose) {

        PreparedStatement statement;
        try {
            statement = dbStmt.prepareStatement(context);
            ResultSet rs = statement.executeQuery();
            JdbcRow.Spliterator spliterator = new JdbcRow.Spliterator(rs, statement, dbStmt.context(), future);
            return autoClose(StreamSupport.stream(spliterator, false)
                    .onClose(() -> {
                        spliterator.close();
                        if (onClose != null) {
                            onClose.run();
                        }
                    }));
        } catch (SQLException ex) {
            dbStmt.closeConnection();
            throw new DbStatementException("Failed to execute Statement", dbStmt.context().statement(), ex);
        }
    }
}
