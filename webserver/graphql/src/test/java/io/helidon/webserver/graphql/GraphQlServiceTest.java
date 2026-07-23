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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.context.Context;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.graphql.server.ExecutionContext;
import io.helidon.graphql.server.GraphQlContextKeys;
import io.helidon.graphql.server.InvocationHandler;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonObject;
import io.helidon.json.binding.Json;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpEntryPoint;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import graphql.GraphQLContext;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.schema.Coercing;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Answers;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ServerTest
class GraphQlServiceTest {
    private static final Annotation EXPLICIT_AUTHORIZATION = Annotation.builder()
            .typeName(TypeName.create("io.helidon.security.annotations.Authorized"))
            .property("explicit", true)
            .build();
    private static final RecordingEntryPoints ENTRY_POINTS = new RecordingEntryPoints();
    private static final AtomicInteger MUTATIONS = new AtomicInteger();

    private final Http1Client client;

    GraphQlServiceTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.register(GraphQlService.builder()
                                 .httpEntryPoints(ENTRY_POINTS,
                                                  mock(ServiceDescriptor.class),
                                                  Set.of(),
                                                  List.of(EXPLICIT_AUTHORIZATION))
                                 .invocationHandler(InvocationHandler.create(buildSchema()))
                                 .permitAll(true)
                                 .build());
        builder.register(GraphQlService.builder()
                                 .webContext("/legacy")
                                 .invocationHandler(new LegacyInvocationHandler())
                                 .permitAll(true)
                                 .build());
    }

    @BeforeEach
    void resetEntryPoints() {
        ENTRY_POINTS.reset();
        MUTATIONS.set(0);
    }

    @Test
    void testHelloWorld() {
        try (Http1ClientResponse response = client.post("/graphql")
                .submit("{\"query\": \"{hello requestContextAvailable}\"}")) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            JsonObject data = json.objectValue("data").orElseThrow();
            assertThat("POST errors: " + json.value("errors"), json.value("errors").isEmpty(), is(true));
            assertThat("POST", data.stringValue("hello").orElseThrow(), is("world"));
            assertThat("POST context",
                       data.booleanValue("requestContextAvailable").orElseThrow(),
                       is(true));
        }

        try (Http1ClientResponse response = client.get("/graphql")
                .queryParam("query", "{hello requestContextAvailable}")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            JsonObject data = json.objectValue("data").orElseThrow();
            assertThat("GET errors: " + json.value("errors"), json.value("errors").isEmpty(), is(true));
            assertThat("GET", data.stringValue("hello").orElseThrow(), is("world"));
            assertThat("GET context",
                       data.booleanValue("requestContextAvailable").orElseThrow(),
                       is(true));
        }
    }

    @Test
    void customInvocationHandlerCanIgnoreContextValues() {
        try (Http1ClientResponse response = client.post("/legacy")
                .submit("{\"query\": \"{hello}\"}")) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.objectValue("data")
                               .orElseThrow()
                               .stringValue("hello")
                               .orElseThrow(),
                       is("legacy"));
        }

        try (Http1ClientResponse response = client.post("/legacy")
                .submit("{\"query\": \"query {\"}")) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(JsonObject.class)
                               .objectValue("data")
                               .orElseThrow()
                               .stringValue("hello")
                               .orElseThrow(),
                       is("legacy"));
        }
    }

    @Test
    void invalidSyntaxReturnsGraphQlError() {
        try (Http1ClientResponse response = client.post("/graphql")
                .submit("{\"query\": \"query {\"}")) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(JsonObject.class)
                               .arrayValue("errors")
                               .orElseThrow()
                               .toString(),
                       containsString("Invalid syntax"));
        }
    }

    @Test
    void entryPointsWrapGraphQlHttpRoutes() {
        try (Http1ClientResponse response = client.get("/graphql/schema.graphql")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), containsString("type Query"));
        }

        try (Http1ClientResponse response = client.post("/graphql")
                .submit("{\"query\": \"{hello}\"}")) {
            assertThat(response.status(), is(Status.OK_200));
        }

        try (Http1ClientResponse response = client.get("/graphql")
                .queryParam("query", "{hello}")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(ENTRY_POINTS.invocations(), is(3));
        assertThat(ENTRY_POINTS.methodNames(), is(List.of("<graphql-schema>", "<graphql-post>", "<graphql-get>")));
        assertThat(ENTRY_POINTS.authorizationMethodNames(), is(List.of()));
    }

    @ParameterizedTest
    @CsvSource({"/, /, /schema.graphql", "/api/, /api, /api/schema.graphql"})
    void schemaRouteUsesSinglePathSeparator(String context, String queryPath, String schemaPath) {
        HttpRules rules = mock(HttpRules.class, Answers.RETURNS_SELF);
        GraphQlService.builder()
                .webContext(context)
                .invocationHandler(new LegacyInvocationHandler())
                .build()
                .routing(rules);

        verify(rules).get(eq(schemaPath), any(Handler.class));
        verify(rules).get(eq(queryPath), any(Handler.class));
        verify(rules).post(eq(queryPath), any(Handler.class));
    }

    @Test
    void metadataRequestsUseStableEntryPoint() {
        int createdHandlers = ENTRY_POINTS.createdHandlers();

        try (Http1ClientResponse response = client.post("/graphql")
                .submit("{\"query\": \"{__typename}\"}")) {
            assertThat(response.status(), is(Status.OK_200));
        }

        try (Http1ClientResponse response = client.get("/graphql")
                .queryParam("query", """
                        query Normal { hello }
                        query Metadata { ...MetadataFields }
                        fragment MetadataFields on Query { __schema { queryType { name } } }
                        """)
                .queryParam("operationName", "Metadata")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        try (Http1ClientResponse response = client.post("/graphql")
                .submit("""
                                {
                                  "operationName": "Normal",
                                  "query": "query Normal { hello } query Metadata { __typename }"
                                }
                                """)) {
            assertThat(response.status(), is(Status.OK_200));
        }

        try (Http1ClientResponse response = client.post("/graphql")
                .submit("{\"query\": \"{__schema { missingField }}\"}")) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(JsonObject.class)
                               .arrayValue("errors")
                               .orElseThrow()
                               .toString(),
                       containsString("missingField"));
        }

        try (Http1ClientResponse response = client.post("/graphql")
                .submit("{\"query\": \"{metadata {__typename}}\"}")) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(ENTRY_POINTS.methodNames(),
                   is(List.of("<graphql-post>",
                              "<graphql-get>",
                              "<graphql-post>",
                              "<graphql-post>",
                              "<graphql-post>")));
        assertThat(ENTRY_POINTS.authorizationMethodNames(),
                   is(List.of("<graphql-introspection>",
                              "<graphql-introspection>",
                              "<graphql-introspection>")));
        assertThat(ENTRY_POINTS.createdHandlers(), is(createdHandlers));
    }

    @Test
    void testVariables() {
        String query = "query($input: NestedInput, $values: [Int], $decimal: Float, $long: ANY, $big: ANY) {"
                + " nested(input: $input) sum(values: $values) scaled(value: $decimal)"
                + " longValue: numberType(value: $long) bigValue: numberType(value: $big)"
                + " }";
        try (Http1ClientResponse response = client.post("/graphql")
                .submit("""
                                {
                                  "query": "%s",
                                  "variables": {
                                    "input": {
                                      "label": "post",
                                      "values": [1, 2],
                                      "missing": null
                                    },
                                    "values": [3, 4, 5],
                                    "decimal": 1.0,
                                    "long": 3000000000,
                                    "big": 9223372036854775808
                                  }
                                }
                                """.formatted(query))) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            JsonObject data = json.objectValue("data").orElseThrow();
            assertThat("POST errors: " + json.value("errors"),
                       data.stringValue("nested").orElseThrow(),
                       is("post:2:true"));
            assertThat("POST sum", data.intValue("sum").orElseThrow(), is(12));
            assertThat("POST decimal", data.doubleValue("scaled").orElseThrow(), is(1.0));
            assertThat("POST long", data.stringValue("longValue").orElseThrow(), is("Long:3000000000"));
            assertThat("POST big", data.stringValue("bigValue").orElseThrow(), is("BigInteger:9223372036854775808"));
        }

        try (Http1ClientResponse response = client.post("/graphql")
                .submit("""
                                {
                                  "query": "query($value: ANY) { numberType(value: $value) }",
                                  "variables": { "value": 1e100000 }
                                }
                                """)) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject data = response.as(JsonObject.class).objectValue("data").orElseThrow();
            assertThat(data.stringValue("numberType").orElseThrow(), is("BigDecimal:1E+100000"));
        }

        try (Http1ClientResponse response = client.get("/graphql")
                .queryParam("query", "query($message: String) { echo(message: $message) }")
                .queryParam("variables", "{\"message\":\"get\"}")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            JsonObject data = json.objectValue("data").orElseThrow();
            assertThat("GET errors: " + json.value("errors"), data.stringValue("echo").orElseThrow(), is("get"));
        }
    }

    @Test
    void getMutationIsRejected() {
        try (Http1ClientResponse response = client.get("/graphql")
                .queryParam("query", "mutation { update(enabled: true) }")
                .request()) {
            assertThat(response.status(), is(Status.METHOD_NOT_ALLOWED_405));
            assertThat(response.headers().first(HeaderNames.ALLOW).orElseThrow(), is("POST"));
        }

        try (Http1ClientResponse response = client.get("/graphql")
                .queryParam("query", "mutation { update(enabled: true) }")
                .queryParam("operationName", "")
                .queryParam("variables", "not-json")
                .request()) {
            assertThat(response.status(), is(Status.METHOD_NOT_ALLOWED_405));
            assertThat(response.headers().first(HeaderNames.ALLOW).orElseThrow(), is("POST"));
        }

        try (Http1ClientResponse response = client.get("/graphql")
                .queryParam("query", "mutation { __typename }")
                .request()) {
            assertThat(response.status(), is(Status.METHOD_NOT_ALLOWED_405));
            assertThat(response.headers().first(HeaderNames.ALLOW).orElseThrow(), is("POST"));
        }

        assertThat(MUTATIONS.get(), is(0));
        assertThat(ENTRY_POINTS.methodNames(), is(List.of("<graphql-get>", "<graphql-get>", "<graphql-get>")));
        assertThat(ENTRY_POINTS.authorizationMethodNames(), is(List.of()));
    }

    @Test
    void testListAndNullResponseValues() {
        try (Http1ClientResponse response = client.post("/graphql")
                .submit("{\"query\": \"{numbers nothing}\"}")) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            JsonObject data = json.objectValue("data").orElseThrow();
            JsonArray numbers = data.arrayValue("numbers").orElseThrow();
            assertThat("Response errors: " + json.value("errors"),
                       numbers.get(0).orElseThrow().asNumber().intValue(),
                       is(1));
            assertThat("Response null", data.value("nothing").orElseThrow() instanceof JsonNull, is(true));
        }
    }

    @Test
    void testErrorResponseValues() {
        try (Http1ClientResponse response = client.post("/graphql")
                .submit("{\"query\": \"{unknown}\"}")) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            JsonObject error = json.arrayValue("errors").orElseThrow()
                    .get(0)
                    .orElseThrow()
                    .asObject();

            assertThat(error.stringValue("message").orElseThrow(), containsString("Validation error"));
            assertThat(error.arrayValue("locations").orElseThrow()
                               .get(0)
                               .orElseThrow()
                               .asObject()
                               .intValue("line")
                               .orElseThrow(),
                       is(1));
            assertThat("Error data", json.value("data").orElseThrow() instanceof JsonNull, is(true));
        }
    }

    @Test
    void testErrorExtensionObjectResponseValues() {
        try (Http1ClientResponse response = client.post("/graphql")
                .submit("{\"query\": \"{extensionObject}\"}")) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            JsonObject data = json.objectValue("data").orElseThrow();
            JsonObject error = json.arrayValue("errors").orElseThrow()
                    .get(0)
                    .orElseThrow()
                    .asObject();
            JsonObject payload = error.objectValue("extensions")
                    .orElseThrow()
                    .objectValue("payload")
                    .orElseThrow();

            assertThat("Response data", data.stringValue("extensionObject").orElseThrow(), is("ok"));
            assertThat(error.stringValue("message").orElseThrow(), is("extension object"));
            assertThat(payload.stringValue("code").orElseThrow(), is("E-1"));
            assertThat(payload.intValue("retryAfterSeconds").orElseThrow(), is(30));
        }
    }

    private static GraphQLSchema buildSchema() {
        String schema = """
                type Query {
                    hello: String
                    requestContextAvailable: Boolean
                    echo(message: String): String
                    nested(input: NestedInput): String
                    numberType(value: ANY): String
                    numbers: [Int]
                    nothing: String
                    extensionObject: String
                    metadata: Metadata
                    scaled(value: Float): Float
                    sum(values: [Int]): Int
                }
                scalar ANY
                type Metadata {
                    name: String
                }
                type Mutation {
                    update(enabled: Boolean): Boolean
                }
                input NestedInput {
                    label: String
                    values: [Int]
                    missing: String
                }
                """;

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .scalar(anyScalar())
                .type("Query", builder -> builder.dataFetcher("hello", _ -> "world")
                        .dataFetcher("echo", environment -> environment.getArgument("message"))
                        .dataFetcher("nested", environment -> {
                            Map<String, Object> input = environment.getArgument("input");
                            List<?> values = (List<?>) input.get("values");
                            return input.get("label") + ":" + values.size() + ":" + input.containsKey("missing");
                        })
                        .dataFetcher("numberType", environment -> {
                            Object value = environment.getArgument("value");
                            return value.getClass().getSimpleName() + ":" + value;
                        })
                        .dataFetcher("numbers", _ -> List.of(1, 2, 3))
                        .dataFetcher("nothing", _ -> null)
                        .dataFetcher("extensionObject", _ -> DataFetcherResult.newResult()
                                .data("ok")
                                .error(GraphqlErrorBuilder.newError()
                                               .message("extension object")
                                               .extensions(Map.of("payload", new ExtensionPayload("E-1", 30)))
                                               .build())
                                .build())
                        .dataFetcher("metadata", _ -> Map.of("name", "metadata"))
                        .dataFetcher("scaled", environment -> environment.getArgument("value"))
                        .dataFetcher("sum", environment -> {
                            List<Integer> values = environment.getArgument("values");
                            return values.stream()
                                    .mapToInt(Integer::intValue)
                                    .sum();
                        })
                        .dataFetcher("requestContextAvailable", environment -> {
                            Context context = environment.getGraphQlContext().get(ExecutionContext.HELIDON_CONTEXT_KEY);
                            return context == environment.<ExecutionContext>getContext()
                                    .contextValue(ExecutionContext.HELIDON_CONTEXT_KEY, Context.class)
                                    .orElseThrow();
                        }))
                .type("Mutation", builder -> builder.dataFetcher("update", environment -> {
                    MUTATIONS.incrementAndGet();
                    return environment.getArgument("enabled");
                }))
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
    }

    private static GraphQLScalarType anyScalar() {
        return GraphQLScalarType.newScalar()
                .name("ANY")
                .coercing(new Coercing<Object, Object>() {
                    @Override
                    public Object serialize(Object dataFetcherResult, GraphQLContext graphQLContext, Locale locale) {
                        return dataFetcherResult;
                    }

                    @Override
                    public Object parseValue(Object input, GraphQLContext graphQLContext, Locale locale) {
                        return input;
                    }
                })
                .build();
    }

    @Json.Entity
    static class ExtensionPayload {
        private String code;
        private int retryAfterSeconds;

        ExtensionPayload() {
        }

        ExtensionPayload(String code, int retryAfterSeconds) {
            this.code = code;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }

        public void setRetryAfterSeconds(int retryAfterSeconds) {
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    private static final class RecordingEntryPoints implements HttpEntryPoint.EntryPoints {
        private final AtomicInteger createdHandlers = new AtomicInteger();
        private final AtomicInteger invocations = new AtomicInteger();
        private final List<String> methodNames = new CopyOnWriteArrayList<>();
        private final List<String> authorizationMethodNames = new CopyOnWriteArrayList<>();

        @Override
        public Handler handler(ServiceDescriptor<?> descriptor,
                               Set<Qualifier> typeQualifiers,
                               List<Annotation> typeAnnotations,
                               TypedElementInfo methodInfo,
                               Handler actualHandler) {
            createdHandlers.incrementAndGet();
            return (req, res) -> {
                invocations.incrementAndGet();
                methodNames.add(methodInfo.elementName());
                actualHandler.handle(req, res);
            };
        }

        @Override
        public Handler authorizationHandler(ServiceDescriptor<?> descriptor,
                                            Set<Qualifier> typeQualifiers,
                                            List<Annotation> typeAnnotations,
                                            TypedElementInfo methodInfo,
                                            Handler actualHandler) {
            return (req, res) -> {
                authorizationMethodNames.add(methodInfo.elementName());
                actualHandler.handle(req, res);
            };
        }

        private void reset() {
            invocations.set(0);
            methodNames.clear();
            authorizationMethodNames.clear();
        }

        private int invocations() {
            return invocations.get();
        }

        private int createdHandlers() {
            return createdHandlers.get();
        }

        private List<String> methodNames() {
            return List.copyOf(methodNames);
        }

        private List<String> authorizationMethodNames() {
            return List.copyOf(authorizationMethodNames);
        }
    }

    private static final class LegacyInvocationHandler implements InvocationHandler {
        @Override
        public Map<String, Object> executeWithContext(String query,
                                                      Map<String, Object> variables,
                                                      Map<String, Object> contextValues) {
            if (contextValues.containsKey(GraphQlContextKeys.PARSED_DOCUMENT)
                    || contextValues.containsKey(GraphQlContextKeys.PARSE_ERROR)) {
                return Map.of("data", Map.of("hello", "preparsed"));
            }
            return InvocationHandler.super.executeWithContext(query, variables, contextValues);
        }

        @Override
        public Map<String, Object> execute(String query, String operationName, Map<String, Object> variables) {
            return Map.of("data", Map.of("hello", "legacy"));
        }

        @Override
        public String schemaString() {
            return "type Query { hello: String }";
        }

        @Override
        public String defaultErrorMessage() {
            return "Server Error";
        }

        @Override
        public Set<String> blacklistedExceptions() {
            return Set.of();
        }

        @Override
        public Set<String> whitelistedExceptions() {
            return Set.of();
        }
    }
}
