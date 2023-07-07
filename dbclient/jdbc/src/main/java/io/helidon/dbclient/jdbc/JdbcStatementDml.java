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
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;

import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementException;

class JdbcStatementDml extends JdbcStatement<DbStatementDml> implements DbStatementDml {

    private JdbcStatementDml(StatementContext context) {
        super(context);
    }

    @Override
    public long execute() {
        // Run interceptors before statement execution
        CompletableFuture<Void> statementFuture = new CompletableFuture<>();
        CompletableFuture<Long> queryFuture = new CompletableFuture<>();
        JdbcClientServiceContext serrviceContext = prepare().createServiceContext()
                .statementFuture(statementFuture)
                .resultFuture(queryFuture);
        context().clientContext().clientServices().forEach(service -> service.statement(serrviceContext));
        // Execute the statement
        try (Connection connection = context().connectionPool().connection();
                Statement statement = prepare().createStatement(connection)) {
            long result = prepare().executeUpdate();
            // Complete interceptor futures
            statementFuture.complete(null);
            queryFuture.complete(result);
            return result;
        } catch (SQLException ex) {
            throw new DbStatementException("Failed to execute Statement", context().statement(), ex);
        }
    }

    static JdbcStatementDml create(StatementContext context) {
        return new JdbcStatementDml(context);
    }

}
