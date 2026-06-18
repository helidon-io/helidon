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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.context.Context;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.graphql.server.ExecutionContext;
import io.helidon.graphql.server.InvocationHandler;
import io.helidon.http.Status;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonObject;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpEntryPoint;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

@ServerTest
class GraphQlServiceTest {
    private static final RecordingEntryPoints ENTRY_POINTS = new RecordingEntryPoints();

    private final Http1Client client;

    GraphQlServiceTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.register(GraphQlService.builder()
                                 .httpEntryPoints(ENTRY_POINTS, mock(ServiceDescriptor.class), Set.of(), List.of())
                                 .invocationHandler(InvocationHandler.create(buildSchema()))
                                 .permitAll(true)
                                 .build());
    }

    @BeforeEach
    void resetEntryPoints() {
        ENTRY_POINTS.reset();
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
        assertThat(ENTRY_POINTS.methodNames(), is(List.of("graphQlSchema", "graphQlPost", "graphQlGet")));
    }

    @Test
    void testVariables() {
        String query = "query($input: NestedInput, $values: [Int], $decimal: Float) {"
                + " nested(input: $input) sum(values: $values) scaled(value: $decimal)"
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
                                    "decimal": 1.0
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

    private static GraphQLSchema buildSchema() {
        String schema = """
                type Query {
                    hello: String
                    requestContextAvailable: Boolean
                    echo(message: String): String
                    nested(input: NestedInput): String
                    numbers: [Int]
                    nothing: String
                    scaled(value: Float): Float
                    sum(values: [Int]): Int
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
                .type("Query", builder -> builder.dataFetcher("hello", _ -> "world")
                        .dataFetcher("echo", environment -> environment.getArgument("message"))
                        .dataFetcher("nested", environment -> {
                            Map<String, Object> input = environment.getArgument("input");
                            List<?> values = (List<?>) input.get("values");
                            return input.get("label") + ":" + values.size() + ":" + input.containsKey("missing");
                        })
                        .dataFetcher("numbers", _ -> List.of(1, 2, 3))
                        .dataFetcher("nothing", _ -> null)
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
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
    }

    private static final class RecordingEntryPoints implements HttpEntryPoint.EntryPoints {
        private final AtomicInteger invocations = new AtomicInteger();
        private final List<String> methodNames = new CopyOnWriteArrayList<>();

        @Override
        public Handler handler(ServiceDescriptor<?> descriptor,
                               Set<Qualifier> typeQualifiers,
                               List<Annotation> typeAnnotations,
                               TypedElementInfo methodInfo,
                               Handler actualHandler) {
            return (req, res) -> {
                invocations.incrementAndGet();
                methodNames.add(methodInfo.elementName());
                actualHandler.handle(req, res);
            };
        }

        private void reset() {
            invocations.set(0);
            methodNames.clear();
        }

        private int invocations() {
            return invocations.get();
        }

        private List<String> methodNames() {
            return List.copyOf(methodNames);
        }
    }
}
