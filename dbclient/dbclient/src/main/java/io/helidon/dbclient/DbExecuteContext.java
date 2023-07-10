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
package io.helidon.dbclient;

import java.util.List;

import io.helidon.common.mapper.MapperManager;

/**
 * Execution context.
 */
public class DbExecuteContext implements DbContext {

    private final String statementName;
    private final String statement;
    private final DbClientContext clientContext;

    private DbExecuteContext(Builder builder) {
        this.statementName = builder.statementName;
        this.statement = builder.statement;
        this.clientContext = builder.clientContext;
    }

    /**
     * Get the execution statement name.
     *
     * @return statement name
     */
    public String statementName() {
        return statementName;
    }

    /**
     * Get the execution statement.
     *
     * @return statement
     */
    public String statement() {
        return statement;
    }

    @Override
    public DbStatements statements() {
        return clientContext.statements();
    }

    @Override
    public DbMapperManager dbMapperManager() {
        return clientContext.dbMapperManager();
    }

    @Override
    public MapperManager mapperManager() {
        return clientContext.mapperManager();
    }

    @Override
    public List<DbClientService> clientServices() {
        return clientContext.clientServices();
    }

    @Override
    public String dbType() {
        return clientContext.dbType();
    }

    /**
     * Create a new execution context.
     *
     * @param statementName statement name
     * @param statement     statement
     * @param context       client context
     * @return execution context
     */
    public static DbExecuteContext create(String statementName, String statement, DbClientContext context) {
        return builder()
                .statement(statement)
                .statementName(statementName)
                .clientContext(context)
                .build();
    }

    /**
     * Create Helidon database client context builder.
     *
     * @return database client context builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DbExecuteContext}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, DbExecuteContext> {

        private String statementName;
        private String statement;
        private DbClientContext clientContext;

        /**
         * Set the execution statement.
         *
         * @param statement statement
         * @return updated builder instance
         */
        public Builder statement(String statement) {
            this.statement = statement;
            return this;
        }

        /**
         * Set the execution statement name.
         *
         * @param statementName statement name
         * @return updated builder instance
         */
        public Builder statementName(String statementName) {
            this.statementName = statementName;
            return this;
        }

        /**
         * Set the client context.
         *
         * @param clientContext client context
         * @return updated builder instance
         */
        public Builder clientContext(DbClientContext clientContext) {
            this.clientContext = clientContext;
            return this;
        }

        @Override
        public DbExecuteContext build() {
            return new DbExecuteContext(this);
        }
    }
}
