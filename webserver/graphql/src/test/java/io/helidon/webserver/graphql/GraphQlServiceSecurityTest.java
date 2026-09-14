/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.concurrent.atomic.AtomicReference;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class GraphQlServiceSecurityTest {
    private static final AtomicReference<String> MARKER = new AtomicReference<>("unset");

    private final Http1Client client;

    GraphQlServiceSecurityTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.register(GraphQlService.create(buildSchema()));
    }

    @BeforeEach
    void resetMarker() {
        MARKER.set("unset");
    }

    @Test
    void anonymousGraphQlRequestsAreRejectedByDefault() {
        try (Http1ClientResponse response = client.post("/graphql")
                .submit("{\"query\":\"mutation{setMarker(value:\\\"post-owned\\\")}\"}")) {
            assertThat(response.status(), is(Status.UNAUTHORIZED_401));
        }

        assertThat(MARKER.get(), is("unset"));

        try (Http1ClientResponse response = client.get("/graphql")
                .queryParam("query", "mutation{setMarker(value:\"get-owned\")}")
                .request()) {
            assertThat(response.status(), is(Status.UNAUTHORIZED_401));
        }

        assertThat(MARKER.get(), is("unset"));

        try (Http1ClientResponse response = client.get("/graphql/schema.graphql").request()) {
            assertThat(response.status(), is(Status.UNAUTHORIZED_401));
        }
    }

    private static GraphQLSchema buildSchema() {
        String schema = "type Query{marker: String} type Mutation{setMarker(value: String!): String}";

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder.dataFetcher("marker", env -> MARKER.get()))
                .type("Mutation", builder -> builder.dataFetcher("setMarker", env -> {
                    String value = env.getArgument("value");
                    MARKER.set(value);
                    return MARKER.get();
                }))
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
    }
}
