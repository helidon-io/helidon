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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import graphql.ExceptionWhileDataFetching;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.language.SourceLocation;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
import graphql.validation.ValidationError;

import static io.helidon.graphql.server.GraphQlConstants.COLUMN;
import static io.helidon.graphql.server.GraphQlConstants.DATA;
import static io.helidon.graphql.server.GraphQlConstants.ERRORS;
import static io.helidon.graphql.server.GraphQlConstants.EXTENSIONS;
import static io.helidon.graphql.server.GraphQlConstants.LINE;
import static io.helidon.graphql.server.GraphQlConstants.LOCATIONS;
import static io.helidon.graphql.server.GraphQlConstants.MESSAGE;
import static io.helidon.graphql.server.GraphQlConstants.PATH;

class InvocationHandlerImpl implements InvocationHandler {
    private static final Logger LOGGER = Logger.getLogger(InvocationHandlerImpl.class.getName());

    private final String defaultErrorMessage;
    private final Set<String> exceptionDenySet = new HashSet<>();
    private final Set<String> exceptionAllowSet = new HashSet<>();
    private final Map<Class<?>, Boolean> denyExceptions = new ConcurrentHashMap<>();
    private final Map<Class<?>, Boolean> allowExceptions = new ConcurrentHashMap<>();
    private final GraphQLSchema schema;
    private final GraphQL graphQl;
    private final SchemaPrinter schemaPrinter;

    InvocationHandlerImpl(InvocationHandler.Builder builder, GraphQL graphQl) {
        this.schema = builder.schema();
        this.schemaPrinter = builder.schemaPrinter();
        this.defaultErrorMessage = builder.defaultErrorMessage();

        this.graphQl = graphQl;

        this.exceptionDenySet.addAll(builder.denyExceptions());
        this.exceptionAllowSet.addAll(builder.allowExceptions());
    }

