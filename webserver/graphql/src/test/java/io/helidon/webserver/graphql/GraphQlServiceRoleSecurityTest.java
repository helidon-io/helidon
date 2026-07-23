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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.security.Security;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.providers.httpauth.SecureUserStore;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.security.SecurityFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

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
class GraphQlServiceRoleSecurityTest {
    private static final String GRAPHQL_ROLE = "graphql-user";
    private static final String USERNAME = "alice";
    private static final char[] PASSWORD = "password".toCharArray();
    private static final String OTHER_USERNAME = "bob";
    private static final char[] OTHER_PASSWORD = "other-password".toCharArray();
    private static final AtomicReference<String> MARKER = new AtomicReference<>("unset");
    private static final Map<String, TestUser> USERS = Map.of(
            USERNAME, new TestUser(USERNAME, PASSWORD, Set.of(GRAPHQL_ROLE)),
            OTHER_USERNAME, new TestUser(OTHER_USERNAME, OTHER_PASSWORD, Set.of("other-role")));

    private final Http1Client client;

    GraphQlServiceRoleSecurityTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        server.featuresDiscoverServices(false)
                .addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.builder()
                                    .security(buildSecurity())
                                    .build());
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.any("/graphql/*", SecurityFeature.rolesAllowed(GRAPHQL_ROLE))
                .register(GraphQlService.create(buildSchema()));
    }

    @BeforeEach
    void resetMarker() {
        MARKER.set("unset");
    }

    @Test
    void graphQlEndpointRequiresExpectedRole() {
        try (Http1ClientResponse response = callMutation(null)) {
            assertThat(response.status(), is(Status.UNAUTHORIZED_401));
        }
        assertThat(MARKER.get(), is("unset"));

        try (Http1ClientResponse response = callMutation(basicAuth(OTHER_USERNAME, OTHER_PASSWORD))) {
            assertThat(response.status(), is(Status.FORBIDDEN_403));
        }
        assertThat(MARKER.get(), is("unset"));

        try (Http1ClientResponse response = callMutation(basicAuth(USERNAME, PASSWORD))) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.objectValue("data")
                               .orElseThrow()
                               .stringValue("setMarker")
                               .orElseThrow(),
                       is("allowed"));
        }
        assertThat(MARKER.get(), is("allowed"));
    }

    @Test
    void graphQlSchemaRequiresExpectedRole() {
        try (Http1ClientResponse response = callSchema(null)) {
            assertThat(response.status(), is(Status.UNAUTHORIZED_401));
        }

        try (Http1ClientResponse response = callSchema(basicAuth(OTHER_USERNAME, OTHER_PASSWORD))) {
            assertThat(response.status(), is(Status.FORBIDDEN_403));
        }

        try (Http1ClientResponse response = callSchema(basicAuth(USERNAME, PASSWORD))) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    private Http1ClientResponse callMutation(String authorization) {
        var request = client.post("/graphql");
        if (authorization != null) {
            request.header(HeaderNames.AUTHORIZATION, authorization);
        }
        return request.submit("{\"query\":\"mutation{setMarker(value:\\\"allowed\\\")}\"}");
    }

    private Http1ClientResponse callSchema(String authorization) {
        var request = client.get("/graphql/schema.graphql");
        if (authorization != null) {
            request.header(HeaderNames.AUTHORIZATION, authorization);
        }
        return request.request();
    }

    private static Security buildSecurity() {
        return Security.builder()
                .addAuthenticationProvider(HttpBasicAuthProvider.builder()
                                                   .realm("helidon")
                                                   .userStore(login -> Optional.ofNullable(USERS.get(login))),
                                           "http-basic-auth")
                .build();
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

    private static String basicAuth(String username, char[] password) {
        String token = username + ":" + new String(password);
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private record TestUser(String login, char[] password, Set<String> roles) implements SecureUserStore.User {
        @Override
        public boolean isPasswordValid(char[] candidate) {
            return Arrays.equals(password(), candidate);
        }
    }
}
