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

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.helidon.media.jsonb.JsonbSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class GraphQlSupportTest {

    @SuppressWarnings("unchecked")
    @Test
    void testHelloWorld() {
        WebServer server = WebServer.builder()
                .host("localhost")
                .routing(r -> r.register(GraphQlSupport.create(buildSchema())))
                .build()
                .start()
                .await(10, TimeUnit.SECONDS);

        try {
            WebClient webClient = WebClient.builder()
                    .addMediaSupport(JsonbSupport.create())
                    .build();

            LinkedHashMap<String, Object> response = webClient
                    .post()
                    .uri("http://localhost:" + server.port() + "/graphql")
                    .submit("{\"query\": \"{hello}\"}", LinkedHashMap.class)
                    .await(10, TimeUnit.SECONDS);

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            assertThat("POST errors: " + response.get("errors"), data, notNullValue());
            assertThat("POST", data.get("hello"), is("world"));

            response = webClient
                    .get()
                    .uri("http://localhost:" + server.port() + "/graphql")
                    .queryParam("query", "{hello}")
                    .request(LinkedHashMap.class)
                    .await(10, TimeUnit.SECONDS);

            data = (Map<String, Object>) response.get("data");
            assertThat("GET errors: " + response.get("errors"), data, notNullValue());
            assertThat("GET", data.get("hello"), is("world"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    void testUnexpectedExceptionSendsErrorResponse() throws Exception {
        CountDownLatch executeStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        WebServer server = WebServer.builder()
                .host("localhost")
                .routing(Routing.builder()
                                 .register(GraphQlSupport.builder()
                                                   .executor(executor)
                                                   .invocationHandler(new ThrowingInvocationHandler(executeStarted))
                                                   .build())
                                 .build())
                .build()
                .start()
                .await(10, TimeUnit.SECONDS);

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("http://localhost:" + server.port() + "/graphql").openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Connection", "close");

            byte[] requestBody = "{\"query\":\"{boom}\"}".getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(requestBody.length);

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBody);
            }

            assertThat("The custom InvocationHandler.execute() method was not reached",
                       executeStarted.await(5, TimeUnit.SECONDS),
                       is(true));
            assertThat(connection.getResponseCode(), is(500));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            server.shutdown().await(10, TimeUnit.SECONDS);
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static GraphQLSchema buildSchema() {
        String schema = "type Query{hello: String}";

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder.dataFetcher("hello", new StaticDataFetcher("world")))
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
    }

    private static final class ThrowingInvocationHandler implements InvocationHandler {
        private final CountDownLatch executeStarted;

        private ThrowingInvocationHandler(CountDownLatch executeStarted) {
            this.executeStarted = executeStarted;
        }

        @Override
        public Map<String, Object> execute(String query, String operationName, Map<String, Object> variables) {
            executeStarted.countDown();
            throw new IllegalStateException("test-trigger");
        }

        @Override
        public String schemaString() {
            return "type Query { boom: String }";
        }

        @Override
        public String defaultErrorMessage() {
            return "Internal Server Error";
        }

        @Override
        public Set<String> blacklistedExceptions() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> whitelistedExceptions() {
            return Collections.emptySet();
        }
    }
}
