/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.examples.micrometer.se;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;

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
    private static WebClient webClient;

    private static double expectedPersonalizedGets;
    private static double expectedAllGets;

    static {
        TEST_JSON_OBJECT = JSON_BF.createObjectBuilder()
                .add("greeting", "Hola")
                .build();
    }

    @BeforeAll
    public static void startTheServer() {
        webServer = Main.startServer()
                .await(10, TimeUnit.SECONDS);

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .addMediaSupport(JsonpSupport.create())
                .build();
    }

    @AfterAll
    public static void stopServer() {
        if (webServer != null) {
            webServer.shutdown()
                    .await(10, TimeUnit.SECONDS);
        }
    }

    private static JsonObject get() {
        return get("/greet");
    }

    private static JsonObject get(String path) {
        JsonObject jsonObject = webClient.get()
                .path(path)
                .request(JsonObject.class)
                .await();
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
        Assertions.assertEquals("Hello World!", jsonObject.getString("greeting"));
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

        WebClientResponse response = webClient.put()
                .path("/greet/greeting")
                .submit(TEST_JSON_OBJECT)
                .await();

        Assertions.assertEquals(Http.Status.NO_CONTENT_204, response.status());

        JsonObject jsonObject = personalizedGet("Joe");
        Assertions.assertEquals("Hola Joe!", jsonObject.getString("greeting"));
    }

    @Test
    @Order(4)
    void testMicrometer() {
        WebClientResponse response = webClient.get()
                .path("/micrometer")
                .request()
                .await();

        Assertions.assertEquals(200, response.status()
                .code());

        String output = response.content()
                .as(String.class)
                .await();
        String expected = Main.ALL_GETS_TIMER_NAME + "_seconds_count " + expectedAllGets;
        Assertions.assertTrue(output.contains(expected),
                "Unable to find expected all-gets timer count " + expected + "; output is " + output); // all gets; the put
        // is not counted
        Assertions.assertTrue(output.contains(Main.ALL_GETS_TIMER_NAME + "_seconds_sum"),
                "Unable to find expected all-gets timer sum");
        expected = Main.PERSONALIZED_GETS_COUNTER_NAME + "_total " + expectedPersonalizedGets;
        Assertions.assertTrue(output.contains(expected),
                "Unable to find expected counter result " + expected + "; output is " + output);
        response.close();
    }
}
