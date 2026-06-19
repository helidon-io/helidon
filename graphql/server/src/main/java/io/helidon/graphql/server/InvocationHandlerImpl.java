/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;

import graphql.ExceptionWhileDataFetching;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.ParseAndValidate;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.language.Document;
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
import static io.helidon.graphql.server.internal.GraphQlContextKeys.PARSED_DOCUMENT;

class InvocationHandlerImpl implements InvocationHandler {
    private static final System.Logger LOGGER = System.getLogger(InvocationHandlerImpl.class.getName());
    private static final Pattern VALIDATION_ERROR_PATTERN = Pattern.compile("Validation error \\((\\w+)@\\[(.*)]\\) : (.*$)");
    private static final Pattern ERROR_ENUM_REPLACE_PATTERN =
            Pattern.compile("Literal value not in allowable values for enum '.*?' - ('.*\\{.*}')");
    private static final Pattern INVALID_TYPE_REPLACE_PATTERN =
            Pattern.compile("- Expected an AST type of ('.*?') but it was a ('.*?') (@.*?)$");

    private final String defaultErrorMessage;
    private final Set<String> exceptionDenySet = new HashSet<>();
    private final Set<String> exceptionAllowSet = new HashSet<>();
    private final Map<Class<?>, Boolean> denyExceptions = new ConcurrentHashMap<>();
    private final Map<Class<?>, Boolean> allowExceptions = new ConcurrentHashMap<>();
    private final GraphQLSchema schema;
    private final GraphQL graphQl;
    private final SchemaPrinter schemaPrinter;
    private final List<InvocationHandler.ContextHandler> contextHandlers;

    InvocationHandlerImpl(InvocationHandler.Builder builder, GraphQL graphQl) {
        this.schema = builder.schema();
        this.schemaPrinter = builder.schemaPrinter();
        this.defaultErrorMessage = builder.defaultErrorMessage();
        this.contextHandlers = List.copyOf(builder.contextHandlers());

        this.graphQl = graphQl;

        this.exceptionDenySet.addAll(builder.denyExceptions());
        this.exceptionAllowSet.addAll(builder.allowExceptions());
    }

    static PreparsedDocumentProvider preparsedDocumentProvider(GraphQLSchema schema) {
        Objects.requireNonNull(schema);
        return (executionInput, parseAndValidate) -> preparsedDocument(schema, executionInput, parseAndValidate);
    }

    private static CompletableFuture<PreparsedDocumentEntry> preparsedDocument(
            GraphQLSchema schema,
            ExecutionInput executionInput,
            Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidate) {

        Object parsedDocument = executionInput.getGraphQLContext().get(PARSED_DOCUMENT);
        if (parsedDocument instanceof Document document) {
            return CompletableFuture.completedFuture(new PreparsedDocumentEntry(document,
                                                                               validate(schema, executionInput, document)));
        }
        return CompletableFuture.completedFuture(parseAndValidate.apply(executionInput));
    }

    @SuppressWarnings("unchecked")
    private static List<ValidationError> validate(GraphQLSchema schema,
                                                  ExecutionInput executionInput,
                                                  Document document) {
        Object validationPredicate = executionInput.getGraphQLContext()
                .getOrDefault(ParseAndValidate.INTERNAL_VALIDATION_PREDICATE_HINT, (Predicate<Class<?>>) _ -> true);
        Locale locale = executionInput.getLocale() == null ? Locale.getDefault() : executionInput.getLocale();
        return ParseAndValidate.validate(schema, document, (Predicate<Class<?>>) validationPredicate, locale);
    }

    @Override
    public Map<String, Object> execute(String query, String operationName, Map<String, Object> variables) {
        return executeInternal(query, operationName, variables, Map.of());
    }

    @Override
    public Map<String, Object> execute(String query,
                                       Map<String, Object> variables,
                                       Map<String, Object> contextValues) {
        return executeInternal(query, null, variables, contextValues);
    }

