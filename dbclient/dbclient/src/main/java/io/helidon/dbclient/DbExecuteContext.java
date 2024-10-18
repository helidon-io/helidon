/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

    /**
     * Creates an instance of execution context.
     *
     * @param builder Helidon database client context builder
     */
    protected DbExecuteContext(
            BuilderBase<? extends BuilderBase<?, ? extends DbExecuteContext>, ? extends DbExecuteContext> builder) {
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
    public boolean missingMapParametersAsNull() {
        return clientContext.missingMapParametersAsNull();
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
     * Returns client context cast to it's extending class.
     *
     * @param cls {@link DbClientContext} extending class
     * @return extended client context
     * @param <C> client context extending type
     */
    protected <C extends DbClientContext> C clientContext(Class<C> cls) {
        return cls.cast(clientContext);
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
    public static final class Builder extends BuilderBase<Builder, DbExecuteContext> {

        @Override
        public DbExecuteContext build() {
            return new DbExecuteContext(this);
        }

    }

    /**
     * Base builder for {@link DbExecuteContext}.
     *
     * @param <B> type of the builder
     * @param <T> type of the built instance
     */
    public abstract static class BuilderBase<B extends BuilderBase<B, T>, T extends DbExecuteContext>
            implements io.helidon.common.Builder<B, T> {

        private String statementName;
        private String statement;
        private DbClientContext clientContext;

        /**
         * Set the execution statement.
         *
         * @param statement statement
         * @return updated builder instance
         */
        public B statement(String statement) {
            this.statement = statement;
            return identity();
        }

        /**
         * Set the execution statement name.
         *
         * @param statementName statement name
         * @return updated builder instance
         */
        public B statementName(String statementName) {
            this.statementName = statementName;
            return identity();
        }

        /**
         * Set the client context.
         *
         * @param clientContext client context
         * @return updated builder instance
         */
        public B clientContext(DbClientContext clientContext) {
            this.clientContext = clientContext;
            return identity();
        }

    }
}
