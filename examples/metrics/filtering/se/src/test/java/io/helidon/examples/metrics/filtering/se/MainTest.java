/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.metrics.filtering.se;

import java.util.Collections;

import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@ServerTest
public class MainTest {

    private static final JsonBuilderFactory JSON_BUILDER = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonObject TEST_JSON_OBJECT = JSON_BUILDER.createObjectBuilder()
                                                                   .add("greeting", "Hola")
                                                                   .build();

    private final Http1Client client;

    public MainTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder server) {
        Main.setup(server);
    }

    @Test
    public void testHelloWorld() {
        try (Http1ClientResponse response = client.get("/greet").request()) {
            assertThat(response.as(JsonObject.class).getString("message"), CoreMatchers.is("Hello World!"));
        }

        try (Http1ClientResponse response = client.get("/greet/Joe").request()) {
            assertThat(response.as(JsonObject.class).getString("message"), CoreMatchers.is("Hello Joe!"));
        }

        try (Http1ClientResponse response = client.put("/greet/greeting").submit(TEST_JSON_OBJECT)) {
            assertThat(response.status().code(), CoreMatchers.is(204));
        }

        try (Http1ClientResponse response = client.get("/greet/Joe").request()) {
            assertThat(response.as(JsonObject.class).getString("message"), CoreMatchers.is("Hola Joe!"));
        }

        try (Http1ClientResponse response = client.get("/observe/metrics").request()) {
            assertThat(response.status().code(), CoreMatchers.is(200));
        }
    }

    @Test
    public void testMetrics() {
        try (Http1ClientResponse response = client.get("/greet").request()) {
            assertThat(response.as(String.class), containsString("Hello World!"));
        }

        try (Http1ClientResponse response = client.get("/greet/Joe").request()) {
            assertThat(response.as(String.class), containsString("Hello Joe!"));
        }

        try (Http1ClientResponse response = client.get("/observe/metrics/application").request()) {
            String openMetricsOutput = response.as(String.class);
            assertThat("Metrics output", openMetricsOutput, not(containsString(GreetService.TIMER_FOR_GETS)));
            assertThat("Metrics output", openMetricsOutput, containsString(GreetService.COUNTER_FOR_PERSONALIZED_GREETINGS));
        }
    }
}
