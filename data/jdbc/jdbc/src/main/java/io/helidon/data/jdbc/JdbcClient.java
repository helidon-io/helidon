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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Api;
import io.helidon.service.registry.Service;

/**
 * Executes JDBC statements for applications and generated repositories.
 * <p>
 * Creating and binding a statement performs no database work. A terminal
 * operation acquires the JDBC resources, fully materializes any result, and
 * releases every resource before returning. The client is safe to share.
 * Statement and result stages are single use and are not safe for concurrent
 * use.
 * <p>
 * A client built through {@link #builder()} or {@link #create(JdbcClientConfig)}
 * owns a connection for each terminal operation. It does not participate in
 * a transaction established by a transaction annotation on its caller.
 * <p>
 * Annotation based applications inject a registry managed client with the
 * {@code jdbc} {@link io.helidon.data.Data.ProviderType} and a {@link Service.Named}
 * qualifier. A registry managed client uses Helidon's local transaction
 * support and participates in the transaction surrounding the intercepted
 * service invocation.
 */
@Api.Preview
@Service.Contract
public interface JdbcClient extends RuntimeType.Api<JdbcClientConfig> {

    /**
     * Creates a builder for a JDBC client.
     *
     * @return new JDBC client builder
     */
    static JdbcClientConfig.Builder builder() {
        return JdbcClientConfig.builder();
    }

    /**
     * Creates a JDBC client from an immutable configuration.
     *
     * @param config JDBC client configuration
     * @return configured JDBC client
     * @throws NullPointerException if the configuration is {@code null}
     * @throws io.helidon.data.DataException if the configuration cannot create a direct client
     */
    static JdbcClient create(JdbcClientConfig config) {
        Objects.requireNonNull(config, "The JDBC client configuration must not be null.");
        return JdbcClientImpl.create(config);
    }

    /**
     * Creates a JDBC client after updating a new builder.
     *
     * @param consumer builder updates
     * @return configured JDBC client
     * @throws NullPointerException if the consumer is {@code null}
     * @throws io.helidon.data.DataException if the configuration cannot create a direct client
     */
    static JdbcClient create(Consumer<JdbcClientConfig.Builder> consumer) {
        Objects.requireNonNull(consumer, "The JDBC client builder consumer must not be null.");
        return builder().update(consumer).build();
    }

    /**
     * Creates a statement description from positional JDBC SQL.
     * <p>
     * The SQL is trusted executable application input. This method does not
     * sanitize data concatenated into the SQL text. Represent untrusted values
     * with {@code ?} markers and supply them through
     * {@link Statement#bind(int, Object)}. Bind markers represent values only.
     * Select identifiers, operators, sort directions, and other SQL structure
     * from an explicit application allowlist.
     * <p>
     * Marker recognition uses a portable lexical policy. Question marks
     * inside ordinary single-quoted strings, PostgreSQL escape and dollar
     * quoted strings, double-quoted or backtick identifiers, Oracle
     * alternative strings, and conventional comments are ignored. Square
     * brackets are ordinary punctuation. A doubled question mark is preserved
     * as driver escape syntax rather than counted as bind markers. A
     * {@code --} comment requires following whitespace, a control character,
     * or end-of-input; other double-dash sequences are rejected as
     * dialect-ambiguous. Nested block comments are rejected.
     *
     * @param sql SQL containing zero or more {@code ?} markers
     * @return statement description
     * @throws NullPointerException if the SQL is {@code null}
     * @throws IllegalArgumentException if the SQL is blank, malformed, or contains named markers
     */
    Statement create(String sql);

    /**
     * Describes one prepared JDBC operation.
     * <p>
     * Bind positions are one-based. The stage accepts exactly one terminal
     * operation and is not safe for concurrent use.
     */
    @Api.Preview
    interface Statement {

        /**
         * Binds a non-null supported scalar value.
         *
         * @param index one-based JDBC position
         * @param value supported scalar value
         * @return this statement
         * @throws NullPointerException if the value is {@code null}
         * @throws IllegalArgumentException if the position or value is invalid
         * @throws IllegalStateException if a terminal operation has started
         */
        Statement bind(int index, Object value);

