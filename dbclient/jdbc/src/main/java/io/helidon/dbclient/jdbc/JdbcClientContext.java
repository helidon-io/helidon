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

import io.helidon.dbclient.DbClientContext;

/**
 * Helidon JDBC database client context.
 * This instance holds configuration and runtimes that are shared by any execution
 * within this client runtime.
 */
class JdbcClientContext extends DbClientContext {

    private final JdbcParametersConfigBlueprint parametersConfig;


    JdbcClientContext(Builder builder) {
        super(builder);
        this.parametersConfig = builder.parametersConfig;
    }

    JdbcParametersConfigBlueprint parametersConfig() {
        return parametersConfig;
    }

    /**
     * Create Helidon JDBC database client context builder.
     *
     * @return JDBC database client context builder
     */
    static Builder jdbcBuilder() {
        return new Builder();
    }

    /**
     * Builder for {@link io.helidon.dbclient.DbClientContext}.
     */
    static final class Builder extends DbClientContext.BuilderBase<Builder, JdbcClientContext> {

        private JdbcParametersConfigBlueprint parametersConfig;

        private Builder() {
            super();
            this.parametersConfig = JdbcParametersConfig.create();
        }

        /**
         * Configure parameters setter.
         *
         * @param parametersConfig parameters setter configuration
         * @return updated builder instance
         */
        Builder parametersSetter(JdbcParametersConfigBlueprint parametersConfig) {
            this.parametersConfig = parametersConfig;
            return this;
        }

        @Override
        public JdbcClientContext build() {
            return new JdbcClientContext(this);
        }

    }

}
