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

class StatementIndexedParams extends JdbcStatement.Params {

    private final StatementContext context;
    private PreparedStatement statement;
    // Parameters values set by user.
    // Each supported value type has its own handler to use proper PreparedStatement set method for its type.
    private List<ParameterValueHandler> parameters;


    private StatementIndexedParams(StatementContext context) {
        this.context = context;
        this.statement = null;
        this.parameters = new LinkedList<>();
    }

    void addParam(ParameterValueHandler handler) {
        parameters.add(handler);
    }

    // Create statement and store it in this instance for execution
    @Override
    PreparedStatement createStatement(Connection connection) throws SQLException {
        statement = connection.prepareStatement(context.statement());
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
    long executeUpdate() throws SQLException {
        return statement.executeUpdate();
    }

    @Override
    ResultSet executeQuery() throws SQLException {
        return statement.executeQuery();
    }

    @Override
    StatementIndexedParams indexed() {
        return this;
    }

    @Override
    StatementNamedParams named() {
        throw new IllegalStateException("Cannot set named parameters when indexed were already chosen");
    }

    @Override
    State state() {
        return State.INDEXED;
    }

    // Create interceptor context
    @Override
    JdbcClientServiceContext.Indexed createServiceContext() {
        return new JdbcClientServiceContext.Indexed(context, this);
    }

    // Return parameters as list for interceptor
    List<Object> parametersAsList() {
        return parameters.stream().map(handler -> handler.rawValue()).toList();
    }

    // Update parameters from interceptor
    void parametersFromList(List<Object> parametersList) {
        parameters = parametersList.stream().map(value -> ParameterValueHandler.handlerOf(value)).toList();
    }

    static StatementIndexedParams create(StatementContext context) {
        return new StatementIndexedParams(context);
    }

}
