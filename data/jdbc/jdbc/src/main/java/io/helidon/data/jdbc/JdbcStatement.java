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

    // A terminal operation freezes the statement and places it in its terminal state.
    private boolean terminalState;

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
        Objects.requireNonNull(value, "The bind value must not be null.");
        if (!JdbcScalarAccess.supported(value.getClass())) {
            throw new IllegalArgumentException(
                    "Helidon Data JDBC provider does not support type '"
                            + value.getClass().getTypeName() + "' as a portable scalar.");
        }
        return bind(index, new JdbcOperation.Bind(value, null));
    }

    /**
     * Executes the statement as an update.
     *
     * @return update count as a {@code long} value
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
        Objects.requireNonNull(mapper, "The row mapper must not be null.");
        ensureMutable();
        return new JdbcRows<>(this,
                              mapper,
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
        Objects.requireNonNull(scalarType, "The scalar type must not be null.");
        ensureMutable();
        if (!JdbcScalarAccess.supported(scalarType)) {
            throw new IllegalArgumentException(
                    "Helidon Data JDBC provider does not support type '"
                            + scalarType.getTypeName() + "' as a portable scalar.");
        }
        return new JdbcRows<>(this,
                              row -> row.get(1, scalarType),
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
     * Stores an explicitly typed SQL null for generated repository code.
     *
     * @param index one-based JDBC position
     * @param type standard JDBC type
     * @return this statement
     */
    JdbcClient.Statement bindNull(int index, JDBCType type) {
        switch (type) {
        case NULL, REF_CURSOR -> throw new IllegalArgumentException(
                "The JDBC client does not support null values of type '" + type + "'.");
        case ARRAY, DISTINCT, JAVA_OBJECT, REF, STRUCT -> throw new IllegalArgumentException(
                "The JDBC client cannot bind a null value of type '" + type
                        + "' without a database type name.");
        default -> {
        }
        }
        return bind(index, new JdbcOperation.Bind(null, type));
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
     * Rejects changes after a terminal operation claims this stage.
     */
    void ensureMutable() {
        if (terminalState) {
            throw new IllegalStateException("A JDBC statement can perform only one terminal operation.");
        }
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
            throw new IllegalArgumentException("The bind index must be between 1 and " + binds.length
                                                       + ". The requested index was " + index + ".");
        }
        if (binds[index - 1] != null) {
            throw new IllegalArgumentException("Bind position " + index + " has already been assigned.");
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
                throw new IllegalStateException("A bind value is missing at JDBC position " + (index + 1) + ".");
            }
        }
        terminalState = true;
        return new JdbcOperation(sql, binds.clone(), plan);
    }
}
