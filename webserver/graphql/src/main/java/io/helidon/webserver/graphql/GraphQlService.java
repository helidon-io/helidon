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

package io.helidon.webserver.graphql;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.Api;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.common.uri.UriQuery;
import io.helidon.config.Config;
import io.helidon.graphql.server.ExecutionContext;
import io.helidon.graphql.server.GraphQlConstants;
import io.helidon.graphql.server.GraphQlContextKeys;
import io.helidon.graphql.server.InvocationHandler;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonNumber;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValue;
import io.helidon.json.binding.JsonBinding;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpEntryPoint;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.SecureHandler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.parser.ParserEnvironment;
import graphql.parser.ParserOptions;
import graphql.schema.GraphQLSchema;

/**
 * Support for GraphQL for Helidon WebServer.
 */
public class GraphQlService implements HttpService {
    private static final TypeName AUTHORIZED_TYPE = TypeName.create("io.helidon.security.annotations.Authorized");
    static final String RESOLVER_INVOCATION_KEY = GraphQlService.class.getName() + ".resolver-invocation";
    private static final TypedElementInfo POST_METHOD = requestMethod("<graphql-post>");
    private static final TypedElementInfo GET_METHOD = requestMethod("<graphql-get>");
    private static final TypedElementInfo SCHEMA_METHOD = requestMethod("<graphql-schema>");

    private final String context;
    private final String schemaUri;
    private final InvocationHandler invocationHandler;
    private final boolean permitAll;
    private final Handler graphQlPost;
    private final Handler graphQlGet;
    private final Handler introspectionAuthorization;
    private final Handler noResolverAuthorization;
    private final Handler graphQlSchema;
    private final boolean parsePostRequest;
    private final JsonBinding jsonBinding;

    private GraphQlService(Builder builder) {
        this.context = builder.context;
        this.schemaUri = builder.schemaUri;
        this.invocationHandler = builder.handler;
        this.permitAll = builder.permitAll;
        this.graphQlPost = builder.postEntryPoint.apply(this::graphQlPost);
        this.graphQlGet = builder.getEntryPoint.apply(this::graphQlGet);
        this.introspectionAuthorization = builder.introspectionAuthorization.apply(this::graphQlRequest);
        this.noResolverAuthorization = builder.noResolverAuthorization.apply(this::graphQlResponse);
        this.graphQlSchema = builder.schemaEntryPoint.apply(this::graphQlSchema);
        this.parsePostRequest = builder.parsePostRequest;
        this.jsonBinding = JsonBinding.create();
    }

    /**
     * Create GraphQL support for a GraphQL schema.
     *
     * @param schema schema to use for GraphQL
     * @return a new support to register with {@link io.helidon.webserver.WebServer}
     *         {@link io.helidon.webserver.http.HttpRouting}
     */
    public static GraphQlService create(GraphQLSchema schema) {
        return builder()
                .invocationHandler(InvocationHandler.create(schema))
                .build();
    }