    @Override
    public Map<String, Object> execute(String query,
                                       String operationName,
                                       Map<String, Object> variables,
                                       Map<String, Object> contextValues) {
        Objects.requireNonNull(operationName);
        return executeInternal(query, operationName, variables, contextValues);
    }

    private Map<String, Object> executeInternal(String query,
                                                String operationName,
                                                Map<String, Object> variables,
                                                Map<String, Object> contextValues) {
        Objects.requireNonNull(query);
        Objects.requireNonNull(variables);
        Objects.requireNonNull(contextValues);
        try {
            return doExecute(query, operationName, variables, contextValues);
        } catch (RuntimeException e) {
            LOGGER.log(Level.DEBUG, "Failed to execute query " + query, e);
            Map<String, Object> result = new HashMap<>();
            addError(result, e, e.getMessage());
            return result;
        }
    }

    private Map<String, Object> doExecute(String query,
                                          String operationName,
                                          Map<String, Object> variables,
                                          Map<String, Object> contextValues) {
        Context commonContext = commonContext(contextValues);
        ExecutionContext context = new ExecutionContextImpl(commonContext);
        contextValues.forEach(context::setContextValue);
        context.setContextValue(ExecutionContext.HELIDON_CONTEXT_KEY, commonContext);
        context.setContextValue(ExecutionContext.EXECUTION_CONTEXT_KEY, context);
        return Contexts.runInContext(commonContext, () -> doExecuteInContext(query,
                                                                             operationName,
                                                                             variables,
                                                                             context,
                                                                             commonContext,
                                                                             contextValues));
    }

