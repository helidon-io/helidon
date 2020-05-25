/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.dbclient.common;

import io.helidon.dbclient.DbStatementType;

/**
 * Context of execution of a specific statement.
 */
public class DbStatementContext {
    private final DbClientContext clientContext;
    private final DbStatementType statementType;
    private final String statementName;
    private final String statementText;

    /**
     * Create a new instance using a builder each implementation must extend.
     *
     * @param builder to get required fields from
     */
    protected DbStatementContext(BuilderBase<?> builder) {
        this.clientContext = builder.clientContext;
        this.statementType = builder.statementType;
        this.statementName = builder.statementName;
        this.statementText = builder.statementText;
    }

    /**
     * Create a new builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new instance of this class.
     *
     * @param clientContext DB client context
     * @param statementType type of statement
     * @param statementName name of statement
     * @param statementText text of the statement to execute
     *
     * @return a new statement context
     */
    public static DbStatementContext create(DbClientContext clientContext,
                                            DbStatementType statementType,
                                            String statementName,
                                            String statementText) {
        return builder()
                .clientContext(clientContext)
                .statementType(statementType)
                .statementName(statementName)
                .statementText(statementText)
                .build();
    }

    /**
     * Client context associated with the client executing this statement.
     * @return client context
     */
    public DbClientContext clientContext() {
        return clientContext;
    }

    /**
     * Statement type of this statement.
     * @return type of statement
     */
    public DbStatementType statementType() {
        return statementType;
    }

    /**
     * Name of this statement.
     * @return name of statement
     */
    public String statementName() {
        return statementName;
    }

    /**
     * Statement text as configured.
     * @return statement text
     */
    public String statement() {
        return statementText;
    }

    /**
     * A fluent API builder to create {@link io.helidon.dbclient.common.DbStatementContext}.
     */
    public static final class Builder extends BuilderBase<Builder> implements io.helidon.common.Builder<DbStatementContext> {
        private Builder() {
        }

        @Override
        public DbStatementContext build() {
            return new DbStatementContext(this);
        }
    }

    /**
     * A base builder that must be extended to implement a new {@link io.helidon.dbclient.common.DbStatementContext}.
     *
     * @param <T> type of the builder extending this builder
     */
    public abstract static class BuilderBase<T extends BuilderBase<T>> {
        @SuppressWarnings("unchecked") private final T me = (T) this;
        private DbClientContext clientContext;
        private DbStatementType statementType;
        private String statementName;
        private String statementText;

        /**
         * A no-op constructor.
         */
        protected BuilderBase() {
        }

        /**
         * Configure client context.
         *
         * @param clientContext client context
         * @return updated builder instance
         */
        public T clientContext(DbClientContext clientContext) {
            this.clientContext = clientContext;
            return me;
        }

        /**
         * Configure statement type.
         *
         * @param statementType the type of this statement
         * @return updated builder instance
         */
        public T statementType(DbStatementType statementType) {
            this.statementType = statementType;
            return me;
        }

        /**
         * Configure name of statement.
         *
         * @param statementName name of this statement
         * @return updated builder instance
         */
        public T statementName(String statementName) {
            this.statementName = statementName;
            return me;
        }

        /**
         * Configure text of statement.
         *
         * @param statementText content of this statement
         * @return updated builder instance
         */
        public T statementText(String statementText) {
            this.statementText = statementText;
            return me;
        }
    }
}