    /**
     * A builder for fine grained configuration of the support.
     *
     * @return a new fluent API builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void routing(HttpRules rules) {
        // schema
        String schemaPath = context.endsWith("/")
                ? context.substring(0, context.length() - 1) + schemaUri
                : context + schemaUri;
        rules.get(schemaPath, protect(graphQlSchema));
        // get and post endpoint for graphQL
        rules.get(context, protect(graphQlGet))
                .post(context, protect(graphQlPost));
    }

    private Handler protect(Handler handler) {
        if (permitAll) {
            return handler;
        }
        return SecureHandler.authenticate().wrap(handler);
    }

    private static TypedElementInfo requestMethod(String name, Annotation... annotations) {
        return TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName(name)
                .typeName(TypeNames.PRIMITIVE_VOID)
                .addAnnotations(List.of(annotations))
                .build();
    }

    // handle POST request for GraphQL endpoint
    private void graphQlPost(ServerRequest req, ServerResponse res) throws Exception {
        JsonObject entity = JsonParser.create(req.content().inputStream()).readJsonObject();
        String query = stringValue(entity, "query");
        String operationName = normalizeOperationName(stringValue(entity, "operationName"));
        Optional<ParsedQuery> parsedQuery = parsePostRequest ? parseQuery(query, operationName) : Optional.empty();
        dispatchRequest(req,
                        res,
                        new GraphQlRequest(query,
                                           operationName,
                                           toVariableMap(entity.value("variables").orElse(null)),
                                           parsedQuery,
                                           false));
    }

    // handle GET request for GraphQL endpoint
    private void graphQlGet(ServerRequest req, ServerResponse res) throws Exception {
        UriQuery queryParams = req.query();
        String query = queryParams.first("query").orElseThrow(() -> new IllegalStateException("Query must be defined"));
        String operationName = normalizeOperationName(queryParams.first("operationName").orElse(null));
        Optional<ParsedQuery> parsedQuery = parseQuery(query, operationName);
        Map<String, Object> variables = parsedQuery.map(ParsedQuery::mutation).orElse(false)
                ? Map.of()
                : queryParams.first("variables")
                        .map(this::toVariableMap)
                        .orElseGet(Map::of);

        dispatchRequest(req, res, new GraphQlRequest(query, operationName, variables, parsedQuery, true));
    }

    // handle GET request to obtain GraphQL schema
    private void graphQlSchema(ServerRequest req, ServerResponse res) {
        res.send(invocationHandler.schemaString());
    }

    private void dispatchRequest(ServerRequest req, ServerResponse res, GraphQlRequest graphQlRequest) throws Exception {
        req.context().register(graphQlRequest);
        boolean getRequest = graphQlRequest.getRequest();
        boolean mutation = graphQlRequest.parsedQuery().map(ParsedQuery::mutation).orElse(false);
        if (getRequest && mutation) {
            graphQlRequest(req, res);
        } else if (graphQlRequest.parsedQuery().map(ParsedQuery::introspectionOnly).orElse(false)) {
            introspectionAuthorization.handle(req, res);
        } else {
            graphQlRequest(req, res);
        }
    }

    private void graphQlRequest(ServerRequest req, ServerResponse res) throws Exception {
        GraphQlRequest graphQlRequest = req.context()
                .get(GraphQlRequest.class)
                .orElseThrow(() -> new IllegalStateException("GraphQL request state is missing"));

        if (graphQlRequest.getRequest() && graphQlRequest.parsedQuery().map(ParsedQuery::mutation).orElse(false)) {
            res.header(HeaderNames.ALLOW, "POST")
                    .status(Status.METHOD_NOT_ALLOWED_405)
                    .send();
            return;
        }

        res.headers().contentType(MediaTypes.APPLICATION_JSON);
        ResolverInvocation resolverInvocation = new ResolverInvocation();
        Map<String, Object> contextValues = requestContext(req, graphQlRequest.parsedQuery(), resolverInvocation);
        Map<String, Object> result = graphQlRequest.operationName() == null
                ? invocationHandler.executeWithContext(graphQlRequest.query(), graphQlRequest.variables(), contextValues)
                : invocationHandler.executeWithContext(graphQlRequest.query(),
                                                       graphQlRequest.operationName(),
                                                       graphQlRequest.variables(),
                                                       contextValues);
        req.context().register(new GraphQlResponse(result));
        if (resolverInvocation.invoked()
                || graphQlRequest.parsedQuery().map(ParsedQuery::introspectionOnly).orElse(false)) {
            graphQlResponse(req, res);
        } else {
            noResolverAuthorization.handle(req, res);
        }
    }

    private void graphQlResponse(ServerRequest req, ServerResponse res) {
        Map<String, Object> result = req.context()
                .get(GraphQlResponse.class)
                .orElseThrow(() -> new IllegalStateException("GraphQL response state is missing"))
                .result();
        res.send(toJsonBytes(result));
    }

    private Map<String, Object> requestContext(ServerRequest req,
                                               Optional<ParsedQuery> parsedQuery,
                                               ResolverInvocation resolverInvocation) {
        if (parsedQuery.isEmpty()) {
            return Map.of(ExecutionContext.HELIDON_CONTEXT_KEY, req.context(),
                          RESOLVER_INVOCATION_KEY, resolverInvocation);
        }
        ParsedQuery parsed = parsedQuery.orElseThrow();
        if (parsed.document().isPresent()) {
            return Map.of(ExecutionContext.HELIDON_CONTEXT_KEY, req.context(),
                          GraphQlContextKeys.PARSED_DOCUMENT, parsed.document().orElseThrow(),
                          RESOLVER_INVOCATION_KEY, resolverInvocation);
        }
        return Map.of(ExecutionContext.HELIDON_CONTEXT_KEY, req.context(),
                      GraphQlContextKeys.PARSE_ERROR, parsed.parseError().orElseThrow(),
                      RESOLVER_INVOCATION_KEY, resolverInvocation);
    }

    private static Optional<ParsedQuery> parseQuery(String query, String operationName) {
        if (query == null) {
            return Optional.empty();
        }
        Document document;
        try {
            document = Parser.parse(ParserEnvironment.newParserEnvironment()
                                            .document(query)
                                            .parserOptions(ParserOptions.getDefaultOperationParserOptions())
                                            .build());
        } catch (InvalidSyntaxException e) {
            return Optional.of(new ParsedQuery(Optional.empty(),
                                               Optional.of(new PreparsedDocumentEntry(e.toInvalidSyntaxError())),
                                               false,
                                               false));
        }

        Optional<OperationDefinition> operationDefinition = operationDefinition(document, operationName);
        boolean mutation = operationDefinition
                .map(OperationDefinition::getOperation)
                .filter(OperationDefinition.Operation.MUTATION::equals)
                .isPresent();

        Map<String, FragmentDefinition> fragments = new LinkedHashMap<>();
        document.getDefinitionsOfType(FragmentDefinition.class)
                .forEach(fragment -> fragments.putIfAbsent(fragment.getName(), fragment));
        Set<String> visitedFragments = new HashSet<>();
        ArrayDeque<SelectionSet> selectionSets = new ArrayDeque<>();
        operationDefinition.map(OperationDefinition::getSelectionSet)
                .ifPresent(selectionSets::addLast);
        boolean introspectionField = false;
        boolean applicationField = false;
        while (!selectionSets.isEmpty() && !applicationField) {
            for (Selection<?> selection : selectionSets.removeFirst().getSelections()) {
                switch (selection) {
                case Field field -> {
                    if (field.getName().startsWith("__")) {
                        introspectionField = true;
                    } else {
                        applicationField = true;
                    }
                }
                case InlineFragment fragment -> selectionSets.addLast(fragment.getSelectionSet());
                case FragmentSpread spread -> {
                    if (visitedFragments.add(spread.getName())) {
                        Optional.ofNullable(fragments.get(spread.getName()))
                                .map(FragmentDefinition::getSelectionSet)
                                .ifPresent(selectionSets::addLast);
                    }
                }
                default -> {
                    // Other selections do not identify request-level introspection.
                }
                }
                if (applicationField) {
                    break;
                }
            }
        }
        boolean introspectionOnly = introspectionField && !applicationField;
        return Optional.of(new ParsedQuery(Optional.of(document), Optional.empty(), mutation, introspectionOnly));
    }

    private static Optional<OperationDefinition> operationDefinition(Document document, String operationName) {
        if (operationName != null) {
            return document.getOperationDefinition(operationName);
        }

        List<OperationDefinition> definitions = document.getDefinitionsOfType(OperationDefinition.class);
        if (definitions.size() == 1) {
            return Optional.of(definitions.getFirst());
        }
        return Optional.empty();
    }

    private static String normalizeOperationName(String operationName) {
        return operationName == null || operationName.isEmpty() ? null : operationName;
    }

    private static String stringValue(JsonObject object, String name) {
        return object.value(name)
                .filter(value -> !(value instanceof JsonNull))
                .map(JsonValue::asString)
                .map(JsonString::value)
                .orElse(null);
    }

    private Map<String, Object> toVariableMap(JsonValue variables) {
        if (variables == null || variables instanceof JsonNull) {
            return Map.of();
        }

        return switch (variables) {
        case JsonObject object -> toVariableMap(object);
        case JsonString string -> toVariableMap(string.value());
        default -> throw new IllegalArgumentException("GraphQL variables must be a JSON object.");
        };
    }

    private static Map<String, Object> toVariableMap(JsonObject variables) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : variables.keysAsStrings()) {
            result.put(key, toJavaValue(variables.value(key).orElseThrow()));
        }
        return result;
    }

    private Map<String, Object> toVariableMap(String jsonString) {
        if (jsonString == null || jsonString.trim().isBlank()) {
            return Map.of();
        }
        return toVariableMap(JsonParser.create(jsonString).readJsonObject());
    }

    private static Object toJavaValue(JsonValue value) {
        return switch (value) {
        case JsonArray array -> toJavaList(array);
        case JsonBoolean bool -> bool.value();
        case JsonNull _ -> null;
        case JsonNumber number -> toJavaNumber(number);
        case JsonObject object -> toVariableMap(object);
        case JsonString string -> string.value();
        default -> throw new IllegalArgumentException("Unsupported JSON value type: " + value.type());
        };
    }

    private static List<Object> toJavaList(JsonArray array) {
        List<Object> result = new ArrayList<>(array.size());
        array.values().forEach(value -> result.add(toJavaValue(value)));
        return result;
    }

    private static Object toJavaNumber(JsonNumber number) {
        BigDecimal value = number.bigDecimalValue();
        if (value.scale() == 0) {
            BigInteger integer = value.toBigIntegerExact();
            if (integer.bitLength() < Integer.SIZE) {
                return integer.intValue();
            }
            if (integer.bitLength() < Long.SIZE) {
                return integer.longValue();
            }
            return integer;
        }
        return value;
    }

    private byte[] toJsonBytes(Map<String, Object> result) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (JsonGenerator generator = JsonGenerator.create(outputStream)) {
            writeJsonObject(generator, result);
        }
        return outputStream.toByteArray();
    }

    private void writeJsonObject(JsonGenerator generator, Map<?, ?> map) {
        generator.writeObjectStart();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            generator.writeKey(String.valueOf(entry.getKey()));
            writeJsonValue(generator, entry.getValue());
        }
        generator.writeObjectEnd();
    }

    private void writeJsonArray(JsonGenerator generator, Iterable<?> iterable) {
        generator.writeArrayStart();
        for (Object value : iterable) {
            writeJsonValue(generator, value);
        }
        generator.writeArrayEnd();
    }

    private void writeJsonValue(JsonGenerator generator, Object value) {
        switch (value) {
        case null -> generator.writeNull();
        case JsonValue jsonValue -> generator.write(jsonValue);
        case String string -> generator.write(string);
        case Character character -> generator.write(character);
        case Boolean bool -> generator.write(bool);
        case BigDecimal bigDecimal -> generator.write(bigDecimal);
        case BigInteger bigInteger -> generator.write(bigInteger);
        case Byte number -> generator.write(number);
        case Short number -> generator.write(number);
        case Integer number -> generator.write(number);
        case Long number -> generator.write(number);
        case Float number -> generator.write(number);
        case Double number -> generator.write(number);
        case Number number -> generator.write(new BigDecimal(number.toString()));
        case Map<?, ?> map -> writeJsonObject(generator, map);
        case Iterable<?> iterable -> writeJsonArray(generator, iterable);
        default -> jsonBinding.serialize(generator, value);
        }
    }

    /**
     * Fluent API builder to create {@link GraphQlService}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, GraphQlService> {
        private String context = GraphQlConstants.GRAPHQL_WEB_CONTEXT;
        private String schemaUri = GraphQlConstants.GRAPHQL_SCHEMA_URI;
        private InvocationHandler handler;
        private boolean permitAll;
        private Function<Handler, Handler> postEntryPoint = Function.identity();
        private Function<Handler, Handler> getEntryPoint = Function.identity();
        private Function<Handler, Handler> schemaEntryPoint = Function.identity();
        private Function<Handler, Handler> introspectionAuthorization = Function.identity();
        private Function<Handler, Handler> noResolverAuthorization = Function.identity();
        private boolean parsePostRequest;

        private Builder() {
        }

        @Override
        public GraphQlService build() {
            if (handler == null) {
                throw new IllegalStateException("Invocation handler must be defined");
            }

            return new GraphQlService(this);
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
         *     <td>web-context</td>
         *     <td>{@value io.helidon.graphql.server.GraphQlConstants#GRAPHQL_WEB_CONTEXT}</td>
         *     <td>Context that serves the GraphQL endpoint.</td>
         * </tr>
         * <tr>
         *     <td>schema-uri</td>
         *     <td>{@value io.helidon.graphql.server.GraphQlConstants#GRAPHQL_SCHEMA_URI}</td>
         *     <td>URI that serves the schema (under web context)</td>
         * </tr>
         * <tr>
         *     <td>permit-all</td>
         *     <td>{@code false}</td>
         *     <td>Whether to permit access without authentication.</td>
         * </tr>
         * </table>
         *
         * @param config configuration to use
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("web-context").asString().ifPresent(this::webContext);
            config.get("schema-uri").asString().ifPresent(this::schemaUri);
            config.get("permit-all").asBoolean().ifPresent(this::permitAll);

            return this;
        }

        /**
         * InvocationHandler to execute GraphQl requests.
         *
         * @param handler handler to use
         * @return updated builder instance
         */
        public Builder invocationHandler(InvocationHandler handler) {
            this.handler = handler;
            return this;
        }

