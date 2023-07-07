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

import java.lang.System.Logger.Level;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbExecuteContext;
import io.helidon.dbclient.DbIndexedStatementParameters;
import io.helidon.dbclient.DbNamedStatementParameters;
import io.helidon.dbclient.DbStatement;
import io.helidon.dbclient.DbStatementBase;
import io.helidon.dbclient.DbStatementParameters;

/**
 * JDBC statement base implementation.
 *
 * @param <S> type of subclass
 */
public abstract class JdbcStatement<S extends DbStatement<S>> extends DbStatementBase<S> {

    private static final System.Logger LOGGER = System.getLogger(JdbcStatement.class.getName());

    private final JdbcConnectionPool connectionPool;
    private Connection connection;

    /**
     * Create a new instance.
     *
     * @param connectionPool connection pool
     * @param context        context
     */
    JdbcStatement(JdbcConnectionPool connectionPool, DbExecuteContext context) {
        super(context);
        this.connectionPool = connectionPool;
    }

    /**
     * Close the connection.
     */
    void closeConnection() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, String.format("Could not close connection: %s", e.getMessage()), e);
        }
    }

    /**
     * Create the {@link PreparedStatement}.
     *
     * @param serviceContext client service context
     * @return PreparedStatement
     */
    protected PreparedStatement prepareStatement(DbClientServiceContext serviceContext) {
        String stmtName = serviceContext.statementName();
        String stmt = serviceContext.statement();
        DbStatementParameters stmtParams = serviceContext.statementParameters();
        LOGGER.log(Level.DEBUG, () -> String.format("Building SQL statement: %s", stmt));
        if (stmtParams instanceof DbIndexedStatementParameters indexed) {
            List<Object> params = indexed.parameters();
            return prepareIndexedStatement(stmtName, stmt, params);
        } else if (stmtParams instanceof DbNamedStatementParameters named) {
            Map<String, Object> params = named.parameters();
            return prepareNamedStatement(stmtName, stmt, params);
        }
        return prepareStatement(stmtName, stmt);
    }

    /**
     * Create the {@link PreparedStatement}.
     *
     * @param stmtName statement name
     * @param stmt     statement text
     * @return statement
     */
    protected PreparedStatement prepareStatement(String stmtName, String stmt) {
        try {
            connection = connectionPool.connection();
            return connection.prepareStatement(stmt);
        } catch (SQLException e) {
            throw new DbClientException(String.format("Failed to prepare statement: %s", stmtName), e);
        }
    }

    private PreparedStatement prepareNamedStatement(String stmtName, String stmt, Map<String, Object> parameters) {
        PreparedStatement preparedStatement = null;
        try {
            // Parameters names must be replaced with ? and names occurrence order must be stored.
            NamedStatementParser parser = new NamedStatementParser(stmt);
            String convertedStmt = parser.convert();
            LOGGER.log(Level.TRACE, () -> String.format("Converted statement: %s", convertedStmt));
            preparedStatement = prepareStatement(stmtName, convertedStmt);
            List<String> namesOrder = parser.namesOrder();
            // Set parameters into prepared statement
            int i = 1;
            for (String name : namesOrder) {
                if (parameters.containsKey(name)) {
                    Object value = parameters.get(name);
                    LOGGER.log(Level.TRACE, String.format("Mapped parameter %d: %s -> %s", i, name, value));
                    preparedStatement.setObject(i, value);
                    i++;
                } else {
                    throw new DbClientException(namedStatementErrorMessage(namesOrder, parameters));
                }
            }
            return preparedStatement;
        } catch (SQLException e) {
            closePreparedStatement(preparedStatement);
            throw new DbClientException("Failed to prepare statement with named parameters: " + stmtName, e);
        }
    }

    private PreparedStatement prepareIndexedStatement(String stmtName, String stmt, List<Object> parameters) {
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = prepareStatement(stmtName, stmt);
            int i = 1; // JDBC set position parameter starts from 1.
            for (Object value : parameters) {
                LOGGER.log(Level.TRACE, String.format("Indexed parameter %d: %s", i, value));
                preparedStatement.setObject(i, value);
                // increase value for next iteration
                i++;
            }
            return preparedStatement;
        } catch (SQLException e) {
            closePreparedStatement(preparedStatement);
            throw new DbClientException(String.format("Failed to prepare statement with indexed params: %s", stmtName), e);
        }
    }

    private void closePreparedStatement(PreparedStatement preparedStatement) {
        if (preparedStatement != null) {
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, String.format("Could not close PreparedStatement: %s", e.getMessage()), e);
            }
        }
    }

    private static String namedStatementErrorMessage(List<String> names, Map<String, Object> parameters) {
        // Parameters in query missing in parameters Map
        List<String> notInParams = new ArrayList<>(names.size());
        for (String name : names) {
            if (!parameters.containsKey(name)) {
                notInParams.add(name);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Query parameters missing in Map: ");
        boolean first = true;
        for (String name : notInParams) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(name);
        }
        return sb.toString();
    }
}
