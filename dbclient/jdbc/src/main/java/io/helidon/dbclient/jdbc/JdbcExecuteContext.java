/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.jdbc;

import io.helidon.dbclient.DbExecuteContext;

/**
 * JDBC execution context.
 */
class JdbcExecuteContext extends DbExecuteContext {

    private JdbcExecuteContext(Builder builder) {
        super(builder);
    }

    JdbcParametersConfigBlueprint parametersConfig() {
        return clientContext(JdbcClientContext.class).parametersConfig();
    }

    /**
     * Create a new execution context.
     *
     * @param statementName statement name
     * @param statement     statement
     * @param context       client context
     * @return execution context
     */
    public static JdbcExecuteContext jdbcCreate(String statementName, String statement, JdbcClientContext context) {
        return jdbcBuilder()
                .statement(statement)
                .statementName(statementName)
                .clientContext(context)
                .build();
    }

    /**
     * Create Helidon JDBC database execution context builder.
     *
     * @return database client context builder
     */
    public static Builder jdbcBuilder() {
        return new Builder();
    }

    /**
     * Builder for {@link DbExecuteContext}.
     */
    public static final class Builder extends BuilderBase<Builder, JdbcExecuteContext> {

        @Override
        public JdbcExecuteContext build() {
            return new JdbcExecuteContext(this);
        }

    }

}
