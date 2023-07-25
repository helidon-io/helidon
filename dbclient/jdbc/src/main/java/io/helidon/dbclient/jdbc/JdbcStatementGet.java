/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementException;
import io.helidon.dbclient.DbStatementGet;
import io.helidon.dbclient.DbStatementType;

/**
 * JDBC implementation of {@link DbStatementGet}.
 */
class JdbcStatementGet extends JdbcStatement<DbStatementGet> implements DbStatementGet {

    /**
     * Create a new instance.
     *
     * @param connectionPool connection pool
     * @param context        execution context
     */
    JdbcStatementGet(JdbcConnectionPool connectionPool, JdbcExecuteContext context) {
        super(connectionPool, context);
    }

    @Override
    public DbStatementType statementType() {
        return DbStatementType.GET;
    }

    @Override
    public Optional<DbRow> execute() {
        return doExecute((future, context) -> {
            try {
                return doExecute(this, future, context);
            } finally {
                closeConnection();
            }
        });
    }

    /**
     * Execute the given statement.
     *
     * @param dbStmt  db statement
     * @param future  query future
     * @param context service context
     * @return query result
     */
    static Optional<DbRow> doExecute(JdbcStatement<? extends DbStatementGet> dbStmt,
                                     CompletableFuture<Long> future,
                                     DbClientServiceContext context) {

        try (PreparedStatement statement = dbStmt.prepareStatement(context);
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                Optional<DbRow> result = Optional.of(JdbcRow.create(rs, dbStmt.context()));
                future.complete(1L);
                return result;
            } else {
                future.complete(0L);
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DbStatementException("Failed to execute Statement", dbStmt.context().statement(), ex);
        }
    }
}