        /**
         * InvocationHandler to execute GraphQl requests.
         *
         * @param handler handler to use
         * @return updated builder instance
         */
        public Builder invocationHandler(Supplier<InvocationHandler> handler) {
            return invocationHandler(handler.get());
        }

        /**
         * Configure HTTP entry point wrapping for this GraphQL service.
         * <p>
         * This is primarily used by generated declarative GraphQL features so request-level HTTP interceptors, such as
         * WebServer security, can run before GraphQL request processing begins.
         * Framework-owned schema and introspection requests use implicit authorization when the endpoint declares explicit
         * authorization; application resolver entry points retain their declared authorization behavior.
         *
         * @param entryPoints HTTP entry points
         * @param descriptor descriptor of the declarative endpoint
         * @param typeQualifiers qualifiers of the declarative endpoint
         * @param typeAnnotations annotations of the declarative endpoint
         * @return updated builder instance
         */
        @Api.Preview
        public Builder httpEntryPoints(HttpEntryPoint.EntryPoints entryPoints,
                                       ServiceDescriptor<?> descriptor,
                                       Set<Qualifier> typeQualifiers,
                                       List<Annotation> typeAnnotations) {

            return configureHttpEntryPoints(entryPoints,
                                            descriptor,
                                            typeQualifiers,
                                            typeAnnotations,
                                            false);
        }

