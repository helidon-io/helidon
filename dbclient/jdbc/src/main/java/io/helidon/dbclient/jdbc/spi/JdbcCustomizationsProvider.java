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

package io.helidon.dbclient.jdbc.spi;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

/**
 * JDBC DbClient customization hooks.
 */
public interface JdbcCustomizationsProvider {

    /**
     * Custom code to build prepared statement using named parameters Map.
     *
     * @return customized prepared statement builder or {@code null} when
     *         no customized method is provided
     */
    default PrepareNamedStatement prepareNamedStatement() {
        return null;
    }

    /**
     * Custom code to build prepared statement using indexed parameters List.
     *
     * @return customized prepared statement builder or {@code null} when
     *         no customized method is provided
     */
    default PrepareIndexedStatement prepareIndexedStatement() {
        return null;
    }

    /**
     * Functional interface for custom prepared statement using named parameters Map builder.
     */
    @FunctionalInterface
    public interface PrepareNamedStatement {

        /**
         * Apply custom prepared statement named parameters Map builder.
         * @param connection database connection
         * @param statementName name of the statement
         * @param statement the statement {@code String}
         * @param parameters named parameters {@code Map}
         * @return built {@code PreparedStatement}
         */
        PreparedStatement apply(
                Connection connection,
                String statementName,
                String statement,
                Map<String, Object> parameters);

    }

    /**
     * Functional interface for custom prepared statement using indexed parameters List builder.
     */
    @FunctionalInterface
    public interface PrepareIndexedStatement {

        /**
         * Apply custom prepared statement indexed parameters List builder.
         *
         * @param connection database connection
         * @param statementName name of the statement
         * @param statement the statement {@code String}
         * @param parameters indexed parameters {@code List}
         * @return built {@code PreparedStatement}
         */
        PreparedStatement apply(
                Connection connection,
                String statementName,
                String statement,
                List<Object> parameters);

    }

}