        /**
         * Executes an update and returns its large update count.
         *
         * @return update count
         * @throws io.helidon.data.DataException if JDBC execution fails
         * @throws IllegalStateException if a bind is missing or a terminal operation has started
         */
        long execute();

        /**
         * Selects an explicit mapper for query rows.
         *
         * @param mapper row mapper
         * @param <T> mapped type
         * @return materialized-row terminal stage
         * @throws NullPointerException if the mapper is {@code null}
         * @throws IllegalStateException if a terminal operation has started
         */
        <T> Rows<T> map(RowMapper<T> mapper);

        /**
         * Selects column-one mapping for a supported scalar type.
         * <p>
         * {@link Rows#one()} and {@link Rows#list()} require a non-null column
         * value. {@link Rows#optional()} returns {@link Optional#empty()} both
         * when no row is returned and when exactly one row contains SQL
         * {@code NULL} in column one. Applications that need to distinguish
         * those states can use {@link #map(RowMapper)} with a mapper that
         * returns an {@link Optional}; the outer optional returned by
         * {@link Rows#optional()} then represents row presence.
         *
         * @param scalarType scalar type
         * @param <T> mapped scalar type
         * @return materialized-row terminal stage
         * @throws NullPointerException if the type is {@code null}
         * @throws IllegalArgumentException if the scalar type is not supported
         * @throws IllegalStateException if a terminal operation has started
         */
        <T> Rows<T> map(Class<T> scalarType);

        /**
         * Selects generated-key execution.
         * <p>
         * Adding no columns requests the driver's default generated keys.
         * Configuration does not acquire JDBC resources.
         *
         * @return generated-key configuration stage
         * @throws IllegalStateException if a terminal operation has started
         */
        GeneratedKeys generatedKeys();
    }

    /**
     * Configures generated key columns and mapping before execution.
     * <p>
     * The stage preserves column order. It is single use, is not safe for
     * concurrent use, and performs no JDBC work.
     */
    @Api.Preview
    interface GeneratedKeys {

        /**
         * Adds one generated column requested from JDBC.
         *
         * @param columnName non-blank generated column name
         * @return this generated key stage
         * @throws NullPointerException if the column name is {@code null}
         * @throws IllegalArgumentException if the column name is blank or exactly duplicates an existing name
         * @throws IllegalStateException if mapping has already been selected
         */
        GeneratedKeys addColumn(String columnName);

        /**
         * Selects the mapper for generated-key rows and finalizes this stage.
         *
         * @param mapper generated key row mapper
         * @param <T> mapped key type
         * @return materialized-key terminal stage
         * @throws NullPointerException if the mapper is {@code null}
         * @throws IllegalStateException if mapping has already been selected
         */
        <T> Rows<T> map(RowMapper<T> mapper);
    }

    /**
     * Materialized result terminals for a mapped query or generated-key result.
     * <p>
     * The stage accepts exactly one terminal invocation and is not safe for
     * concurrent use. An exception from an application mapper is propagated
     * unchanged.
     *
     * @param <T> mapped type
     */
    @Api.Preview
    interface Rows<T> {

        /**
         * Returns exactly one row.
         *
         * @return mapped row
         * @throws io.helidon.data.NoResultException if no row is returned
         * @throws io.helidon.data.NonUniqueResultException if more than one row is returned
         * @throws io.helidon.data.DataException if JDBC execution or provider mapping fails
         * @throws IllegalStateException if a terminal operation has started
         */
        T one();

        /**
         * Returns zero or one row.
         * <p>
         * For a scalar stage created by {@link Statement#map(Class)}, an empty
         * result represents either no row or exactly one row whose first
         * column is SQL {@code NULL}. For a stage created by
         * {@link Statement#map(RowMapper)}, an empty result represents no row;
         * the mapper may return an {@link Optional} value to represent column
         * nullability separately.
         *
         * @return empty if no row is returned or, for scalar mapping selected
         *         by {@link Statement#map(Class)}, if one row contains SQL
         *         {@code NULL} in column one; otherwise the mapped row
         * @throws io.helidon.data.NonUniqueResultException if more than one row is returned
         * @throws io.helidon.data.DataException if JDBC execution or provider mapping fails
         * @throws IllegalStateException if a terminal operation has started
         */
        Optional<T> optional();

