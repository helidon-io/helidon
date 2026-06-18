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

import io.helidon.common.context.Context;
import io.helidon.graphql.server.ExecutionContext;
import io.helidon.graphql.server.InvocationHandler;
import io.helidon.http.Status;
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
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class GraphQlServiceTest {

    private final Http1Client client;

    GraphQlServiceTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.register(GraphQlService.builder()
                                 .invocationHandler(InvocationHandler.create(buildSchema()))
                                 .permitAll(true)
                                 .build());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testHelloWorld() {
        try (Http1ClientResponse response = client.post("/graphql")
                .submit("{\"query\": \"{hello requestContextAvailable}\"}")) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat("POST errors: " + json.get("errors"), json, notNullValue());
            assertThat("POST", json.get("data").asJsonObject().getJsonString("hello").getString(), is("world"));
            assertThat("POST context",
                       json.get("data").asJsonObject().getBoolean("requestContextAvailable"),
                       is(true));
        }

        try (Http1ClientResponse response = client.get("/graphql")
                .queryParam("query", "{hello requestContextAvailable}")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat("GET errors: " + json.get("errors"), json, notNullValue());
            assertThat("GET", json.get("data").asJsonObject().getJsonString("hello").getString(), is("world"));
            assertThat("GET context",
                       json.get("data").asJsonObject().getBoolean("requestContextAvailable"),
                       is(true));
        }
    }

    private static GraphQLSchema buildSchema() {
        String schema = "type Query{hello: String requestContextAvailable: Boolean}";

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder.dataFetcher("hello", _ -> "world")
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
}
