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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.configurable.ServerThreadPoolSupplier;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.common.uri.UriQuery;
import io.helidon.config.Config;
import io.helidon.graphql.server.ExecutionContext;
import io.helidon.graphql.server.GraphQlConstants;
import io.helidon.graphql.server.InvocationHandler;
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
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpEntryPoint;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.SecureHandler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;

/**
 * Support for GraphQL for Helidon WebServer.
 */
public class GraphQlService implements HttpService {
    private static final TypedElementInfo POST_METHOD = requestMethod("graphQlPost");
    private static final TypedElementInfo GET_METHOD = requestMethod("graphQlGet");
    private static final TypedElementInfo SCHEMA_METHOD = requestMethod("graphQlSchema");

    private final String context;
    private final String schemaUri;
    private final InvocationHandler invocationHandler;
    private final ExecutorService executor;
    private final boolean permitAll;
    private final Handler graphQlPost;
    private final Handler graphQlGet;
    private final Handler graphQlSchema;

    private GraphQlService(Builder builder) {
        this.context = builder.context;
        this.schemaUri = builder.schemaUri;
        this.invocationHandler = builder.handler;
        this.executor = builder.executor.get();
        this.permitAll = builder.permitAll;
        this.graphQlPost = builder.postEntryPoint.apply(this::graphQlPost);
        this.graphQlGet = builder.getEntryPoint.apply(this::graphQlGet);
        this.graphQlSchema = builder.schemaEntryPoint.apply(this::graphQlSchema);
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
        rules.get(context + schemaUri, protect(graphQlSchema));
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

    private static TypedElementInfo requestMethod(String name) {
        return TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName(name)
                .typeName(TypeNames.PRIMITIVE_VOID)
                .build();
    }

    // handle POST request for GraphQL endpoint
    private void graphQlPost(ServerRequest req, ServerResponse res) {
        JsonObject entity = JsonParser.create(req.content().inputStream()).readJsonObject();
        processRequest(req,
                       res,
                       stringValue(entity, "query"),
                       stringValue(entity, "operationName"),
                       toVariableMap(entity.value("variables").orElse(null)));
    }

    // handle GET request for GraphQL endpoint
    private void graphQlGet(ServerRequest req, ServerResponse res) {
        UriQuery queryParams = req.query();
        String query = queryParams.first("query").orElseThrow(() -> new IllegalStateException("Query must be defined"));
        String operationName = queryParams.first("operationName").orElse(null);
        Map<String, Object> variables = queryParams.first("variables")
                .map(this::toVariableMap)
                .orElseGet(Map::of);

        if (mutationOperation(query, operationName)) {
            res.status(Status.METHOD_NOT_ALLOWED_405)
                    .send();
            return;
        }

        processRequest(req, res, query, operationName, variables);
    }

    // handle GET request to obtain GraphQL schema
    private void graphQlSchema(ServerRequest req, ServerResponse res) {
        res.send(invocationHandler.schemaString());
    }

    private void processRequest(ServerRequest req,
                                ServerResponse res,
                                String query,
                                String operationName,
                                Map<String, Object> variables) {

        res.headers().contentType(MediaTypes.APPLICATION_JSON);
        Map<String, Object> result = invocationHandler.execute(query, operationName, variables, requestContext(req));
        res.send(toJsonBytes(result));
    }

    private Map<String, Object> requestContext(ServerRequest req) {
        return Map.of(ExecutionContext.HELIDON_CONTEXT_KEY, req.context());
    }

    private static boolean mutationOperation(String query, String operationName) {
        Document document;
        try {
            document = Parser.parse(query);
        } catch (InvalidSyntaxException e) {
            return false;
        }

        return operationDefinition(document, operationName)
                .map(OperationDefinition::getOperation)
                .filter(OperationDefinition.Operation.MUTATION::equals)
                .isPresent();
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
        if (value.scale() <= 0) {
            try {
                return value.intValueExact();
            } catch (ArithmeticException e) {
                try {
                    return value.longValueExact();
                } catch (ArithmeticException ignored) {
                    return value.toBigIntegerExact();
                }
            }
        }
        return value;
    }

    private static byte[] toJsonBytes(Map<String, Object> result) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (JsonGenerator generator = JsonGenerator.create(outputStream)) {
            writeJsonObject(generator, result);
        }
        return outputStream.toByteArray();
    }

