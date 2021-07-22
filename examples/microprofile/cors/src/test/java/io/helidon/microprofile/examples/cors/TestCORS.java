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
import io.helidon.media.jsonp.JsonpSupport;
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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestCORS {

    private static final String JSON_MESSAGE_RESPONSE_LABEL = "message";
    private static final String JSON_NEW_GREETING_LABEL = "greeting";

    private static final JsonBuilderFactory JSON_BF = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonpSupport JSONP_SUPPORT = JsonpSupport.create();

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
                    .addMediaSupport(JSONP_SUPPORT)
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

        assertThat("HTTP response1", r.status().code(), is(200));
        assertThat("default message", fromPayload(r), is("Hello World!"));

        r = getResponse("/greet/Joe");
        assertThat("HTTP response2", r.status().code(), is(200));
        assertThat("Hello Joe message", fromPayload(r), is("Hello Joe!"));

        r = putResponse("/greet/greeting", "Hola");
        assertThat("HTTP response3", r.status().code(), is(204));

        r = getResponse("/greet/Jose");
        assertThat("HTTP response4", r.status().code(), is(200));
        assertThat("Hola Jose message", fromPayload(r), is("Hola Jose!"));

        r = getResponse("/health");
        assertThat("HTTP response health", r.status().code(), is(200));

        r = getResponse("/metrics");
        assertThat("HTTP response metrics", r.status().code(), is(200));
    }

    @Order(10) // Run after the non-CORS tests (so the greeting is Hola) but before the CORS test that changes the greeting again.
    @Test
    void testAnonymousGreetWithCors() throws Exception {
        WebClientRequestBuilder builder = client.get();
        Headers headers = builder.headers();
        headers.add("Origin", "http://foo.com");
        headers.add("Host", "here.com");

        WebClientResponse r = getResponse("/greet", builder);
        assertThat("HTTP response", r.status().code(), is(200));
        String payload = fromPayload(r);
        assertThat("HTTP response payload", payload, is("Hola World!"));
        headers = r.headers();
        Optional<String> allowOrigin = headers.value(CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat("Expected CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN + " is present",
                allowOrigin.isPresent(), is(true));
        assertThat("CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigin.get(), is("*"));
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

        assertThat("pre-flight status", r.status().code(), is(200));
        Headers preflightResponseHeaders = r.headers();
        List<String> allowMethods = preflightResponseHeaders.values(CrossOriginConfig.ACCESS_CONTROL_ALLOW_METHODS);
        assertThat("pre-flight response check for " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_METHODS,
                allowMethods, is(not(empty())));
        assertThat("Header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_METHODS, allowMethods, contains("PUT"));
        List<String> allowOrigins = preflightResponseHeaders.values(CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat("pre-flight response check for " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN,
                allowOrigins, is(not(empty())));
        assertThat( "Header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigins, contains("http://foo.com"));

        // Send the follow-up request.

        builder = client.put();
        headers = builder.headers();
        headers.add("Origin", "http://foo.com");
        headers.add("Host", "here.com");
        headers.addAll(preflightResponseHeaders);

        r = putResponse("/greet/greeting", "Cheers", builder);
        assertThat("HTTP response3", r.status().code(), is(204));
        headers = r.headers();
        allowOrigins = headers.values(CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat("Expected CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN,
                allowOrigins, is(not(empty())));
        assertThat( "Header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigins, contains("http://foo.com"));
    }

    @Order(12) // Run after CORS test changes greeting to Cheers.
    @Test
    void testNamedGreetWithCors() throws Exception {
        WebClientRequestBuilder builder = client.get();
        Headers headers = builder.headers();
        headers.add("Origin", "http://foo.com");
        headers.add("Host", "here.com");

        WebClientResponse r = getResponse("/greet/Maria", builder);
        assertThat("HTTP response", r.status().code(), is(200));
        assertThat(fromPayload(r), containsString("Cheers Maria"));
        headers = r.headers();
        Optional<String> allowOrigin = headers.value(CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat("Expected CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN + " presence check",
                allowOrigin.isPresent(), is(true));
        assertThat(allowOrigin.get(), is("*"));
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
        assertThat("HTTP response3", r.status().code(), is(isOverriding ? 204 : 403));
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
