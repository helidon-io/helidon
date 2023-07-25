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
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementException;
import io.helidon.dbclient.DbStatementType;

/**
 * JDBC implementation of {@link DbStatementDml}.
 */
class JdbcStatementDml extends JdbcStatement<DbStatementDml> implements DbStatementDml {

    private final DbStatementType type;

    /**
     * Create a new instance.
     *
     * @param connectionPool connection pool
     * @param context        execution context
     */
    JdbcStatementDml(JdbcConnectionPool connectionPool, DbStatementType type, JdbcExecuteContext context) {
        super(connectionPool, context);
        this.type = type;
    }

    @Override
    public DbStatementType statementType() {
        return type;
    }

    @Override
    public long execute() {
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
    static long doExecute(JdbcStatement<? extends DbStatementDml> dbStmt,
                          CompletableFuture<Long> future,
                          DbClientServiceContext context) {

        try (PreparedStatement statement = dbStmt.prepareStatement(context)) {
            long result = statement.executeUpdate();
            future.complete(result);
            return result;
        } catch (SQLException ex) {
            throw new DbStatementException("Failed to execute statement", dbStmt.context().statement(), ex);
        }
    }
}
