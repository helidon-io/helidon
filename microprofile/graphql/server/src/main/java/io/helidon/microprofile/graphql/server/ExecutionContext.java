/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import io.helidon.config.Config;

import graphql.ExceptionWhileDataFetching;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.execution.SubscriptionExecutionStrategy;
import graphql.language.SourceLocation;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
import graphql.validation.ValidationError;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.graphql.ConfigKey;

import static graphql.ExecutionInput.newExecutionInput;

/**
 * Defines a content in which to execute GraphQL commands.
 *
 * @param <C> the context that will be used when executing queries.
 */
public class ExecutionContext<C> {

    /**
     * Key for errors.
     */
    protected static final String ERRORS = "errors";

    /**
     * Key for extensions.
     */
    protected static final String EXTENSIONS = "extensions";

    /**
     * Key for locations.
     */
    protected static final String LOCATIONS = "locations";

    /**
     * Key for message.
     */
    protected static final String MESSAGE = "message";

    /**
     * Key for data.
     */
    protected static final String DATA = "data";

    /**
     * Key for line.
     */
    protected static final String LINE = "line";

    /**
     * Key for column.
     */
    protected static final String COLUMN = "column";

    /**
     * Key for path.
     */
    protected static final String PATH = "path";

    /**
     * Empty String.
     */
    private static final String EMPTY = "";

    /**
     * Config parts for default error message.
     */
    protected static final String[] MESSAGE_PARTS = ConfigKey.DEFAULT_ERROR_MESSAGE.split("\\.");

    /**
     * Config parts for whitelist.
     */
    protected static final String[] WHITELIST_PARTS = ConfigKey.EXCEPTION_WHITE_LIST.split("\\.");

    /**
     * Config parts for blacklist.
     */
    protected static final String[] BLACKLIST_PARTS = ConfigKey.EXCEPTION_BLACK_LIST.split("\\.");

    /**
     * Logger.
     */
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
     * {@link Schema} used.
     */
    private Schema schema;

    /**
     * A context to pass to GraphQL for execution.
     */
    private C context;

    /**
     * Default error message.
     */
    private String defaultErrorMessage;

    /**
     * List of blacklisted exceptions to hide.
     */
    private final List<String> exceptionBlacklist = new ArrayList<>();

    /**
     * List of whitelisted exceptions to allow through.
     */
    private final List<String> exceptionWhitelist = new ArrayList<>();

    /**
     * Configuration.
     */
    private Config config;

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
     * Construct an execution context in which to execute GraphQL queries.
     *
     * @param context context
     */
    public ExecutionContext(C context) {
        try {
            configureExceptionHandling();
            SchemaGenerator schemaGenerator = new SchemaGenerator();
            schema = schemaGenerator.generateSchema();
            graphQLSchema = schema.generateGraphQLSchema();
            this.context = context;
            SchemaPrinter.Options options = SchemaPrinter.Options
                    .defaultOptions()
                    .includeDirectives(false)
                    .includeScalarTypes(true)
                    .includeExtendedScalarTypes(true);
            SchemaPrinter schemaPrinter = new SchemaPrinter(options);

            GraphQL.Builder builder = GraphQL.newGraphQL(this.graphQLSchema)
                    .subscriptionExecutionStrategy(new SubscriptionExecutionStrategy());

            graphQL = builder.build();

            LOGGER.info("Generated schema:\n" + schemaPrinter.print(graphQLSchema));
        } catch (Throwable t) {
            // since we cannot generate the schema, just log the message and throw it
            String message = "Unable to build GraphQL Schema: " + t.getMessage();
            LOGGER.warning(message);
            throw new RuntimeException(message, t);
        }
    }

