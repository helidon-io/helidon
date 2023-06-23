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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import io.helidon.dbclient.DbIndexedParameterException;

class StatementIndexedParams implements JdbcStatement.Builder {

    // List of parameters to be used in the query execution
    private final List<ParameterValueHandler> parameters;
    private final StatementContext context;
    private PreparedStatement statement;


    private StatementIndexedParams(StatementContext context) {
        this.parameters = new LinkedList<>();
        this.context = context;
        this.statement = null;
    }

    void addParam(ParameterValueHandler handler) {
        parameters.add(handler);
    }

    // Create statement and store it in this instance for execution
    @Override
    public PreparedStatement createStatement(Connection connection) throws SQLException {
        statement = connection.prepareStatement(context.statement());
        return statement;
    }

    @Override
    public long executeUpdate() throws SQLException {
        return prepareStatement(statement).executeUpdate();
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        return prepareStatement(statement).executeQuery();
    }

    // Set PreparedStatement parameters before statement is executed.
    PreparedStatement prepareStatement(PreparedStatement statement) {
        // Index starts from 1
        int i = 1;
        for (ParameterValueHandler handler : parameters) {
            try {
                handler.set(statement, i);
            } catch (SQLException ex) {
                throw new DbIndexedParameterException("Failed to set indexed parameter", i, context.statement(), ex);
            }
            i++;
        }
        return statement;
    }

    @Override
    public StatementIndexedParams indexed() {
        return this;
    }

    @Override
    public StatementNamedParams named() {
        throw new IllegalStateException("Cannot set named parameters when indexed were already chosen");
    }

    @Override
    public State state() {
        return State.INDEXED;
    }

    static StatementIndexedParams create(StatementContext context) {
        return new StatementIndexedParams(context);
    }

}
