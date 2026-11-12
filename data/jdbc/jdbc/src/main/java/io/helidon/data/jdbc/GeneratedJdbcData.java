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
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.common.Api;
import io.helidon.data.jdbc.JdbcClient.Statement;

/**
 * Methods used by generated Helidon Data JDBC repositories.
 * <p>
 * These methods form the compatibility boundary between JDBC repository code
 * generation and the JDBC runtime. Application code uses {@link JdbcClient}
 * directly.
 */
@Api.Incubating
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
                    "The JDBC parameter count must be between zero and the SQL statement length, inclusive. "
                            + "The requested count was " + parameterCount + ".");
        }
        if (client instanceof JdbcClientImpl clientImpl) {
            return clientImpl.createGenerated(sql, parameterCount);
        }
        return client.create(sql);
    }

    /**
     * Prepares named SQL for a generated repository with input aliases or ordinary callable outputs.
     * The native provider validates the complete input contract and scans SQL once
     * without acquiring JDBC resources. The returned supplier creates fresh stages
     * without rescanning SQL or capturing a transaction context.
     * The SQL must contain a named marker and use exactly the unique input names
     * supplied in the list. For CALL, the list includes outputs and a leading return
     * named return. Only input markers may repeat. The list is defensively copied.
     * The supplier is safe to share. Individual stages are not safe for concurrent mutation.
     *
     * @param client The native Helidon JDBC client owns execution.
     * @param sql The SQL contains named markers for every logical input.
     * @param inputNames The effective names identify all logical inputs and callable outputs.
     * @return The supplier retains an immutable definition and creates fresh stages.
     * @throws NullPointerException The client, SQL, list, or a list entry is null.
     * @throws IllegalArgumentException The SQL or input contract is invalid, or SQL does not use named markers.
     * @throws UnsupportedOperationException The client is not the native Helidon JDBC client.
     */
    @Api.Since("28.0.0")
    public static Supplier<Statement> prepareNamed(JdbcClient client, String sql, List<String> inputNames) {
        Objects.requireNonNull(client, "The JDBC client must not be null.");
        Objects.requireNonNull(sql, "The SQL statement must not be null.");
        Objects.requireNonNull(inputNames, "The input names must not be null.");
        inputNames.forEach(name -> Objects.requireNonNull(name, "The input name must not be null."));
        if (client instanceof JdbcClientImpl clientImpl) {
            return clientImpl.prepareNamed(sql, inputNames);
        }
        throw new UnsupportedOperationException(
                "Named preparation for generated repositories requires the native Helidon JDBC client.");
    }

    /**
     * Prepares callable SQL against the requirements of a generated repository method.
     * The leading return must be present exactly when leadingReturn is true.
     * A true namedOnly flag requires named argument markers. Logical names include
     * every input and output once, with return first when a leading return is required.
     * The names are defensively copied. Validation performs no JDBC work.
     * <p>
     * The native provider scans once and retains an immutable definition. Each supplier
     * invocation creates an independent stage without rescanning SQL or retaining a
     * transaction context. The supplier is safe to share, but its stages are not safe
     * for concurrent mutation. Alternate clients receive general preparation after
     * validation when namedOnly is false. Named-only preparation requires the native client.
     *
     * @param client The client owns execution of the prepared call.
     * @param sql The SQL defines one callable statement.
     * @param logicalNames The names identify parameters in the compiled binding order.
     * @param namedOnly The flag requires named argument markers when true.
     * @param leadingReturn The flag requires a leading return when true and excludes it when false.
     * @return The supplier creates fresh statement stages.
     * @throws NullPointerException The client, SQL, list, or a list entry is null.
     * @throws IllegalArgumentException The SQL or logical parameters do not satisfy the compiled contract.
     * @throws UnsupportedOperationException The client does not support the required preparation.
     */
    @Api.Since("28.0.0")
    public static Supplier<Statement> prepareCall(JdbcClient client,
                                                  String sql,
                                                  List<String> logicalNames,
                                                  boolean namedOnly,
                                                  boolean leadingReturn) {
        Objects.requireNonNull(client, "The JDBC client must not be null.");
        Objects.requireNonNull(sql, "The SQL statement must not be null.");
        Objects.requireNonNull(logicalNames, "The logical names must not be null.");
        logicalNames.forEach(name -> Objects.requireNonNull(name, "The logical name must not be null."));
        List<String> names = List.copyOf(logicalNames);
        if (client instanceof JdbcClientImpl clientImpl) {
            return clientImpl.prepareCall(sql, names, namedOnly, leadingReturn);
        }
        if (namedOnly) {
            throw new UnsupportedOperationException(
                    "Named preparation for generated repositories requires the native Helidon JDBC client.");
        }
        JdbcConfiguredStatement.createCall(sql, names, false, leadingReturn);
        return client.prepare(sql, names);
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
    /**
     * Binds a typed SQL {@code NULL} to every JDBC position associated with a logical input.
     * Every destination is validated before any binding changes, and binding performs no JDBC work.
     *
     * @param statement The statement created by the Helidon JDBC provider.
     * @param name The name identifies the logical input without a colon prefix.
     * @param type The SQL type supplied by generated code.
     * @return The same statement.
     * @throws NullPointerException The statement, name, or type is null.
     * @throws IllegalArgumentException The name or type is unsupported, or a slot is assigned.
     * @throws IllegalStateException A terminal operation has started, or the statement has no reusable definition.
     * @throws UnsupportedOperationException The statement belongs to another provider.
     */
    public static Statement bindNull(Statement statement, String name, JDBCType type) {
        Objects.requireNonNull(statement, "The JDBC statement must not be null.");
        Objects.requireNonNull(name, "The bind name must not be null.");
        Objects.requireNonNull(type, "The JDBC null type must not be null.");
        if (statement instanceof JdbcStatement jdbcStatement) {
            return jdbcStatement.bindNull(name, type);
        }
        throw new UnsupportedOperationException(
                "Typed SQL null binding requires a statement created by the Helidon JDBC provider.");
    }

}
