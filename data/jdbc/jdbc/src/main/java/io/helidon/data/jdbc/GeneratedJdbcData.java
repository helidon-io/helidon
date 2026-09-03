/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc;

import java.sql.JDBCType;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.data.jdbc.JdbcClient.Statement;

/**
 * Methods used by generated Helidon Data JDBC repositories.
 * <p>
 * These methods form the compatibility boundary between JDBC repository code
 * generation and the JDBC runtime. Application code uses {@link JdbcClient}
 * directly.
 */
@Api.Preview
@Api.Since("27.0.0")
public final class GeneratedJdbcData {

    private GeneratedJdbcData() {
    }

    /**
     * Creates a statement description for generated repository code that has
     * already validated the positional JDBC SQL.
     * <p>
     * The physical marker count is computed by the annotation processor. A
     * statement from Helidon's JDBC provider avoids scanning static SQL and
     * accessing the runtime marker count cache. An alternate provider uses its
     * supported {@link JdbcClient#create(String)} operation.
     *
     * @param client JDBC client
     * @param sql validated SQL containing positional JDBC markers
     * @param parameterCount exact number of physical JDBC markers
     * @return statement description
     * @throws NullPointerException if the client or SQL is {@code null}
     * @throws IllegalArgumentException if the parameter count is negative or
     *                                  greater than the SQL text length
     */
    public static Statement createGenerated(JdbcClient client, String sql, int parameterCount) {
        Objects.requireNonNull(client, "The JDBC client must not be null.");
        Objects.requireNonNull(sql, "The SQL statement must not be null.");
        // A physical marker occupies at least one character. This constant time
        // check prevents an invalid generated caller from requesting an
        // unrelated or unbounded bind array without scanning the SQL again.
        if (parameterCount < 0 || parameterCount > sql.length()) {
            throw new IllegalArgumentException(
                    "The JDBC parameter count must be between zero and the SQL statement length.");
        }
        if (client instanceof JdbcClientImpl clientImpl) {
            return clientImpl.createGenerated(sql, parameterCount);
        }
        return client.create(sql);
    }

    /**
     * Binds SQL {@code NULL} for a generated declarative repository.
     * <p>
     * This method accepts only a statement created by Helidon's JDBC provider.
     * Types that require a database type name are not supported.
     *
     * @param statement statement created by Helidon's JDBC provider
     * @param index JDBC position starting at one
     * @param type SQL type of the null value
     * @return the statement
     * @throws NullPointerException if the statement or type is {@code null}
     * @throws IllegalArgumentException if the position or type is invalid or
     *                                  the type requires a database type name
     * @throws IllegalStateException if a terminal operation has started
     * @throws UnsupportedOperationException if the statement was not created
     *                                       by Helidon's JDBC provider
     */
    public static Statement bindNull(Statement statement, int index, JDBCType type) {
        Objects.requireNonNull(statement, "The JDBC statement must not be null.");
        Objects.requireNonNull(type, "The JDBC null type must not be null.");
        if (statement instanceof JdbcStatement jdbcStatement) {
            return jdbcStatement.bindNull(index, type);
        }
        throw new UnsupportedOperationException(
                "Typed SQL null binding requires a statement created by Helidon's JDBC provider.");
    }
}
