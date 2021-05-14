/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.dbclient.jdbc.oradb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.jdbc.JdbcStatement;
import io.helidon.dbclient.jdbc.spi.JdbcCustomizationsProvider;

import oracle.jdbc.OracleType;
import oracle.sql.json.OracleJsonValue;

/**
 *
 */
public class OraDbCustomizationsProvider implements JdbcCustomizationsProvider {

    private static final Logger LOGGER = Logger.getLogger(OraDbCustomizationsProvider.class.getName());

    @Override
    public JdbcCustomizationsProvider.PrepareNamedStatement prepareNamedStatement() {
        return new PrepareNamedStatement();
    }

    /**
     * Custom code to build prepared statement using indexed parameters List.
     *
     * @return customized prepared statement builder or {@code null} when
     *         no customized method is provided
     */
    @Override
    public JdbcCustomizationsProvider.PrepareIndexedStatement prepareIndexedStatement() {
        return new PrepareIndexedStatement();
    }

    // Oracle DB handler for named statement parameters, used by JdbcCustomizationsManager when not overriden
    private static class PrepareNamedStatement implements JdbcCustomizationsProvider.PrepareNamedStatement {

        @Override
        public PreparedStatement apply(
                Connection connection,
                String statementName,
                String statement,
                Map<String, Object> parameters) {

            PreparedStatement preparedStatement = null;
            try {
                // Parameters names must be replaced with ? and names occurence order must be stored.
                JdbcStatement.Parser parser = new JdbcStatement.Parser(statement);
                String jdbcStatement = parser.convert();
                LOGGER.finer(() -> String.format("Converted statement: %s", jdbcStatement));
                preparedStatement = connection.prepareStatement(jdbcStatement);
                List<String> namesOrder = parser.namesOrder();
                // Set parameters into prepared statement
                int i = 1;
                for (String name : namesOrder) {
                    if (parameters.containsKey(name)) {
                        Object value = parameters.get(name);
                        LOGGER.finest(String.format("Mapped parameter %d: %s -> %s", i, name, value));
                        if (OracleJsonValue.class.isAssignableFrom(value.getClass())) {
                            preparedStatement.setObject(i, value, OracleType.JSON);
                        } else {
                            preparedStatement.setObject(i, value);
                        }
                        i++;
                    } else {
                        throw new DbClientException(JdbcStatement.namedStatementErrorMessage(namesOrder, parameters));
                    }
                }
                return preparedStatement;
            } catch (SQLException e) {
                JdbcStatement.closePreparedStatement(preparedStatement);
                throw new DbClientException("Failed to prepare statement with named parameters: " + statementName, e);
            }
        }
    }

    // Oracle DB handler for indexed statement parameters, used by JdbcCustomizationsManager when not overriden
    private static class PrepareIndexedStatement implements JdbcCustomizationsProvider.PrepareIndexedStatement {

        @Override
        public PreparedStatement apply(
                Connection connection,
                String statementName,
                String statement,
                List<Object> parameters) {

            PreparedStatement preparedStatement = null;
            try {
                preparedStatement = connection.prepareStatement(statement);
                int i = 1; // JDBC set position parameter starts from 1.
                for (Object value : parameters) {
                    LOGGER.finest(String.format("Indexed parameter %d: %s", i, value));
                    if (OracleJsonValue.class.isAssignableFrom(value.getClass())) {
                        preparedStatement.setObject(i, value, OracleType.JSON);
                    } else {
                        preparedStatement.setObject(i, value);
                    }
                    i++;
                }
                return preparedStatement;
            } catch (SQLException e) {
                JdbcStatement.closePreparedStatement(preparedStatement);
                throw new DbClientException(
                        String.format("Failed to prepare statement with indexed params: %s", statementName), e);
            }
        }

    }

}
