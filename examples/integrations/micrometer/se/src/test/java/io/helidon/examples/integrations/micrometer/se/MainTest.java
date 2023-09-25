/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.integrations.micrometer.se;

import java.util.Collections;

import io.helidon.config.Config;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig.Builder;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

// we need to first call the methods, before validating metrics
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ServerTest
public class MainTest {

    private static final JsonBuilderFactory JSON_BF = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonObject TEST_JSON_OBJECT;

    private static double expectedPersonalizedGets;
    private static double expectedAllGets;
    private final Http1Client client;

    static {
        TEST_JSON_OBJECT = JSON_BF.createObjectBuilder()
                .add("greeting", "Hola")
                .build();
    }

    public MainTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    public static void setup(Builder builder) {
        builder.routing(Main::setupRouting);
    }

    @Test
    @Order(1)
    void testDefaultGreeting() {
        JsonObject jsonObject = get();
        assertThat(jsonObject.getString("greeting"), is("Hello World!"));
    }

    @Test
    @Order(2)
    void testNamedGreeting() {
        JsonObject jsonObject = personalizedGet("Joe");
        Assertions.assertEquals("Hello Joe!", jsonObject.getString("greeting"));
    }

    @Test
    @Order(3)
    void testUpdateGreeting() {
        try (Http1ClientResponse response = client.put()
                .path("/greet/greeting")
                .submit(TEST_JSON_OBJECT)) {

            assertThat(response.status(), is(Status.NO_CONTENT_204));
        }

        JsonObject jsonObject = personalizedGet("Joe");
        assertThat(jsonObject.getString("greeting"), is("Hola Joe!"));
    }

    @Test
    @Order(4)
    void testMicrometer() {
        Http1ClientResponse response = client.get()
                .path("/micrometer")
                .request();

        assertThat(response.status().code(), is(200));

        String output = response.as(String.class);
        String expected = Main.ALL_GETS_TIMER_NAME + "_seconds_count " + expectedAllGets;
        assertThat("Unable to find expected all-gets timer count " + expected + "; output is " + output,
                output, containsString(expected)); // all gets; the put
        // is not counted
        assertThat("Unable to find expected all-gets timer sum", output,
                containsString(Main.ALL_GETS_TIMER_NAME + "_seconds_sum"));
        expected = Main.PERSONALIZED_GETS_COUNTER_NAME + "_total " + expectedPersonalizedGets;
        assertThat("Unable to find expected counter result " + expected + "; output is " + output,
                output, containsString(expected));
        response.close();
    }

    private JsonObject get() {
        return get("/greet");
    }

    private JsonObject get(String path) {
        JsonObject jsonObject = client.get()
                .path(path)
                .requestEntity(JsonObject.class);
        expectedAllGets++;
        return jsonObject;
    }

    private JsonObject personalizedGet(String name) {
        JsonObject result = get("/greet/" + name);
        expectedPersonalizedGets++;
        return result;
    }
}
