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
import java.util.Optional;

/**
 * In-memory description of one JDBC statement assembled by an application or
 * generated repository.
 * <p>
 * The stage does not borrow a connection. Its first terminal operation freezes
 * the bindings into an immutable operation and hands ownership to the runner.
 */
final class JdbcStatement implements JdbcClient.Statement {

    private final JdbcRunner runner;
    private final String sql;
    private final JdbcOperation.Bind[] binds;

    // A terminal freezes the statement, so later mutation or execution must fail.
    private boolean terminalStarted;

    /**
     * Creates an empty statement description.
     *
     * @param runner shared execution engine
     * @param sql positional SQL
     * @param parameterCount number of JDBC markers
     */
    JdbcStatement(JdbcRunner runner, String sql, int parameterCount) {
        this.runner = runner;
        this.sql = sql;
        this.binds = new JdbcOperation.Bind[parameterCount];
    }

    /**
     * Stores a non-null scalar binding.
     *
     * @param index one-based JDBC position
     * @param value scalar value
     * @return this statement
     */
    @Override
    public JdbcClient.Statement bind(int index, Object value) {
        Objects.requireNonNull(value, "Bind value must not be null; use bindNull for SQL NULL");
        if (!JdbcRow.supportedScalar(value.getClass())) {
            throw new IllegalArgumentException("Unsupported JDBC bind value type: " + value.getClass().getTypeName());
        }
        return bind(index, new JdbcOperation.Bind(value, null));
    }

    /**
     * Stores an explicitly typed SQL null.
     *
     * @param index one-based JDBC position
     * @param type standard JDBC type
     * @return this statement
     */
    @Override
    public JdbcClient.Statement bindNull(int index, JDBCType type) {
        Objects.requireNonNull(type, "JDBC null type must not be null");
        if (type == JDBCType.NULL || type == JDBCType.REF_CURSOR) {
            throw new IllegalArgumentException("Unsupported JDBC null type: " + type);
        }
        return bind(index, new JdbcOperation.Bind(null, type));
    }

    /**
     * Executes the statement as an update.
     *
     * @return large update count
     */
    @Override
    public long execute() {
        return runner.execute(operation(JdbcPreparationPlan.update()));
    }

    /**
     * Selects an explicit query mapper.
     *
     * @param mapper row mapper
     * @param <T> mapped type
     * @return mapped rows stage
     */
    @Override
    public <T> JdbcClient.Rows<T> map(JdbcClient.RowMapper<T> mapper) {
        ensureMutable();
        return new JdbcRows<>(this,
                              Objects.requireNonNull(mapper, "Row mapper must not be null"),
                              JdbcPreparationPlan.query());
    }

    /**
     * Selects the built-in column-one scalar mapper.
     *
     * @param scalarType supported scalar type
     * @param <T> mapped type
     * @return mapped rows stage
     */
    @Override
    public <T> JdbcClient.Rows<T> map(Class<T> scalarType) {
        ensureMutable();
        Objects.requireNonNull(scalarType, "Scalar type must not be null");
        if (!JdbcRow.supportedScalar(scalarType)) {
            throw new IllegalArgumentException("Unsupported JDBC scalar type: " + scalarType.getTypeName());
        }
        return new JdbcRows<>(this,
                              row -> row.required(1, scalarType),
                              JdbcPreparationPlan.query(),
                              scalarType);
    }

    /**
     * Selects generated-key execution.
     *
     * @return generated-key configuration stage
     */
    @Override
    public JdbcClient.GeneratedKeys generatedKeys() {
        ensureMutable();
        return new JdbcGeneratedKeys(this);
    }

    /**
     * Executes an exactly-one mapped terminal.
     *
     * @param mapper row mapper
     * @param plan preparation plan
     * @param <T> mapped type
     * @return exactly one mapped value
     */
    <T> T one(JdbcClient.RowMapper<T> mapper, JdbcPreparationPlan plan) {
        return runner.one(operation(plan), mapper);
    }

    /**
     * Executes a zero-or-one mapped terminal.
     *
     * @param mapper row mapper
     * @param plan preparation plan
     * @param <T> mapped type
     * @return optional mapped value
     */
    <T> Optional<T> optional(JdbcClient.RowMapper<T> mapper, JdbcPreparationPlan plan) {
        return runner.optional(operation(plan), mapper);
    }

    /**
     * Executes a scalar result with nullable zero-or-one cardinality.
     *
     * @param scalarType mapped scalar type
     * @param plan query or generated-key preparation
     * @param <T> scalar type
     * @return empty for no row or one SQL {@code NULL}, otherwise the value
     */
    <T> Optional<T> optionalScalar(Class<T> scalarType, JdbcPreparationPlan plan) {
        return runner.optionalScalar(operation(plan), scalarType);
    }

    /**
     * Executes an all-rows mapped terminal.
     *
     * @param mapper row mapper
     * @param plan preparation plan
     * @param <T> mapped type
     * @return materialized values
     */
    <T> List<T> list(JdbcClient.RowMapper<T> mapper, JdbcPreparationPlan plan) {
        return runner.list(operation(plan), mapper);
    }

    /**
     * Assigns one bind slot after validating stage state and position.
     *
     * @param index one-based JDBC position
     * @param value immutable binding
     * @return this statement
     */
    private JdbcClient.Statement bind(int index, JdbcOperation.Bind value) {
        ensureMutable();
        if (index < 1 || index > binds.length) {
            throw new IllegalArgumentException("Bind index must be between 1 and " + binds.length + ": " + index);
        }
        if (binds[index - 1] != null) {
            throw new IllegalArgumentException("Bind position " + index + " was already assigned");
        }
        binds[index - 1] = value;
        return this;
    }

    /**
     * Validates all parameters and creates the runner-owned snapshot.
     *
     * @param plan preparation and result contract
     * @return immutable operation
     */
    private JdbcOperation operation(JdbcPreparationPlan plan) {
        ensureMutable();
        for (int index = 0; index < binds.length; index++) {
            if (binds[index] == null) {
                throw new IllegalStateException("Missing bind value at JDBC position " + (index + 1));
            }
        }
        terminalStarted = true;
        return new JdbcOperation(sql, binds.clone(), plan);
    }

    /**
     * Rejects changes after a terminal operation claims this stage.
     */
    void ensureMutable() {
        if (terminalStarted) {
            throw new IllegalStateException("A JDBC statement stage permits exactly one terminal operation");
        }
    }
}