    /**
     * Configure microprofile exception handling.
     */
    private void configureExceptionHandling() {
        config = (Config) ConfigProviderResolver.instance().getConfig();

        defaultErrorMessage = config.get(MESSAGE_PARTS[0])
                .get(MESSAGE_PARTS[1])
                .get(MESSAGE_PARTS[2])
                .asString().orElse("Server Error");

        String whitelist = config.get(WHITELIST_PARTS[0])
                .get(WHITELIST_PARTS[1])
                .get(WHITELIST_PARTS[2])
                .asString().orElse(EMPTY);
        String blacklist = config.get(BLACKLIST_PARTS[0])
                .get(BLACKLIST_PARTS[1])
                .get(BLACKLIST_PARTS[2])
                .asString().orElse(EMPTY);

        if (!EMPTY.equals(whitelist)) {
            exceptionWhitelist.addAll(Arrays.asList(whitelist.split(",")));
        }

        if (!EMPTY.equals(blacklist)) {
            exceptionBlacklist.addAll(Arrays.asList(blacklist.split(",")));
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
     * @return the {@link Map} containing the execution result
     */
    public Map<String, Object> execute(String query) {
        return execute(query, null, EMPTY_MAP);
    }

    /**
     * Execute the given query and return the the {@link ExecutionResult} for the given operation name.
     *
     * @param query         query to execute
     * @param operationName the name of the operation
     * @return the {@link Map} containing the execution result
     */
    public Map<String, Object> execute(String query, String operationName) {
        return execute(query, operationName, EMPTY_MAP);
    }

    /**
     * Execute the given query and return the the {@link ExecutionResult} for the given operation name.
     *
     * @param query         query to execute
     * @param operationName the name of the operation
     * @param mapVariables  the map of variables to pass through
     * @return the {@link Map} containing the execution result
     */
    public Map<String, Object> execute(String query, String operationName, Map<String, Object> mapVariables) {
        try {
            ExecutionInput executionInput = newExecutionInput()
                    .query(query)
                    .operationName(operationName)
                    .context(context)
                    .variables(mapVariables == null ? EMPTY_MAP : mapVariables)
                    .build();

            ExecutionResult result = graphQL.execute(executionInput);
            List<GraphQLError> errors = result.getErrors();
            boolean hasErrors = false;
            Map<String, Object> mapErrors = newErrorPayload(result.getData());
            if (errors != null && errors.size() > 0) {
                for (GraphQLError error : errors) {
                    if (error instanceof ExceptionWhileDataFetching) {
                        ExceptionWhileDataFetching e = (ExceptionWhileDataFetching) error;
                        Throwable cause = e.getException().getCause();
                        hasErrors = true;
                        if (cause != null) {
                            if (cause instanceof Error || cause instanceof RuntimeException) {
                                // unchecked
                                addErrorPayload(mapErrors, getUncheckedMessage(cause), error);
                            } else {
                                // checked
                                addErrorPayload(mapErrors, getCheckedMessage(cause), error);
                            }
                        }
                    } else if (error instanceof ValidationError) {
                        addErrorPayload(mapErrors, error.getMessage(), error);
                        // the spec tests for empty "data" node on validation errors
                        if (!mapErrors.containsKey(DATA)) {
                            mapErrors.put(DATA, null);
                        }

                        hasErrors = true;
                    }
                }
            }

            return hasErrors ? mapErrors : result.toSpecification();
        } catch (RuntimeException | Error e) {
            // unchecked exception
            Map<String, Object> mapErrors = getErrorPayload(getUncheckedMessage(e), e);
            LOGGER.warning(e.getMessage());
            return mapErrors;
        } catch (Exception e) {
            // checked exception
            Map<String, Object> mapErrors = getErrorPayload(getCheckedMessage(e), e);
            LOGGER.warning(e.getMessage());
            return mapErrors;
        }
    }

    /**
     * Return the a new error payload.
     *
     * @param message the message to add
     * @param t       the {@link Throwable} that caused the error
     * @return the a new error payload
     */
    private Map<String, Object> getErrorPayload(String message, Throwable t) {
        Map<String, Object> mapErrors = newErrorPayload();
        addErrorPayload(mapErrors, message, (String) null);
        return mapErrors;
    }

    /**
     * Return the message for an un-checked exception. This will return the default message unless the unchecked exception is on
     * the whitelist.
     *
     * @param throwable {@link Throwable}
     * @return the message for an un-checked exception
     */
    protected String getUncheckedMessage(Throwable throwable) {
        String exceptionClass = throwable.getClass().getName();
        return exceptionWhitelist.contains(exceptionClass)
                ? throwable.toString()
                : defaultErrorMessage;
    }

    /**
     * Return the message for a checked exception. This will return the exception message unless the exception is on the
     * blacklist.
     *
     * @param throwable {@link Throwable}
     * @return the message for a checked exception
     */
    protected String getCheckedMessage(Throwable throwable) {
        String exceptionClass = throwable.getClass().getName();
        return exceptionBlacklist.contains(exceptionClass)
                ? defaultErrorMessage
                : throwable.toString();
    }

    /**
     * Return the default error message.
     *
     * @return the default error message
     */
    public String getDefaultErrorMessage() {
        return defaultErrorMessage;
    }

    /**
     * Return the list blacklisted exceptions to hide.
     *
     * @return the list blacklisted exceptions to hide
     */
    public List<String> getExceptionBlacklist() {
        return exceptionBlacklist;
    }

    /**
     * Return the list of whitelisted exceptions to allow through.
     *
     * @return the list of whitelisted exceptions to allow through
     */
    public List<String> getExceptionWhitelist() {
        return exceptionWhitelist;
    }

    /**
     * Return the {@link Config}.
     *
     * @return the {@link Config}
     */
    public Config getConfig() {
        return config;
    }

    /**
     * Generate a new error payload.
     *
     * @return a new error payload
     */
    protected Map<String, Object> newErrorPayload() {
        return newErrorPayload(null);
    }

    /**
     * Generate a new error payload.
     *
     * @param initialData {@link Map} of initial data.
     * @return a new error payload
     */
    protected Map<String, Object> newErrorPayload(Map<String, Object> initialData) {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put(DATA, initialData);
        errorMap.put(ERRORS, new ArrayList<Map<String, Object>>());

        return errorMap;
    }

    /**
     * Add a message to the error payload.
     *
     * @param errorMap   error {@link Map} to add to
     * @param message    message to add
     * @param line       line number of message
     * @param column     column of message
     * @param extensions any extensions to add
     * @param path       path to add
     */
    @SuppressWarnings("unchecked")
    protected void addErrorPayload(Map<String, Object> errorMap,
                                   String message,
                                   int line,
                                   int column,
                                   Map<String, Object> extensions,
                                   String path) {
        List<Map<String, Object>> listErrors = (List<Map<String, Object>>) errorMap.get(ERRORS);

        if (listErrors == null) {
            throw new IllegalArgumentException("Please initialize errorMap via newErrorPayload");
        }

        Map<String, Object> newErrorMap = new HashMap<>();

        // inner map
        newErrorMap.put(MESSAGE, message);

        if (line != -1 && column != -1) {
            ArrayList<Map<String, Object>> listLocations = new ArrayList<>();
            listLocations.add(Map.of(LINE, line, COLUMN, column));
            newErrorMap.put(LOCATIONS, listLocations);
        }

        if (extensions != null && extensions.size() > 0) {
            newErrorMap.put(EXTENSIONS, extensions);
        }

        if (path != null) {
            newErrorMap.put(PATH, List.of(path));
        }

        listErrors.add(newErrorMap);
    }

    /**
     * Add a message to the error payload.
     *
     * @param errorMap   error {@link Map} to add to
     * @param message    message to add
     * @param extensions any extensions to add
     * @param path       path to add
     */
    protected void addErrorPayload(Map<String, Object> errorMap, String message, Map<String, Object> extensions, String path) {
        addErrorPayload(errorMap, message, -1, -1, extensions, path);
    }

    /**
     * Add a message to the error payload.
     *
     * @param errorMap error {@link Map} to add to
     * @param message  message to add
     * @param path     path to add
     */
    protected void addErrorPayload(Map<String, Object> errorMap, String message, String path) {
        addErrorPayload(errorMap, message, -1, -1, EMPTY_MAP, path);
    }

    /**
     * Add error payload from a given {@link GraphQLError}.
     *
     * @param errorMap error {@link Map} to add to
     * @param message  message to add
     * @param error    {@link GraphQLError} to retireve infornation from
     */
    protected void addErrorPayload(Map<String, Object> errorMap, String message, GraphQLError error) {
        int line = -1;
        int column = -1;
        String path = null;
        List<SourceLocation> locations = error.getLocations();
        if (locations != null && locations.size() > 0) {
            SourceLocation sourceLocation = locations.get(0);
            line = sourceLocation.getLine();
            column = sourceLocation.getColumn();
        }

        List<Object> listPath = error.getPath();
        if (listPath != null && listPath.size() > 0) {
            path = listPath.get(0).toString();
        }

        addErrorPayload(errorMap, message, line, column, error.getExtensions(), path);
    }

}
