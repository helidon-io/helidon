/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.examples.cors;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonPointer;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonString;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.jsonp.common.JsonProcessing;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;

import io.helidon.webserver.cors.CrossOriginConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MainTest {

    private static WebServer webServer;
    private static WebClient webClient;

    private static final JsonReaderFactory JSON_RF = Json.createReaderFactory(Collections.emptyMap());

    private static final JsonBuilderFactory JSON_BF = Json.createBuilderFactory(Collections.emptyMap());

    private static final JsonProcessing JSON_PROCESSING = JsonProcessing.create();

    @BeforeAll
    public static void start() throws Exception {
        webServer = Main.startServer();
        webClient = WebClient.builder()
                        .baseUri("http://localhost:" + webServer.port())
                        .mediaSupport(MediaSupport.builder()
                                        .registerDefaults()
                                        .registerReader(JSON_PROCESSING.newReader())
                                        .registerWriter(JSON_PROCESSING.newWriter())
                                        .build())
                        .build();

        long timeout = 2000; // 2 seconds should be enough to start the server
        long now = System.currentTimeMillis();

        while (!webServer.isRunning()) {
            Thread.sleep(100);
            if ((System.currentTimeMillis() - now) > timeout) {
                Assertions.fail("Failed to start webserver");
            }
        }
    }

    @AfterAll
    public static void stop() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                     .toCompletableFuture()
                     .get(10, TimeUnit.SECONDS);
        }
    }

    @Order(1)
    @Test
    public void testHelloWorld() throws Exception {
        
        WebClientResponse r = getResponse("/greet");

        assertEquals(200, r.status().code(), "HTTP response1");
        assertEquals("Hello World!", fromPayload(r).getMessage(),
                "default message");

        r = getResponse("/greet/Joe");
        assertEquals(200, r.status().code(), "HTTP response2");
        assertEquals("Hello Joe!", fromPayload(r).getMessage(),
                "hello Joe message");

        r = putResponse("/greet/greeting", new GreetingMessage("Hola"));
        assertEquals(204, r.status().code(), "HTTP response3");

        r = getResponse("/greet/Jose");
        assertEquals(200, r.status().code(), "HTTP response4");
        assertEquals("Hola Jose!", fromPayload(r).getMessage(),
                "hola Jose message");

        r = getResponse("/health");
        assertEquals(200, r.status().code(), "HTTP response2");

        r = getResponse("/metrics");
        assertEquals(200, r.status().code(), "HTTP response2");
    }

    @Test
    void testAnonymousGreetWithCors() throws Exception {
        WebClientRequestBuilder builder = webClient.get();
        Headers headers = builder.headers();
        headers.add("Origin", "http://foo.com");
        headers.add("Host", "bar.com");

        WebClientResponse r = getResponse("/greet", builder);
        assertEquals(200, r.status().code(), "HTTP response");
        assertTrue(fromPayload(r).getMessage().contains("World"));
        headers = r.headers();
        Optional<String> allowOrigin = headers.value(CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertTrue(allowOrigin.isPresent(),
                "Expected CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN + " is absent");
        assertEquals(allowOrigin.get(), "*");
    }

    @Test
    void testNamedGreetWithCors() throws Exception {
        WebClientRequestBuilder builder = webClient.get();
        Headers headers = builder.headers();
        headers.add("Origin", "http://foo.com");
        headers.add("Host", "bar.com");

        WebClientResponse r = getResponse("/greet/Maria", builder);
        assertEquals(200, r.status().code(), "HTTP response");
        assertTrue(fromPayload(r).getMessage().contains("Maria"));
        headers = r.headers();
        Optional<String> allowOrigin = headers.value(CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertTrue(allowOrigin.isPresent(),
                "Expected CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN + " is absent");
        assertEquals(allowOrigin.get(), "*");
    }

    @Test
    void testGreetingChangeWithCors() throws Exception {
        WebClientRequestBuilder builder = webClient.put();
        Headers headers = builder.headers();
        headers.add("Origin", "http://foo.com");
        headers.add("Host", "bar.com");

        WebClientResponse r = putResponse("/greet/greeting", new GreetingMessage("Hola"), builder);
        assertEquals(204, r.status().code(), "HTTP response3");
        headers = r.headers();
        List<String> allowOrigins = headers.values(CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertFalse(allowOrigins.isEmpty(),
                "Expected CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN + " has no value(s)");
        assertTrue(allowOrigins.contains("*"), "Header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN
                + " should contain '*' but does not; " + allowOrigins);
    }

    @Test
    void testForbiddenGreetingChangeWithCors() throws Exception {
        WebClientRequestBuilder builder = webClient.put();
        Headers headers = builder.headers();
        headers.add("Origin", "http://other.com");
        headers.add("Host", "bar.com");

        WebClientResponse r = putResponse("/greet/greeting", new GreetingMessage("Hola"), builder);
        assertEquals(403, r.status().code(), "HTTP response3");
    }

    private static WebClientResponse getResponse(String path) throws ExecutionException, InterruptedException {
        return getResponse(path, webClient.get());
//        return webClient.get()
//                .accept(MediaType.APPLICATION_JSON)
//                .path(path)
//                .submit()
//                .toCompletableFuture()
//                .get();
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

    private static WebClientResponse putResponse(String path, GreetingMessage payload) throws ExecutionException,
            InterruptedException {
        return putResponse(path, payload, webClient.put());
    }

    private static WebClientResponse putResponse(String path, GreetingMessage payload, WebClientRequestBuilder builder)
            throws ExecutionException, InterruptedException {
        return builder
                .accept(MediaType.APPLICATION_JSON)
                .path(path)
                .submit(payload.forRest())
                .toCompletableFuture()
                .get();
    }

    private static GreetingMessage fromPayload(WebClientResponse response) throws ExecutionException,
            InterruptedException {
        JsonObject json = response
                .content()
                .as(JsonObject.class)
                .toCompletableFuture()
                .get();
        return GreetingMessage.fromRest(json);
    }
}
