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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementException;
import io.helidon.dbclient.DbStatementGet;

class JdbcStatementGet extends JdbcStatement<DbStatementGet> implements DbStatementGet {

    private JdbcStatementGet(StatementContext context) {
        super(context);
    }

    @Override
    @SuppressWarnings("unused")
    public Optional<DbRow> execute() {
        // Run interceptors before statement execution
        CompletableFuture<Void> statementFuture = new CompletableFuture<>();
        CompletableFuture<Long> queryFuture = new CompletableFuture<>();
        JdbcClientServiceContext serviceContext = prepare().createServiceContext()
                .statementFuture(statementFuture)
                .resultFuture(queryFuture);
        context().clientContext().clientServices().forEach(service -> service.statement(serviceContext));
        // Execute the statement
        try (Connection connection = context().connectionPool().connection();
                Statement statement = prepare().createStatement(connection);
                ResultSet rs = prepare().executeQuery()) {
            statementFuture.complete(null);
            if (rs.next()) {
                Optional<DbRow> result =  Optional.of(JdbcRow.create(rs,
                                                  context().dbMapperManager(),
                                                  context().mapperManager()));
                queryFuture.complete(1L);
                return result;
            } else {
                queryFuture.complete(0L);
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DbStatementException("Failed to execute Statement", context().statement(), ex);
        }
    }

    static JdbcStatementGet create(StatementContext context) {
        return new JdbcStatementGet(context);
    }

}
