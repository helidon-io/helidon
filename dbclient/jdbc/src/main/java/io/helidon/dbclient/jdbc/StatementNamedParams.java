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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.helidon.dbclient.DbNamedParameterException;

/**
 * Extends JDBC {@link PreparedStatement} with named parameters support.
 */
class StatementNamedParams extends JdbcStatement.Params {

    /** Local logger instance. */
    private static final System.Logger LOGGER = System.getLogger(StatementNamedParams.class.getName());

    // JDBC execution context bound with statement.
    private final StatementContext context;
    private PreparedStatement statement;
    // Parameters values set by user.
    // Each supported value type has its own handler to use proper PreparedStatement set method for its type.
    private Map<String, ParameterValueHandler> parameters;

    // Instance shall be always created by factory method.
    private StatementNamedParams(StatementContext context) {
        this.context = context;
        this.statement = null;
        parameters = new HashMap<>();
    }

    void addParam(String name, ParameterValueHandler handler) {
        parameters.put(name, handler);
    }

    // Create statement and store it in this instance for execution
    @Override
    PreparedStatement createStatement(Connection connection) throws SQLException {
        NamedStatementParser parser = new NamedStatementParser(context.statement());
        String jdbcStatement = parser.convert();
        statement = connection.prepareStatement(jdbcStatement);
        List<String> namesOrder = parser.namesOrder();
        // Index starts from 1
        int i = 1;
        // Walk through query parameters names order list
        for (String name : namesOrder) {
            if (parameters.containsKey(name)) {
                ParameterValueHandler handler = parameters.get(name);
                try {
                    handler.set(statement, i);
                } catch (SQLException ex) {
                    throw new DbNamedParameterException("Failed to set named parameter", name, context.statement(), ex);
                }
                LOGGER.log(System.Logger.Level.DEBUG,
                        String.format("Mapped parameter %d: %s -> %s", i, name, handler.valueToString()));
                i++;
                // Throw an exception when some parameter name was not set.
            } else {
                throw new DbNamedParameterException(
                        String.format("Missing named parameter %s from the statement", name), name, context.statement());
            }
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
        throw new IllegalStateException("Cannot set indexed parameters when named were already chosen");
    }

    @Override
    StatementNamedParams named() {
        return this;
    }

    @Override
    State state() {
        return State.NAMED;
    }

    // Create interceptor context
    @Override
    JdbcClientServiceContext.Named createServiceContext() {
        return new JdbcClientServiceContext.Named(context, this);
    }

    // Return parameters as list for interceptor
    Map<String, Object> parametersAsMap() {
        return parameters.entrySet().stream().collect(
                Collectors.toMap(e -> e.getKey(), e -> e.getValue().rawValue()));
    }

    void parametersFromMap(Map<String, Object> parametersMap) {
        parameters = parametersMap.entrySet().stream().collect(
                Collectors.toMap(e -> e.getKey(), e -> ParameterValueHandler.handlerOf(e.getValue())));
    }

    /**
     * Creates an instance of JDBC {@link PreparedStatement} with named parameters support.
     *
     * @param context JDBC statement context
     * @return new instance of JDBC {@link PreparedStatement} with named parameters support
     */
    static StatementNamedParams create(StatementContext context) {
        return new StatementNamedParams(context);
    }

}