        /**
         * Returns all rows in JDBC encounter order.
         *
         * @return materialized rows
         * @throws io.helidon.data.DataException if JDBC execution or provider mapping fails
         * @throws IllegalStateException if a terminal operation has started
         */
        List<T> list();
    }

    /**
     * Maps one JDBC row to an application value during a callback.
     * <p>
     * Imperative code passes a mapper to {@link Statement#map(RowMapper)} or
     * {@link GeneratedKeys#map(RowMapper)}. Applications may also register
     * implementations as services. A generated repository resolves a mapper selected by
     * {@code @Jdbc.RowMapper(SomeMapper.class)} by its concrete service type.
     * The marker form {@code @Jdbc.RowMapper} resolves this contract with the
     * exact {@code T} type.
     * <p>
     * A mapper held by a singleton repository may be invoked concurrently. It
     * must be stateless or safe for concurrent use. Each invocation receives
     * its own row. The row may be read only by the thread executing
     * {@link RowMapper#map(Row)}. The provider retains ownership of the JDBC
     * resources and exposes only the scoped row view.
     *
     * @param <T> mapped type
     */
    @Api.Preview
    @Service.Contract
    @FunctionalInterface
    interface RowMapper<T> {

        /**
         * Maps the current row synchronously. Row reads call the JDBC driver
         * and may block until the driver returns or reports a failure. The row
         * may be read only by the current thread and is no longer valid after
         * this method returns.
         *
         * @param row current row
         * @return mapped value, never {@code null}
         */
        T map(Row row);
    }

    /**
     * Restricted view of the current result row.
     * <p>
     * The row may be read only by the thread executing the current
     * {@link RowMapper#map(Row)} callback. It must not be passed to another
     * thread and is no longer valid after the callback returns.
     */
    @Api.Preview
    interface Row {

        /**
         * Reads a nullable value by one-based column index.
         *
         * @param index one-based column index
         * @param type requested scalar type
         * @param <T> scalar type
         * @return optional value
         * @throws io.helidon.data.DataException if the column cannot be read
         * @throws IllegalArgumentException if the index or type is invalid
         * @throws IllegalStateException if the row is no longer active or the
         *                                  caller is not the callback thread
         */
        <T> Optional<T> optional(int index, Class<T> type);

        /**
         * Reads a nullable value by column label.
         *
         * @param label column label
         * @param type requested scalar type
         * @param <T> scalar type
         * @return optional value
         * @throws io.helidon.data.DataException if the label is absent or ambiguous, or the column cannot be read
         * @throws NullPointerException if the label or type is {@code null}
         * @throws IllegalArgumentException if the label or type is invalid
         * @throws IllegalStateException if the row is no longer active or the
         *                                  caller is not the callback thread
         */
        <T> Optional<T> optional(String label, Class<T> type);

        /**
         * Reads a non-null value by the column index, starting at one.
         *
         * @param index one-based column index
         * @param type requested scalar type
         * @param <T> scalar type
         * @return non-null value
         * @throws io.helidon.data.DataException if the column contains SQL {@code NULL} or cannot be read
         * @throws IllegalArgumentException if the index or type is invalid
         * @throws IllegalStateException if the row is no longer active or the
         *                                  caller is not the callback thread
         */
        <T> T get(int index, Class<T> type);

        /**
         * Reads a non-null value by column label.
         *
         * @param label column label
         * @param type requested scalar type
         * @param <T> scalar type
         * @return non-null value
         * @throws io.helidon.data.DataException if the column contains SQL {@code NULL} or cannot be read
         * @throws NullPointerException if the label or type is {@code null}
         * @throws IllegalArgumentException if the label or type is invalid
         * @throws IllegalStateException if the row is no longer active or the
         *                                  caller is not the callback thread
         */
        <T> T get(String label, Class<T> type);
    }
}
