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

import java.util.function.Supplier;

/**
 * Retains a reusable definition and its execution engine without JDBC resources.
 */
final class JdbcStatementFactory implements Supplier<JdbcClient.Statement> {

    private final JdbcRunner runner;
    private final JdbcConfiguredStatement definition;

    JdbcStatementFactory(JdbcRunner runner, JdbcConfiguredStatement definition) {
        this.runner = runner;
        this.definition = definition;
    }

    @Override
    public JdbcClient.Statement get() {
        return new JdbcStatement(runner, definition);
    }

    @Override
    public String toString() {
        return "JdbcStatementFactory";
    }
}
