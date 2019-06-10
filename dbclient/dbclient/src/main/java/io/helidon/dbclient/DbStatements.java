/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.helidon.config.Config;

/**
 * Configuration of statements to be used by database provider.
 */
@FunctionalInterface
public interface DbStatements {
    /**
     * Get statement text for a named statement.
     *
     * @param name name of the statement
     * @return text of the statement (such as SQL code for SQL-based database statements)
     * @throws DbClientException in case the statement name does not exist
     */
    String statement(String name) throws DbClientException;

    /**
     * Builder of statements.
     *
     * @return a builder to customize statements
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create statements from configuration.
     * Statement configuration is expected to be a map of name to statement pairs.
     *
     * @param config configuration of the statements
     * @return statements as read from the configuration
     */
    static DbStatements create(Config config) {
        return DbStatements.builder()
                .config(config)
                .build();
    }

    /**
     * Fluent API builder for {@link io.helidon.dbclient.DbStatements}.
     */
    class Builder implements io.helidon.common.Builder<DbStatements> {
        private final Map<String, String> configuredStatements = new HashMap<>();

        /**
         * Add named database statement to database configuration..
         *
         * @param name      database statement name
         * @param statement database statement {@link String}
         * @return database provider builder
         */
        public Builder addStatement(String name, String statement) {
            Objects.requireNonNull(name, "Statement name must be provided");
            Objects.requireNonNull(statement, "Statement body must be provided");
            configuredStatements.put(name, statement);
            return this;
        }

        /**
         * Set statements from configuration. Each key in the current node is treated as a name of the statement,
         * each value as the statement content.
         *
         * @param config config node located on correct node
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.detach().asMap()
                    .ifPresent(configuredStatements::putAll);
            return this;
        }

        @Override
        public DbStatements build() {
            return new DbStatements() {
                private final Map<String, String> statements = new HashMap<>(configuredStatements);

                @Override
                public String statement(String name) {
                    String statement = statements.get(name);

                    if (null == statement) {
                        throw new DbClientException("Statement named '" + name + "' is not defined");
                    }

                    return statement;
                }

            };
        }
    }
}