    private Map<String, Object> doExecuteInContext(String query,
                                                   String operationName,
                                                   Map<String, Object> variables,
                                                   ExecutionContext context,
                                                   Context commonContext,
                                                   Map<String, Object> contextValues) {
        contextHandlers.forEach(handler -> handler.update(context));
        context.setContextValue(ExecutionContext.HELIDON_CONTEXT_KEY, commonContext);
        context.setContextValue(ExecutionContext.EXECUTION_CONTEXT_KEY, context);

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .operationName(operationName)
                .context(context)
                .graphQLContext(builder -> {
                    builder.of(contextValues);
                    builder.put(ExecutionContext.HELIDON_CONTEXT_KEY, commonContext);
                    builder.put(ExecutionContext.EXECUTION_CONTEXT_KEY, context);
                })
                .variables(variables)
                .build();

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

    private static List<Object> path(GraphQLError error) {
        List<Object> path = error.getPath();
        if (path == null) {
            return List.of();
        }
        return List.copyOf(path);
    }

    private void addError(Map<String, Object> resultMap,
                          GraphQLError error,
                          Throwable cause) {

        int line = -1;
        int column = -1;
        List<Object> path = path(error);

        List<SourceLocation> locations = error.getLocations();
        if (locations != null && locations.size() > 0) {
            SourceLocation sourceLocation = locations.get(0);
            line = sourceLocation.getLine();
            column = sourceLocation.getColumn();
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
        List<Object> path = path(error);

        List<SourceLocation> locations = error.getLocations();
        if (locations != null && locations.size() > 0) {
            SourceLocation sourceLocation = locations.get(0);
            line = sourceLocation.getLine();
            column = sourceLocation.getColumn();
        }

        addErrorPayload(resultMap, fixMessage(error.getMessage()), path, line, column, error.getExtensions());
    }

    // this works around https://github.com/eclipse/microprofile-graphql/issues/520
    // once the TCK is fixed, we can remove this work-around
    // this is depending on version of GraphQL (heavily), and any change in error message format
    // will result in wrong responses
    private String fixMessage(String message) {
        if (!message.startsWith("Validation error")) {
            return message;
        }
        /*
        What we get (first) and what we want (second)
        Validation error (FieldUndefined@[allHeroes/weaknesses]) : Field 'weaknesses' in type 'SuperHero' is undefined
        Validation error of type FieldUndefined: Field 'weaknesses' in type 'SuperHero' is undefined @ 'allHeroes/weaknesses'
         */
        /*
        1: type of error (FieldUndefined)
        2: path (allHeroes/weaknesses)
        3: Message (Field 'weaknesses' in type 'SuperHero' is undefined)
         */
        Matcher matcher = VALIDATION_ERROR_PATTERN.matcher(message);
        if (matcher.matches()) {
            String fixedMessage = "Validation error of type " + matcher.group(1)
                    + ": " + matcher.group(3)
                    + " @ '" + matcher.group(2) + "'";

            if (fixedMessage.contains("Literal value not in allowable values for enum")) {
             /*
             What we get (first) and what we want (second)
             Validation error (WrongType@[createNewHero]) : argument 'hero.tshirtSize' with value 'EnumValue{name='XLTall'}'
                is not a valid 'ShirtSize' - Literal value not in allowable values for enum 'ShirtSize'
                - 'EnumValue{name='XLTall'}'
             Validation error of type WrongType: argument 'hero.tshirtSize' with value 'EnumValue{name='XLTall'}' is not a valid
                'ShirtSize' - Expected enum literal value not in allowable values
                -  'EnumValue{name='XLTall'}'. @ 'createNewHero'
             */
                // enum failures
                return ERROR_ENUM_REPLACE_PATTERN.matcher(fixedMessage)
                        .replaceAll("Expected enum literal value not in allowable values -  $1.");
            }

            /*
            actual graphql message
            fixed graphql message
            expected graphql message
            Validation error (WrongType@[updateItemPowerLevel]) : argument 'powerLevel' with value
                    'StringValue{value='Unlimited'}' is not a valid 'Int' -
                    Expected an AST type of 'IntValue' but it was a 'StringValue'
            Validation error of type WrongType: argument 'powerLevel' with value 'StringValue{value='Unlimited'}'
                    is not a valid 'Int' - Expected an AST type of 'IntValue' but it
                    was a 'StringValue' @ 'updateItemPowerLevel'
            Validation error of type WrongType: argument 'powerLevel' with value 'StringValue{value='Unlimited'}'
                    is not a valid 'Int' - Expected AST type 'IntValue' but
                    was 'StringValue'. @ 'updateItemPowerLevel
             */
            if (fixedMessage.contains("type WrongType")) {
                return INVALID_TYPE_REPLACE_PATTERN.matcher(fixedMessage)
                        .replaceAll("- Expected AST type $1 but was $2. $3");
            }

            return fixedMessage;
        } else {
            return message;
        }
    }

    private Context commonContext(Map<String, Object> contextValues) {
        Object value = contextValues.get(ExecutionContext.HELIDON_CONTEXT_KEY);
        if (value instanceof Context context) {
            return context;
        }
        return Contexts.context().orElseGet(Context::create);
    }

    @SuppressWarnings("unchecked")
    private void addError(Map<String, Object> resultMap,
                          Throwable cause,
                          String originalMessage) {

        Object data = resultMap.get(DATA);
        List<Object> path = List.of();

        if (data instanceof Map) {
            Map<String, Object> dataMap = (Map<String, Object>) data;
            path = dataMap.keySet()
                    .stream()
                    .findFirst()
                    .map(it -> (Object) it)
                    .map(List::of)
                    .orElseGet(List::of);
        }

        if (cause instanceof GraphQLException) {
            addErrorPayload(resultMap, getCheckedMessage(cause), path);
        } else if (cause instanceof Error || cause instanceof RuntimeException) {
            addErrorPayload(resultMap, getUncheckedMessage(cause), path);
        } else {
            addErrorPayload(resultMap, (cause == null) ? originalMessage : getCheckedMessage(cause), path);
        }
    }

    private void addErrorPayload(Map<String, Object> resultMap, String checkedMessage, List<Object> path) {
        addErrorPayload(resultMap, checkedMessage, path, -1, -1, Map.of());
    }

    @SuppressWarnings("unchecked")
    private void addErrorPayload(Map<String, Object> resultMap,
                                 String message,
                                 List<Object> path,
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

        if (!path.isEmpty()) {
            newErrorMap.put(PATH, path);
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