    @Override
    public Map<String, Object> execute(String query, String operationName, Map<String, Object> variables) {
        try {
            return doExecute(query, operationName, variables);
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "Failed to execute query " + query, e);
            Map<String, Object> result = new HashMap<>();
            addError(result, e, e.getMessage());
            return result;
        }
    }

    private Map<String, Object> doExecute(String query, String operationName, Map<String, Object> variables) {
        ExecutionContext context = new ExecutionContextImpl();
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .operationName(operationName)
                .context(context)
                .variables(variables)
                .build();

        context.setExecutionInput(executionInput);

        ExecutionResult result = graphQl.execute(executionInput);
        List<GraphQLError> errors = result.getErrors();

        if (errors.isEmpty() && context.hasPartialResultsException()) {
            return processPartialResultsException(result, context);
        } else if (errors.isEmpty()) {
            // no errors
            return result.toSpecification();
        } else {
            // errors
            return processErrors(result, errors);
        }
    }

    private Map<String, Object> processErrors(ExecutionResult result, List<GraphQLError> errors) {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(DATA, result.getData());

        boolean hasErrors = false;
        for (GraphQLError error : errors) {
            if (error instanceof ExceptionWhileDataFetching) {
                ExceptionWhileDataFetching e = (ExceptionWhileDataFetching) error;
                Throwable cause = e.getException().getCause();
                if (cause instanceof Error) {
                    // re-throw the error as this should result in 500 from graphQL endpoint
                    throw (Error) cause;
                }
                hasErrors = true;
                addError(resultMap, error, cause);
            } else if (error instanceof ValidationError) {
                addError(resultMap, error);
                // the spec tests for empty "data" node on validation errors
                if (result.getData() == null) {
                    resultMap.put(DATA, null);
                }

                hasErrors = true;
            }
        }

        if (hasErrors) {
            return resultMap;
        } else {
            return result.toSpecification();
        }
    }

    private Map<String, Object> processPartialResultsException(ExecutionResult result,
                                                               ExecutionContext context) {
        // partial result with errors
        Throwable cause = context.partialResultsException().getCause();
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(DATA, result.getData());
        addError(resultMap, cause, context.partialResultsException().getMessage());
        return resultMap;
    }

    private void addError(Map<String, Object> resultMap,
                          GraphQLError error,
                          Throwable cause) {

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

        if (cause instanceof GraphQLException) {
            addErrorPayload(resultMap, getCheckedMessage(cause), path, line, column, error.getExtensions());
        } else if (cause instanceof Error || cause instanceof RuntimeException) {
            addErrorPayload(resultMap, getUncheckedMessage(cause), path, line, column, error.getExtensions());
        } else {
            addErrorPayload(resultMap,
                            (cause == null) ? error.getMessage() : getCheckedMessage(cause),
                            path,
                            line,
                            column,
                            error.getExtensions());
        }
    }

    private void addError(Map<String, Object> resultMap,
                          GraphQLError error) {

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

        addErrorPayload(resultMap, error.getMessage(), path, line, column, error.getExtensions());
    }

    @SuppressWarnings("unchecked")
    private void addError(Map<String, Object> resultMap,
                          Throwable cause,
                          String originalMessage) {

        Object data = resultMap.get(DATA);
        String path = null;

        if (data instanceof Map) {
            Map<String, Object> dataMap = (Map<String, Object>) data;
            path = dataMap.keySet().stream().findFirst().orElse(null);
        }

        if (cause instanceof GraphQLException) {
            addErrorPayload(resultMap, getCheckedMessage(cause), path);
        } else if (cause instanceof Error || cause instanceof RuntimeException) {
            addErrorPayload(resultMap, getUncheckedMessage(cause), path);
        } else {
            addErrorPayload(resultMap, (cause == null) ? originalMessage : getCheckedMessage(cause), path);
        }
    }

    private void addErrorPayload(Map<String, Object> resultMap, String checkedMessage, String path) {
        addErrorPayload(resultMap, checkedMessage, path, -1, -1, Map.of());
    }

    @SuppressWarnings("unchecked")
    private void addErrorPayload(Map<String, Object> resultMap,
                                 String message,
                                 String path,
                                 int line,
                                 int column,
                                 Map<String, Object> extensions) {
        LinkedList<Map<String, Object>> errorList = (LinkedList<Map<String, Object>>) resultMap
                .computeIfAbsent(ERRORS, it -> new LinkedList<Map<String, Object>>());

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

        errorList.add(newErrorMap);
    }

    private String getUncheckedMessage(Throwable throwable) {
        Class<?> exceptionClazz = throwable.getClass();

        if (allowExceptions.containsKey(exceptionClazz)) {
            return throwable.getMessage();
        }

        // the allow list is exception or its superclass
        // we do not want to use class.forName, as that causes trouble in native image
        do {
            if (exceptionAllowSet.contains(exceptionClazz.getName())) {
                allowExceptions.put(exceptionClazz, true);
                return throwable.getMessage();
            }
            exceptionClazz = exceptionClazz.getSuperclass();
        } while (exceptionClazz != null);

        return defaultErrorMessage;
    }

    private String getCheckedMessage(Throwable throwable) {
        Class<?> exceptionClazz = throwable.getClass();

        // loop through each exception in the deny list and check if
        // the exception on the deny list or a subclass of an exception on the deny list
        if (denyExceptions.containsKey(exceptionClazz)) {
            // cache
            return defaultErrorMessage;
        }

        // the deny list is exception or its superclass
        // we do not want to use class.forName, as that causes trouble in native image
        do {
            if (exceptionDenySet.contains(exceptionClazz.getName())) {
                denyExceptions.put(exceptionClazz, true);
                return defaultErrorMessage;
            }
            exceptionClazz = exceptionClazz.getSuperclass();
        } while (exceptionClazz != null);

        return throwable.getMessage();
    }

    @Override
    public String schemaString() {
        return schemaPrinter.print(schema);
    }

    @Override
    public String defaultErrorMessage() {
        return defaultErrorMessage;
    }

    @Override
    public Set<String> blacklistedExceptions() {
        return Collections.unmodifiableSet(exceptionDenySet);
    }

    @Override
    public Set<String> whitelistedExceptions() {
        return Collections.unmodifiableSet(exceptionAllowSet);
    }
}