    private static void writeJsonObject(JsonGenerator generator, Map<?, ?> map) {
        generator.writeObjectStart();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            generator.writeKey(String.valueOf(entry.getKey()));
            writeJsonValue(generator, entry.getValue());
        }
        generator.writeObjectEnd();
    }

    private static void writeJsonArray(JsonGenerator generator, Iterable<?> iterable) {
        generator.writeArrayStart();
        for (Object value : iterable) {
            writeJsonValue(generator, value);
        }
        generator.writeArrayEnd();
    }

    private static void writeJsonValue(JsonGenerator generator, Object value) {
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
        default -> throw new IllegalArgumentException("Unsupported GraphQL result type: " + value.getClass().getName());
        }
    }

    /**
     * Fluent API builder to create {@link GraphQlService}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, GraphQlService> {
        private String context = GraphQlConstants.GRAPHQL_WEB_CONTEXT;
        private String schemaUri = GraphQlConstants.GRAPHQL_SCHEMA_URI;
        private Supplier<? extends ExecutorService> executor;
        private InvocationHandler handler;
        private boolean permitAll;
        private Function<Handler, Handler> postEntryPoint = Function.identity();
        private Function<Handler, Handler> getEntryPoint = Function.identity();
        private Function<Handler, Handler> schemaEntryPoint = Function.identity();

        private Builder() {
        }

        @Override
        public GraphQlService build() {
            if (handler == null) {
                throw new IllegalStateException("Invocation handler must be defined");
            }

            if (executor == null) {
                executor = ServerThreadPoolSupplier.builder()
                        .name("graphql")
                        .threadNamePrefix("graphql-")
                        .build();
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
         * <tr>
         *     <td>executor-service</td>
         *     <td>default server thread pool configuration</td>
         *     <td>see {@link io.helidon.common.configurable.ServerThreadPoolSupplier#builder()}</td>
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

            if (executor == null) {
                executor = ServerThreadPoolSupplier.builder()
                        .name("graphql")
                        .threadNamePrefix("graphql-")
                        .config(config.get("executor-service"))
                        .build();
            }

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
         *
         * @param entryPoints HTTP entry points
         * @param descriptor descriptor of the declarative endpoint
         * @param typeQualifiers qualifiers of the declarative endpoint
         * @param typeAnnotations annotations of the declarative endpoint
         * @return updated builder instance
         */
        public Builder httpEntryPoints(HttpEntryPoint.EntryPoints entryPoints,
                                       ServiceDescriptor<?> descriptor,
                                       Set<Qualifier> typeQualifiers,
                                       List<Annotation> typeAnnotations) {

            Objects.requireNonNull(entryPoints);
            Objects.requireNonNull(descriptor);
            Objects.requireNonNull(typeQualifiers);
            Objects.requireNonNull(typeAnnotations);

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
                                                                   SCHEMA_METHOD,
                                                                   handler);
            return this;
        }

        /**
         * Set a new root context for REST API of graphQL.
         *
         * @param path context to use
         * @return updated builder instance
         */
        public Builder webContext(String path) {
            if (path.startsWith("/")) {
                this.context = path;
            } else {
                this.context = "/" + path;
            }
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
         */
        public Builder executor(ExecutorService executor) {
            this.executor = () -> executor;
            return this;
        }

        /**
         * Executor service to use for GraphQL processing.
         *
         * @param executor executor service
         * @return updated builder instance
         */
        public Builder executor(Supplier<? extends ExecutorService> executor) {
            this.executor = executor;
            return this;
        }
    }
}