        /**
         * Configure HTTP entry point wrapping for a generated declarative GraphQL service.
         * <p>
         * Unlike {@link #httpEntryPoints(HttpEntryPoint.EntryPoints, ServiceDescriptor, Set, List)}, this method may
         * complete explicit request authorization after GraphQL execution when no resolver was invoked. Generated
         * declarative services guarantee that every application resolver records its invocation before application code
         * executes, so this cannot defer authorization until after an untracked resolver runs.
         *
         * @param entryPoints HTTP entry points
         * @param descriptor descriptor of the declarative endpoint
         * @param typeQualifiers qualifiers of the declarative endpoint
         * @param typeAnnotations annotations of the declarative endpoint
         * @return updated builder instance
         */
        @Api.Internal
        public Builder declarativeHttpEntryPoints(HttpEntryPoint.EntryPoints entryPoints,
                                                  ServiceDescriptor<?> descriptor,
                                                  Set<Qualifier> typeQualifiers,
                                                  List<Annotation> typeAnnotations) {

            return configureHttpEntryPoints(entryPoints,
                                            descriptor,
                                            typeQualifiers,
                                            typeAnnotations,
                                            true);
        }

        private Builder configureHttpEntryPoints(HttpEntryPoint.EntryPoints entryPoints,
                                                 ServiceDescriptor<?> descriptor,
                                                 Set<Qualifier> typeQualifiers,
                                                 List<Annotation> typeAnnotations,
                                                 boolean declarativeResolvers) {

            Objects.requireNonNull(entryPoints);
            Objects.requireNonNull(descriptor);
            Objects.requireNonNull(typeQualifiers);
            Objects.requireNonNull(typeAnnotations);

            Optional<Annotation> implicitAuthorization = Annotations.findFirst(AUTHORIZED_TYPE, typeAnnotations)
                    .filter(annotation -> annotation.booleanValue().orElse(true))
                    .filter(annotation -> annotation.booleanValue("explicit").orElse(false))
                    .map(annotation -> Annotation.builder()
                            .from(annotation)
                            .property("explicit", false)
                            .build());
            TypedElementInfo schemaMethod = implicitAuthorization
                    .map(annotation -> requestMethod("<graphql-schema>", annotation))
                    .orElse(SCHEMA_METHOD);
            this.postEntryPoint = handler -> entryPoints.handler(descriptor,
                                                                 typeQualifiers,
                                                                 typeAnnotations,
                                                                 POST_METHOD,
                                                                 handler);
            this.getEntryPoint = handler -> entryPoints.handler(descriptor,
                                                                typeQualifiers,
                                                                typeAnnotations,
                                                                GET_METHOD,
                                                                handler);
            this.schemaEntryPoint = handler -> entryPoints.handler(descriptor,
                                                                   typeQualifiers,
                                                                   typeAnnotations,
                                                                   schemaMethod,
                                                                   handler);
            implicitAuthorization.ifPresent(annotation -> {
                TypedElementInfo introspectionMethod = requestMethod("<graphql-introspection>", annotation);
                TypedElementInfo noResolverMethod = requestMethod("<graphql-no-resolver>", annotation);
                this.introspectionAuthorization = handler -> entryPoints.authorizationHandler(descriptor,
                                                                                                typeQualifiers,
                                                                                                typeAnnotations,
                                                                                                introspectionMethod,
                                                                                                handler);
                if (declarativeResolvers) {
                    this.noResolverAuthorization = handler -> entryPoints.authorizationHandler(descriptor,
                                                                                                 typeQualifiers,
                                                                                                 typeAnnotations,
                                                                                                 noResolverMethod,
                                                                                                 handler);
                }
            });
            this.parsePostRequest = true;
            return this;
        }

