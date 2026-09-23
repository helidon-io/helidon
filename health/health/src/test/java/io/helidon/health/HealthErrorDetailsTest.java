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

package io.helidon.health;

import java.io.StringReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@Timeout(30)
class HealthErrorDetailsTest {
    private static final String SENSITIVE_MESSAGE = "jdbc:test://database.example:5432/private?password=health-secret";

    private static WebServer server;
    private static WebClient client;

    @BeforeAll
    static void setUp() {
        Routing routing = Routing.builder()
                .register(healthSupport("/failure", () -> {
                    throw new IllegalStateException(SENSITIVE_MESSAGE);
                }))
                .register(healthSupport("/null-message", () -> {
                    throw new IllegalStateException();
                }))
                .register(healthSupport("/application", () -> HealthCheckResponse.named("application")
                        .down()
                        .withData("message", "Retrying")
                        .withData("error", "temporary")
                        .withData("attempts", 2)
                        .build()))
                .build();
        server = WebServer.builder(routing).host("127.0.0.1").port(0).build();
        server.start().await(10, TimeUnit.SECONDS);
        client = WebClient.builder()
                .baseUri("http://127.0.0.1:" + server.port())
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.shutdown().await(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void aggregateErrorsOmitExceptionDetails() {
        for (String path : List.of("/failure", "/failure/ready")) {
            assertErrorResponse(path);
        }
    }

    @Test
    void exceptionsWithoutMessagesKeepTheHealthResponse() {
        for (String path : List.of("/null-message", "/null-message/ready")) {
            assertErrorResponse(path);
        }
    }

    @Test
    void applicationDetailsArePreserved() {
        for (String path : List.of("/application", "/application/ready")) {
            JsonObject json = getResponse(path, 503);
            assertThat(path, json.getString("status"), is("DOWN"));
            assertThat(path, json.getJsonArray("checks"), hasSize(1));
            JsonObject check = json.getJsonArray("checks").getJsonObject(0);
            assertThat(path, check.getString("name"), is("application"));
            assertThat(path, check.getString("status"), is("DOWN"));
            JsonObject data = check.getJsonObject("data");
            assertThat(path, data.keySet(), containsInAnyOrder("message", "error", "attempts"));
            assertThat(path, data.getString("message"), is("Retrying"));
            assertThat(path, data.getString("error"), is("temporary"));
            assertThat(path, data.getInt("attempts"), is(2));
        }
    }

    @Test
    void headReturnsOnlyStatus() {
        for (String path : List.of("/failure", "/failure/ready")) {
            WebClientResponse response = client.head().path(path).request().await(10, TimeUnit.SECONDS);
            try {
                assertThat(path, response.status().code(), is(500));
                assertThat(path, response.content().as(String.class).await(10, TimeUnit.SECONDS), is(""));
            } finally {
                response.close();
            }
        }
    }

    private static void assertErrorResponse(String path) {
        JsonObject json = getResponse(path, 500);
        assertThat(path, json.getJsonArray("checks"), hasSize(1));
        JsonObject check = json.getJsonArray("checks").getJsonObject(0);
        assertThat(path, check.keySet(), containsInAnyOrder("name", "status", "data"));
        assertThat(path, check.getString("name"), not(is("")));
        assertThat(path, check.getString("status"), is("DOWN"));
        JsonObject data = check.getJsonObject("data");
        assertThat(path, data.keySet(), containsInAnyOrder("message"));
        assertThat(path, data.getString("message"), is("Failed to compute health. Error logged"));
    }

    private static JsonObject getResponse(String path, int expectedStatus) {
        WebClientResponse response = client.get().path(path).request().await(10, TimeUnit.SECONDS);
        try {
            assertThat(path, response.status().code(), is(expectedStatus));
            String body = response.content().as(String.class).await(10, TimeUnit.SECONDS);
            assertThat(path, body, not(containsString(SENSITIVE_MESSAGE)));
            assertThat(path, body, not(containsString(IllegalStateException.class.getName())));
            try (JsonReader reader = Json.createReader(new StringReader(body))) {
                return reader.readObject();
            }
        } finally {
            response.close();
        }
    }

    private static HealthSupport healthSupport(String path, HealthCheck check) {
        return HealthSupport.builder().webContext(path).addReadiness(check).build();
    }
}
