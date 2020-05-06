/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.microprofile.examples.cors;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import io.helidon.common.http.Headers;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.jsonp.common.JsonProcessing;
import io.helidon.microprofile.server.Server;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.cors.CrossOriginConfig;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestCORS {

    private static final String JSON_MESSAGE_RESPONSE_LABEL = "message";
    private static final String JSON_NEW_GREETING_LABEL = "greeting";

    private static final JsonBuilderFactory JSON_BF = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonProcessing JSON_PROCESSING = JsonProcessing.create();


    private static WebClient client;
    private static Server server;

    @BeforeAll
    static void init() {
        Config serverConfig = Config.create().get("server");
        Server.Builder serverBuilder = Server.builder();
        serverConfig.ifExists(serverBuilder::config);
        server = serverBuilder
                .port(-1) // override the port for testing
                .build()
            .start();
        client = WebClient.builder()
                    .baseUri("http://localhost:" + server.port())
                    .mediaSupport(MediaSupport.builder()
                        .registerDefaults()
                        .registerReader(JSON_PROCESSING.newReader())
                        .registerWriter(JSON_PROCESSING.newWriter())
                        .build())
                    .build();
    }

    @AfterAll
    static void cleanup() {
        if (server != null) {
            server.stop();
        }
    }

    @Order(1) // Make sure this runs before the greeting message changes so responses are deterministic.
    @Test
    public void testHelloWorld() throws Exception {

        WebClientResponse r = getResponse("/greet");

        assertEquals(200, r.status().code(), "HTTP response1");
        assertEquals("Hello World!", fromPayload(r),
                "default message");

        r = getResponse("/greet/Joe");
        assertEquals(200, r.status().code(), "HTTP response2");
        assertEquals("Hello Joe!", fromPayload(r),
                "hello Joe message");

        r = putResponse("/greet/greeting", "Hola");
        assertEquals(204, r.status().code(), "HTTP response3");

        r = getResponse("/greet/Jose");
        assertEquals(200, r.status().code(), "HTTP response4");
        assertEquals("Hola Jose!", fromPayload(r),
                "hola Jose message");

        r = getResponse("/health");
        assertEquals(200, r.status().code(), "HTTP response2");

        r = getResponse("/metrics");
        assertEquals(200, r.status().code(), "HTTP response2");
    }

    @Order(10) // Run after the non-CORS tests (so the greeting is Hola) but before the CORS test that changes the greeting again.
    @Test
    void testAnonymousGreetWithCors() throws Exception {
        WebClientRequestBuilder builder = client.get();
        Headers headers = builder.headers();
        headers.add("Origin", "http://foo.com");
        headers.add("Host", "here.com");

        WebClientResponse r = getResponse("/greet", builder);
        assertEquals(200, r.status().code(), "HTTP response");
        String payload = fromPayload(r);
        assertEquals("Hola World!", payload, "HTTP response payload");
        headers = r.headers();
        Optional<String> allowOrigin = headers.value(CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertTrue(allowOrigin.isPresent(),
                "Expected CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN + " is absent");
        assertEquals("*", allowOrigin.get(), 
                "CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Order(11) // Run after the non-CORS tests but before other CORS tests.
    @Test
    void testGreetingChangeWithCors() throws Exception {

        // Send the pre-flight request and check the response.

        WebClientRequestBuilder builder = client.method("OPTIONS");
        Headers headers = builder.headers();
        headers.add("Origin", "http://foo.com");
        headers.add("Host", "here.com");
        headers.add("Access-Control-Request-Method", "PUT");

        WebClientResponse r = builder.path("/greet/greeting")
                .submit()
                .toCompletableFuture()
                .get();

        assertEquals(200, r.status().code(), "pre-flight status");
        Headers preflightResponseHeaders = r.headers();
        List<String> allowMethods = preflightResponseHeaders.values(CrossOriginConfig.ACCESS_CONTROL_ALLOW_METHODS);
        assertFalse(allowMethods.isEmpty(),
                "pre-flight response check for " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_METHODS);
        assertTrue(allowMethods.contains("PUT"));
        List<String> allowOrigins = preflightResponseHeaders.values(CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertFalse(allowOrigins.isEmpty(),
                "pre-flight response check for " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertTrue(allowOrigins.contains("http://foo.com"), "Header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN
                + " should contain '*' but does not; " + allowOrigins);

        // Send the follow-up request.

        builder = client.put();
        headers = builder.headers();
        headers.add("Origin", "http://foo.com");
        headers.add("Host", "here.com");
        headers.addAll(preflightResponseHeaders);

        r = putResponse("/greet/greeting", "Cheers", builder);
        assertEquals(204, r.status().code(), "HTTP response3");
        headers = r.headers();
        allowOrigins = headers.values(CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertFalse(allowOrigins.isEmpty(),
                "Expected CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN + " has no value(s)");
        assertTrue(allowOrigins.contains("http://foo.com"), "Header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN
                + " should contain '*' but does not; " + allowOrigins);
    }

    @Order(12) // Run after CORS test changes greeting to Cheers.
    @Test
    void testNamedGreetWithCors() throws Exception {
        WebClientRequestBuilder builder = client.get();
        Headers headers = builder.headers();
        headers.add("Origin", "http://foo.com");
        headers.add("Host", "here.com");

        WebClientResponse r = getResponse("/greet/Maria", builder);
        assertEquals(200, r.status().code(), "HTTP response");
        assertTrue(fromPayload(r).contains("Cheers Maria"));
        headers = r.headers();
        Optional<String> allowOrigin = headers.value(CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertTrue(allowOrigin.isPresent(),
                "Expected CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN + " is absent");
        assertEquals("*", allowOrigin.get());
    }

    @Order(100) // After all other tests so we can rely on deterministic greetings.
    @Test
    void testGreetingChangeWithCorsAndOtherOrigin() throws Exception {
        WebClientRequestBuilder builder = client.put();
        Headers headers = builder.headers();
        headers.add("Origin", "http://other.com");
        headers.add("Host", "here.com");

        WebClientResponse r = putResponse("/greet/greeting", "Ahoy", builder);
        // Result depends on whether we are using overrides or not.
        boolean isOverriding = Config.create().get("cors").exists();
        assertEquals(isOverriding ? 204 : 403, r.status().code(), "HTTP response3");
    }


    private static WebClientResponse getResponse(String path) throws ExecutionException, InterruptedException {
        return getResponse(path, client.get());
    }

    private static WebClientResponse getResponse(String path, WebClientRequestBuilder builder) throws ExecutionException,
            InterruptedException {
        return builder
                .accept(MediaType.APPLICATION_JSON)
                .path(path)
                .submit()
                .toCompletableFuture()
                .get();
    }

    private static String fromPayload(WebClientResponse response) throws ExecutionException,
            InterruptedException {
        JsonObject json = response
                .content()
                .as(JsonObject.class)
                .toCompletableFuture()
                .get();
        return json.getString(JSON_MESSAGE_RESPONSE_LABEL);
    }

    private static JsonObject toPayload(String message) {
        JsonObjectBuilder builder = JSON_BF.createObjectBuilder();
        return builder.add(JSON_NEW_GREETING_LABEL, message)
                .build();
    }
    private static WebClientResponse putResponse(String path, String message) throws ExecutionException,
            InterruptedException {
        return putResponse(path, message, client.put());
    }

    private static WebClientResponse putResponse(String path, String message, WebClientRequestBuilder builder)
            throws ExecutionException, InterruptedException {
        return builder
                .accept(MediaType.APPLICATION_JSON)
                .path(path)
                .submit(toPayload(message))
                .toCompletableFuture()
                .get();
    }


}
