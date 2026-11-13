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

/**
 * Captures one complete parameter set during a synchronous binder invocation.
 */
final class JdbcBatchBindings implements JdbcClient.BatchBindings {
    private final Thread owner = Thread.currentThread();
    private final JdbcOperation.Bind[] binds;
    private final JdbcConfiguredStatement configured;
    private boolean open = true;

    JdbcBatchBindings(int parameterCount, JdbcConfiguredStatement configured) {
        binds = new JdbcOperation.Bind[parameterCount];
        this.configured = configured;
    }

    @Override
    public JdbcClient.BatchBindings bind(int index, Object value) {
        Objects.requireNonNull(value, "The bind value must not be null.");
        validate(index);
        if (!JdbcScalarAccess.supported(value.getClass())) {
            throw new IllegalArgumentException("The batch value must be a supported scalar.");
        }
        binds[index - 1] = new JdbcOperation.Bind(value, null);
        return this;
    }

    @Override
    public JdbcClient.BatchBindings bindNull(int index, JDBCType type) {
        Objects.requireNonNull(type, "The JDBC type must not be null.");
        validate(index);
        validateType(type);
        binds[index - 1] = new JdbcOperation.Bind(null, type);
        return this;
    }

    @Override
    public JdbcClient.BatchBindings bind(String name, Object value) {
        Objects.requireNonNull(name, "The bind name must not be null.");
        Objects.requireNonNull(value, "The bind value must not be null.");
        List<Integer> positions = positions(name);
        if (!JdbcScalarAccess.supported(value.getClass())) {
            throw new IllegalArgumentException("The batch value must be a supported scalar.");
        }
        JdbcOperation.Bind bind = new JdbcOperation.Bind(value, null);
        for (int index : positions) {
            binds[index - 1] = bind;
        }
        return this;
    }

    @Override
    public JdbcClient.BatchBindings bindNull(String name, JDBCType type) {
        Objects.requireNonNull(name, "The bind name must not be null.");
        Objects.requireNonNull(type, "The JDBC type must not be null.");
        List<Integer> positions = positions(name);
        validateType(type);
        JdbcOperation.Bind bind = new JdbcOperation.Bind(null, type);
        for (int index : positions) {
            binds[index - 1] = bind;
        }
        return this;
    }

    void close() {
        open = false;
    }

    JdbcOperation.Bind[] complete() {
        for (JdbcOperation.Bind bind : binds) {
            if (bind == null) {
                throw new IllegalStateException("The current batch item does not bind every SQL parameter position.");
            }
        }
        return binds;
    }

    private static void validateType(JDBCType type) {
        switch (type) {
            case NULL, REF_CURSOR, ARRAY, DISTINCT, JAVA_OBJECT, REF, STRUCT ->
                    throw new IllegalArgumentException("This JDBC type is not supported for batch SQL NULL bindings.");
            default -> {
            }
        }
    }

    private void validateOpen() {
        if (!open || Thread.currentThread() != owner) {
            throw new IllegalStateException("Batch bindings may be used only while the callback is running "
                    + "and on the thread that invoked it.");
        }
    }

    private List<Integer> positions(String name) {
        validateOpen();
        if (configured == null) {
            throw new IllegalStateException("Named batch bindings require a prepared statement definition with input names.");
        }
        List<Integer> positions = configured.positions(name);
        for (int index : positions) {
            validate(index);
        }
        return positions;
    }

    private void validate(int index) {
        validateOpen();
        if (index < 1 || index > binds.length) {
            throw new IllegalArgumentException("The batch binding position is outside the statement parameter range.");
        }
        if (binds[index - 1] != null) {
            throw new IllegalArgumentException("The batch binding position has already been assigned.");
        }
    }
}
