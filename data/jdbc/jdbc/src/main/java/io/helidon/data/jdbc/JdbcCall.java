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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class JdbcCall implements JdbcClient.Call {
    private final JdbcStatement statement;
    private final JdbcRunner runner;
    private final int parameterCount;
    private final List<JdbcCallOutput> outputs = new ArrayList<>();

    JdbcCall(JdbcStatement statement, JdbcRunner runner, int parameterCount) {
        this.statement = statement;
        this.runner = runner;
        this.parameterCount = parameterCount;
    }

    @Override
    public JdbcClient.Call out(String name, int index, JDBCType type) {
        Objects.requireNonNull(name, "The output name must not be null.");
        Objects.requireNonNull(type, "The output type must not be null.");
        return register(new JdbcCallOutput(name, index, type, "", -1));
    }

    @Override
    public JdbcClient.Call out(String name, int index, JDBCType type, String typeName) {
        Objects.requireNonNull(name, "The output name must not be null.");
        Objects.requireNonNull(type, "The output type must not be null.");
        Objects.requireNonNull(typeName, "The database type name must not be null.");
        if (typeName.isBlank()) {
            throw new IllegalArgumentException("The database type name must not be blank.");
        }
        return register(new JdbcCallOutput(name, index, type, typeName, -1));
    }

    @Override
    public JdbcClient.Call out(String name, int index, JDBCType type, int scale) {
        Objects.requireNonNull(name, "The output name must not be null.");
        Objects.requireNonNull(type, "The output type must not be null.");
        if (scale < 0 || (type != JDBCType.NUMERIC && type != JDBCType.DECIMAL)) {
            throw new IllegalArgumentException("An output scale must be nonnegative and requires NUMERIC or DECIMAL.");
        }
        return register(new JdbcCallOutput(name, index, type, "", scale));
    }

    @Override
    public JdbcClient.Call out(String name, JDBCType type) {
        Objects.requireNonNull(name, "The output name must not be null.");
        Objects.requireNonNull(type, "The output type must not be null.");
        return out(name, statement.outputPosition(name), type);
    }

    @Override
    public JdbcClient.Call out(String name, JDBCType type, String typeName) {
        Objects.requireNonNull(name, "The output name must not be null.");
        Objects.requireNonNull(type, "The output type must not be null.");
        Objects.requireNonNull(typeName, "The database type name must not be null.");
        return out(name, statement.outputPosition(name), type, typeName);
    }

    @Override
    public JdbcClient.Call out(String name, JDBCType type, int scale) {
        Objects.requireNonNull(name, "The output name must not be null.");
        Objects.requireNonNull(type, "The output type must not be null.");
        return out(name, statement.outputPosition(name), type, scale);
    }

    @Override
    public JdbcClient.Call returns(JDBCType type) {
        Objects.requireNonNull(type, "The output type must not be null.");
        return out("return", statement.returnPosition(), type);
    }

    @Override
    public JdbcClient.Call returns(JDBCType type, String typeName) {
        Objects.requireNonNull(type, "The output type must not be null.");
        Objects.requireNonNull(typeName, "The database type name must not be null.");
        return out("return", statement.returnPosition(), type, typeName);
    }

    @Override
    public JdbcClient.Call returns(JDBCType type, int scale) {
        Objects.requireNonNull(type, "The output type must not be null.");
        return out("return", statement.returnPosition(), type, scale);
    }

    @Override
    public void execute() {
        map(result -> {
            Objects.requireNonNull(result, "The callable result must not be null.");
            result.skipRemainingResults();
            return Boolean.TRUE;
        });
    }

    @Override
    public JdbcClient.OutParameters outputs() {
        statement.ensureMutable();
        for (JdbcCallOutput output : outputs) {
            if (!output.typeName().isEmpty() || JdbcCallOutput.canonical(output.type()) == null) {
                throw new IllegalStateException("The output registration is outside the detached scalar snapshot profile.");
            }
        }
        return map(result -> {
            Objects.requireNonNull(result, "The callable result must not be null.");
            result.skipRemainingResults();
            return JdbcOutParameters.capture(result, outputs);
        });
    }

    @Override
    public void consume(Consumer<JdbcClient.CallResult> action) {
        Objects.requireNonNull(action, "The call consumer must not be null.");
        map(result -> {
            Objects.requireNonNull(result, "The callable result must not be null.");
            action.accept(result);
            return Boolean.TRUE;
        });
    }

    @Override
    public <T> T map(JdbcClient.CallMapper<T> mapper) {
        Objects.requireNonNull(mapper, "The call mapper must not be null.");
        return runner.call(statement.callOperation(outputs), List.copyOf(outputs), mapper);
    }

    private JdbcClient.Call register(JdbcCallOutput output) {
        statement.ensureMutable();
        if (output.name().isBlank() || output.index() < 1 || output.index() > parameterCount) {
            throw new IllegalArgumentException("An output requires a nonblank name and a valid JDBC position.");
        }
        for (JdbcCallOutput existing : outputs) {
            if (existing.name().equals(output.name()) || existing.index() == output.index()) {
                throw new IllegalArgumentException("Output names and positions must be unique.");
            }
        }
        statement.validateOutputPosition(output.index());
        outputs.add(output);
        return this;
    }
}