        /**
         * Set a new root context for REST API of graphQL.
         *
         * @param path context to use
         * @return updated builder instance
         */
        public Builder webContext(String path) {
            String normalized = path.startsWith("/") ? path : "/" + path;
            while (normalized.length() > 1 && normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            this.context = normalized;
            return this;
        }

        /**
         * Configure URI that will serve the GraphQL schema under the context root.
         *
         * @param uri URI of the schema
         * @return updated builder instance
         */
        public Builder schemaUri(String uri) {
            if (uri.startsWith("/")) {
                this.schemaUri = uri;
            } else {
                this.schemaUri = "/" + uri;
            }

            return this;
        }

        /**
         * Whether to permit access without authentication.
         * This applies to GET and POST requests to the GraphQL endpoint and to the schema endpoint.
         *
         * @param permitAll whether requests are permitted without authentication
         * @return updated builder instance
         */
        public Builder permitAll(boolean permitAll) {
            this.permitAll = permitAll;
            return this;
        }

        /**
         * Executor service to use for GraphQL processing.
         *
         * @param executor executor service
         * @return updated builder instance
         * @deprecated GraphQL request processing does not use a dedicated executor
         */
        @Deprecated(since = "27.0.0", forRemoval = true)
        public Builder executor(ExecutorService executor) {
            return this;
        }

        /**
         * Executor service to use for GraphQL processing.
         *
         * @param executor executor service
         * @return updated builder instance
         * @deprecated GraphQL request processing does not use a dedicated executor
         */
        @Deprecated(since = "27.0.0", forRemoval = true)
        public Builder executor(Supplier<? extends ExecutorService> executor) {
            return this;
        }
    }

    private record GraphQlRequest(String query,
                                  String operationName,
                                  Map<String, Object> variables,
                                  Optional<ParsedQuery> parsedQuery,
                                  boolean getRequest) {
    }

    private record GraphQlResponse(Map<String, Object> result) {
    }

    static final class ResolverInvocation {
        private final AtomicBoolean invoked = new AtomicBoolean();

        void markInvoked() {
            invoked.set(true);
        }

        boolean invoked() {
            return invoked.get();
        }
    }

    private record ParsedQuery(Optional<Document> document,
                               Optional<PreparsedDocumentEntry> parseError,
                               boolean mutation,
                               boolean introspectionOnly) {
    }
}
