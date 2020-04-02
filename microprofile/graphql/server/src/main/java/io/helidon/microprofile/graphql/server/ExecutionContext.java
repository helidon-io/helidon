/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.graphql.server;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.SubscriptionExecutionStrategy;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;

import static graphql.ExecutionInput.newExecutionInput;

/**
 * Defines a content in which to execute GraphQL commands.
 *
 * @param <C> the context that will be used when executing queries.
 */
public class ExecutionContext<C> {

    private static final Logger LOGGER = Logger.getLogger(ExecutionContext.class.getName());

    /**
     * An empty map.
     */
    static final Map<String, Object> EMPTY_MAP = Collections.emptyMap();

    /**
     * {@link GraphQL} instance to use for execution.
     */
    private GraphQL graphQL;

    /**
     * {@link GraphQLSchema} instance to use for execution.
     */
    private GraphQLSchema graphQLSchema;

    /**
     * {@link SchemaGenerator} instance to use for {@link Schema} generation.
     */
    private final SchemaGenerator schemaGenerator;

    /**
     * {@link Schema} used.
     */
    private Schema schema;

    /**
     * A context to pass to GrapQL for execution.
     */
    private C context;

    /**
     * Return the {@link GraphQLSchema} instance created.
     *
     * @return the {@link GraphQLSchema} instance
     */
    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    /**
     * Return the generated {@link Schema}.
     *
     * @return the generated {@link Schema}
     */
    public Schema getSchema() {
        return schema;
    }

    /**
     * Construct an execution context in which ti execute GraphQL queries.
     *
     * @param context context
     */
    public ExecutionContext(C context) {
        try {
            this.schemaGenerator = new SchemaGenerator();
            this.schema = schemaGenerator.generateSchema();
            this.graphQLSchema = schema.generateGraphQLSchema();
            this.context = context;
            SchemaPrinter.Options options = SchemaPrinter.Options
                    .defaultOptions().includeDirectives(false)
                    .includeScalarTypes(true)
                    .includeExtendedScalarTypes(true);
            SchemaPrinter schemaPrinter = new SchemaPrinter(options);

            GraphQL.Builder builder = GraphQL.newGraphQL(this.graphQLSchema)
                    .subscriptionExecutionStrategy(new SubscriptionExecutionStrategy());

            //            Instrumentation instrumentation = null; // getInstrumentation();
            //
            //            if (instrumentation != null) {
            //                builder.instrumentation(instrumentation);
            //            }

            graphQL = builder.build();

            LOGGER.info("Generated schema:\n" + schemaPrinter.print(graphQLSchema));
        } catch (Exception e) {
            String message = "Unable to build GraphQL Schema: " + e;
            LOGGER.warning(message);
            throw new RuntimeException(message, e);
        }
    }

    /**
     * Return a new {@link DefaultContext}.
     *
     * @return a new {@link DefaultContext
     */
    public static DefaultContext getDefaultContext() {
        return new DefaultContext();
    }

    /**
     * Execute the given query and return the the {@link ExecutionResult}.
     *
     * @param query query to execute
     * @return the {@link ExecutionResult}
     */
    public ExecutionResult execute(String query) {
        return execute(query, null, EMPTY_MAP);
    }

    /**
     * Execute the given query and return the the {@link ExecutionResult} for the given operation name.
     *
     * @param query         query to execute
     * @param operationName the name of the operation
     * @return the {@link ExecutionResult}
     */
    public ExecutionResult execute(String query, String operationName) {
        return execute(query, operationName, EMPTY_MAP);
    }

    /**
     * Execute the given query and return the the {@link ExecutionResult} for the given operation name.
     *
     * @param query         query to execute
     * @param operationName the name of the operation
     * @param mapVariables  the map of variables to pass through
     * @return the {@link ExecutionResult}
     */
    public ExecutionResult execute(String query, String operationName, Map<String, Object> mapVariables) {
        ExecutionInput.Builder executionInput = newExecutionInput()
                .query(query)
                .operationName(operationName)
                .context(context)
                .variables(mapVariables == null ? EMPTY_MAP : mapVariables);

        return graphQL.execute(executionInput.build());
    }
}
