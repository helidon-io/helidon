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
package io.helidon.examples.micrometer.se;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Collections;

import io.helidon.common.config.Config;
import io.helidon.common.http.Http;
import io.helidon.nima.http.media.jsonp.JsonpSupport;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

// we need to first call the methods, before validating metrics
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MainTest {

    private static final JsonBuilderFactory JSON_BF = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonObject TEST_JSON_OBJECT;
    private static WebServer webServer;
    private static Http1Client webClient;

    private static double expectedPersonalizedGets;
    private static double expectedAllGets;

    static {
        TEST_JSON_OBJECT = JSON_BF.createObjectBuilder()
                .add("greeting", "Hola")
                .build();
    }

    @BeforeAll
    public static void startTheServer() {
        webServer = Main.startServer();

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .addMediaSupport(JsonpSupport.create(Config.empty()))
                .build();
    }

    @AfterAll
    public static void stopServer() {
        webServer.stop();
    }

    private static JsonObject get() {
        return get("/greet");
    }

    private static JsonObject get(String path) {
        JsonObject jsonObject = webClient.get()
                .path(path)
                .request(JsonObject.class);
        expectedAllGets++;
        return jsonObject;
    }

    private static JsonObject personalizedGet(String name) {
        JsonObject result = get("/greet/" + name);
        expectedPersonalizedGets++;
        return result;
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

        Http1ClientResponse response = webClient.put()
                .path("/greet/greeting")
                .submit(TEST_JSON_OBJECT);

        assertThat(response.status(), is(Http.Status.NO_CONTENT_204));

        JsonObject jsonObject = personalizedGet("Joe");
        assertThat(jsonObject.getString("greeting"), is("Hola Joe!"));
    }

    @Test
    @Order(4)
    void testMicrometer() {
        Http1ClientResponse response = webClient.get()
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
}
