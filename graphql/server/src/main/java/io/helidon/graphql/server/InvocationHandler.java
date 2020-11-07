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

package io.helidon.graphql.server;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.config.Config;

import graphql.GraphQL;
import graphql.execution.SubscriptionExecutionStrategy;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;

import static io.helidon.graphql.server.GraphQlConstants.DEFAULT_ERROR_MESSAGE;

/**
 * Invocation handler that allows execution of GraphQL requests without a WebServer.
 */
public interface InvocationHandler {
    /**
     * Create a handler for GraphQL schema.
     *
     * @param schema schema to use
     * @return a new invocation handler
     */
    static InvocationHandler create(GraphQLSchema schema) {
        return builder().schema(schema).build();
    }

    /**
     * Fluent API builder to configure the invocation handler.
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Execute a GraphQL query.
     *
     * @param query query string
     * @return GraphQL result
     */
    default Map<String, Object> execute(String query) {
        return execute(query, null, Map.of());
    }

    /**
     * Execute a GraphQL query.
     *
     * @param query query string
     * @param operationName operation name
     * @param variables variables to use (optional)
     * @return GraphQL result
     */
    Map<String, Object> execute(String query, String operationName, Map<String, Object> variables);

    /**
     * The schema of this GraphQL endpoint.
     *
     * @return schema as a string
     */
    String schemaString();

    /**
     * Configured default error message.
     *
     * @return default error message
     */
    String defaultErrorMessage();

    /**
     * Configured set of exceptions that are blacklisted.
     *
     * @return blacklisted exception class set
     */
    Set<String> blacklistedExceptions();

    /**
     * Configured set of exceptions that are whitelisted.
     *
     * @return whitelisted exception class set
     */
    Set<String> whitelistedExceptions();

    /**
     * Fluent API builder to configure the invocation handler.
     */
    class Builder implements io.helidon.common.Builder<InvocationHandler> {
        private final Set<String> blacklistedExceptions = new HashSet<>();
        private final Set<String> whitelistedExceptions = new HashSet<>();

        private String defaultErrorMessage = DEFAULT_ERROR_MESSAGE;
        private GraphQLSchema schema;
        private SchemaPrinter schemaPrinter;

        private Builder() {
        }

        @Override
        public InvocationHandler build() {
            if (schema == null) {
                throw new IllegalStateException("GraphQL schema must be configured");
            }

            GraphQL graphQl = GraphQL.newGraphQL(schema)
                    .subscriptionExecutionStrategy(new SubscriptionExecutionStrategy())
                    .build();

            SchemaPrinter.Options options = SchemaPrinter.Options
                    .defaultOptions()
                    .includeDirectives(false)
                    .useAstDefinitions(false)
                    .includeScalarTypes(true);

            schemaPrinter = new SchemaPrinter(options);

            return new InvocationHandlerImpl(this, graphQl);
        }

        /**
         * Update builder from configuration.
         *
         * Configuration options:
         * <table class="config">
         * <caption>Optional configuration parameters</caption>
         * <tr>
         *     <th>key</th>
         *     <th>default value</th>
         *     <th>description</th>
         * </tr>
         * <tr>
         *     <td>default-error-message</td>
         *     <td>{@value io.helidon.graphql.server.GraphQlConstants#DEFAULT_ERROR_MESSAGE}</td>
         *     <td>Error message used for internal errors that are not whitelisted.</td>
         * </tr>
         * <tr>
         *     <td>exception-white-list</td>
         *     <td>&nbsp;</td>
         *     <td>Array of exceptions classes. If an {@link java.lang.Error} or a {@link java.lang.RuntimeException}
         *     of this type is caught, its message will be propagated to the caller.</td>
         * </tr>
         * <tr>
         *     <td>exception-black-list</td>
         *     <td>&nbsp;</td>
         *     <td>Array of exception classes. If a checked {@link java.lang.Exception} is called, its message
         *     is propagated to the caller, unless it is in the blacklist.</td>
         * </tr>
         * </table>
         *
         * @param config configuration to use
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("default-error-message").asString().ifPresent(this::defaultErrorMessage);
            config.get("exception-white-list").asList(String.class)
                    .stream()
                    .flatMap(List::stream)
                    .forEach(this::addWhitelistedException);
            config.get("exception-black-list").asList(String.class)
                    .stream()
                    .flatMap(List::stream)
                    .forEach(this::addBlacklistedException);

            return this;
        }

        /**
         * Configure the GraphQL schema to be used.
         *
         * @param schema schema to handle by this support
         * @return updated builder instance
         */
        public Builder schema(GraphQLSchema schema) {
            this.schema = schema;
            return this;
        }

        /**
         * Default error message to return when an internal server error occurs.
         *
         * @param defaultErrorMessage default error message
         * @return updated builder instance
         */
        public Builder defaultErrorMessage(String defaultErrorMessage) {
            this.defaultErrorMessage = defaultErrorMessage;
            return this;
        }

        /**
         * Blacklisted error classes that will not return error message back to caller.
         *
         * @param classNames names of classes to deny for checked exceptions
         * @return updated builder instance
         */
        public Builder exceptionBlacklist(String[] classNames) {
            for (String className : classNames) {
                addBlacklistedException(className);
            }
            return this;
        }

        /**
         * Add an exception to the blacklist. If a blacklisted exception is thrown, {@link #defaultErrorMessage(String)}
         * is returned instead.
         *
         * @param exceptionClass exception to blacklist
         * @return updated builder instance
         */
        public Builder addBlacklistedException(String exceptionClass) {
            blacklistedExceptions.add(exceptionClass);
            return this;
        }

        /**
         * Whitelisted error classes that will return error message back to caller.
         *
         * @param classNames names of classes to allow for runtime exceptions and errors
         * @return updated builder instance
         */
        public Builder exceptionWhitelist(String[] classNames) {
            for (String className : classNames) {
                addWhitelistedException(className);
            }
            return this;
        }

        /**
         * Add an exception to the whitelist. If a whitelisted exception is thrown, its message is returned, otherwise
         * {@link #defaultErrorMessage(String)} is returned.
         *
         * @param exceptionClass exception to whitelist
         * @return updated builder instance
         */
        public Builder addWhitelistedException(String exceptionClass) {
            whitelistedExceptions.add(exceptionClass);
            return this;
        }

        GraphQLSchema schema() {
            return schema;
        }

        String defaultErrorMessage() {
            return defaultErrorMessage;
        }

        Set<String> denyExceptions() {
            return blacklistedExceptions;
        }

        Set<String> allowExceptions() {
            return whitelistedExceptions;
        }

        SchemaPrinter schemaPrinter() {
            return schemaPrinter;
        }
    }
}
