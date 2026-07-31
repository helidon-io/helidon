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
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.data.Data;
import io.helidon.data.DataException;
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
 * A configured persistence unit publishes a client through the Service
 * Registry. Applications select it with the {@code jdbc}
 * {@link Data.ProviderType} and a {@link Service.Named} qualifier using the
 * persistence unit name.
 */
@Api.Preview
@Service.Contract
public interface JdbcClient {

    /**
     * Creates a single-use statement description from positional JDBC SQL.
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
         * Binds SQL {@code NULL} with an explicit standard JDBC type.
         *
         * @param index one-based JDBC position
         * @param type SQL type of the null value
         * @return this statement
         * @throws NullPointerException if the type is {@code null}
         * @throws IllegalArgumentException if the position or type is invalid
         * @throws IllegalStateException if a terminal operation has started
         */
        Statement bindNull(int index, JDBCType type);

        /**
         * Executes an update and returns its large update count.
         *
         * @return update count
         * @throws DataException if JDBC execution fails
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
         * @throws IllegalArgumentException if the column name is blank or duplicates an existing name
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
         * @throws DataException if JDBC execution or provider mapping fails
         * @throws IllegalStateException if a terminal operation has started
         */
        T one();

        /**
         * Returns zero or one row.
         *
         * @return optional mapped row
         * @throws io.helidon.data.NonUniqueResultException if more than one row is returned
         * @throws DataException if JDBC execution or provider mapping fails
         * @throws IllegalStateException if a terminal operation has started
         */
        Optional<T> optional();

        /**
         * Returns all rows in JDBC encounter order.
         *
         * @return materialized rows
         * @throws DataException if JDBC execution or provider mapping fails
         * @throws IllegalStateException if a terminal operation has started
         */
        List<T> list();
    }

    /**
     * Maps one callback-scoped JDBC row to an application value.
     * <p>
     * Imperative code passes a mapper to {@link Statement#map(RowMapper)} or
     * {@link GeneratedKeys#map(RowMapper)}. Applications may also register
     * implementations as services. A generated repository resolves a mapper selected by
     * {@code @Jdbc.RowMapper(SomeMapper.class)} by its concrete service type.
     * The marker form {@code @Jdbc.RowMapper} resolves this contract with the
     * exact {@code T} type.
     * <p>
     * A mapper held by a singleton repository may be invoked concurrently. It
     * must be stateless or safe for concurrent use. The provider retains
     * ownership of the JDBC resources and exposes only the scoped row view.
     *
     * @param <T> mapped type
     */
    @Api.Preview
    @Service.Contract
    @FunctionalInterface
    interface RowMapper<T> {

        /**
         * Maps the current row. The row is no longer valid after this method
         * returns.
         *
         * @param row current row
         * @return mapped value, never {@code null}
         */
        T map(Row row);
    }

    /**
     * Restricted view of the current result row.
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
         * @throws DataException if the column cannot be read
         * @throws IllegalArgumentException if the index or type is invalid
         * @throws IllegalStateException if the row is no longer active
         */
        <T> Optional<T> optional(int index, Class<T> type);

        /**
         * Reads a nullable value by column label.
         *
         * @param label column label
         * @param type requested scalar type
         * @param <T> scalar type
         * @return optional value
         * @throws DataException if the label is absent or ambiguous, or the column cannot be read
         * @throws NullPointerException if the label or type is {@code null}
         * @throws IllegalArgumentException if the label or type is invalid
         * @throws IllegalStateException if the row is no longer active
         */
        <T> Optional<T> optional(String label, Class<T> type);

        /**
         * Reads a required value by one-based column index.
         *
         * @param index one-based column index
         * @param type requested scalar type
         * @param <T> scalar type
         * @return non-null value
         * @throws DataException if the column contains SQL {@code NULL} or cannot be read
         * @throws IllegalArgumentException if the index or type is invalid
         * @throws IllegalStateException if the row is no longer active
         */
        <T> T required(int index, Class<T> type);

        /**
         * Reads a required value by column label.
         *
         * @param label column label
         * @param type requested scalar type
         * @param <T> scalar type
         * @return non-null value
         * @throws DataException if the column contains SQL {@code NULL} or cannot be read
         * @throws NullPointerException if the label or type is {@code null}
         * @throws IllegalArgumentException if the label or type is invalid
         * @throws IllegalStateException if the row is no longer active
         */
        <T> T required(String label, Class<T> type);
    }
}
