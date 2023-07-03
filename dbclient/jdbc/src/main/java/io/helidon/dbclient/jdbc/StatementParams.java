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
import java.util.function.Consumer;

class StatementParams extends JdbcStatement.Params {

    private final StatementContext context;
    private final Consumer<JdbcStatement.Params> update;

    private Statement statement;

    StatementParams(StatementContext context, Consumer<JdbcStatement.Params> update) {
        this.context = context;
        this.update = update;
        this.statement = null;
    }

    // Create statement and store it in this instance for execution
    @Override
    public Statement createStatement(Connection connection) throws SQLException {
        statement = connection.createStatement();
        return statement;
    }

    @Override
    public long executeUpdate() throws SQLException {
        return statement.executeUpdate(context.statement());
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        return statement.executeQuery(context.statement());
    }

    // Requested indexed statement params.
    // Create indexed params statement and set it to JdbcStatement instance.
    @Override
    public StatementIndexedParams indexed() {
        StatementIndexedParams statement = StatementIndexedParams.create(context);
        update.accept(statement);
        return statement;
    }

    // Requested named statement params.
    // Create named params statement and set it to JdbcStatement instance.
    @Override
    public StatementNamedParams named() {
        StatementNamedParams statement = StatementNamedParams.create(context);
        update.accept(statement);
        return statement;
    }

    @Override
    public State state() {
        return State.INIT;
    }

    // Create interceptor context
    @Override
    JdbcClientServiceContext.NoParams createServiceContext() {
        return new JdbcClientServiceContext.NoParams(context, this);
    }

}
