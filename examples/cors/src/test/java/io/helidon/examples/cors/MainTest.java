/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Headers;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.reactive.media.jsonp.JsonpSupport;
import io.helidon.reactive.webclient.WebClient;
import io.helidon.reactive.webclient.WebClientRequestBuilder;
import io.helidon.reactive.webclient.WebClientRequestHeaders;
import io.helidon.reactive.webclient.WebClientResponse;
import io.helidon.reactive.webclient.WebClientResponseHeaders;
import io.helidon.reactive.webserver.WebServer;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.common.http.Http.Header.HOST;
import static io.helidon.common.http.Http.Header.ORIGIN;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MainTest {

    private static WebServer webServer;
    private static WebClient webClient;

    @BeforeAll
    public static void start() throws Exception {
        // the port is only available if the server started already!
        // so we need to wait
        webServer = Main.startServer().await();

        webClient = WebClient.builder()
                        .baseUri("http://localhost:" + webServer.port())
                        .addMediaSupport(JsonpSupport.create())
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
    public static void stop() {
        if (webServer != null) {
            webServer.shutdown()
                     .await(10, TimeUnit.SECONDS);
        }
    }

    @Order(1) // Make sure this runs before the greeting message changes so responses are deterministic.
    @Test
    public void testHelloWorld() {

        WebClientResponse r = getResponse("/greet");

        assertThat("HTTP response1", r.status().code(), is(200));
        assertThat("default message", fromPayload(r).getMessage(),
                is("Hello World!"));

        r = getResponse("/greet/Joe");
        assertThat("HTTP response2", r.status().code(), is(200));
        assertThat("hello Joe message", fromPayload(r).getMessage(),
                is("Hello Joe!"));

        r = putResponse("/greet/greeting", new GreetingMessage("Hola"));
        assertThat("HTTP response3", r.status().code(), is(204));

        r = getResponse("/greet/Jose");
        assertThat( "HTTP response4", r.status().code(), is(200));
        assertThat("hola Jose message", fromPayload(r).getMessage(),
                is("Hola Jose!"));

        r = getResponse("/health");
        assertThat("HTTP response2", r.status().code(), is(200));

        r = getResponse("/metrics");
        assertThat( "HTTP response2", r.status().code(), is(200));
    }

    @Order(10) // Run after the non-CORS tests (so the greeting is Hola) but before the CORS test that changes the greeting again.
    @Test
    void testAnonymousGreetWithCors() {
        WebClientRequestBuilder builder = webClient.get();
        WebClientRequestHeaders headers = builder.headers();
        headers.set(ORIGIN, "http://foo.com");
        headers.set(HOST, "here.com");

        WebClientResponse r = getResponse("/greet", builder);
        assertThat("HTTP response", r.status().code(), is(200));
        String payload = fromPayload(r).getMessage();
        assertThat("HTTP response payload was " + payload, payload, containsString("Hola World"));
        Headers responseHeaders = r.headers();
        Optional<String> allowOrigin = responseHeaders.value(ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat("Expected CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN + " is absent",
                allowOrigin.isPresent(), is(true));
        assertThat(allowOrigin.get(), is("*"));
    }

    @Order(11) // Run after the non-CORS tests but before other CORS tests.
    @Test
    void testGreetingChangeWithCors() {

        // Send the pre-flight request and check the response.

        WebClientRequestBuilder builder = webClient.options();
        WebClientRequestHeaders headers = builder.headers();
        headers.set(ORIGIN, "http://foo.com");
        headers.set(HOST, "here.com");
        headers.set(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse r = builder.path("/greet/greeting")
                .submit()
                .await();

        Headers responseHeaders = r.headers();
        List<String> allowMethods = responseHeaders.values(ACCESS_CONTROL_ALLOW_METHODS);
        assertThat("pre-flight response does not include " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_METHODS,
                allowMethods, not(empty()));
        assertThat(allowMethods, hasItem("PUT"));
        List<String> allowOrigins = responseHeaders.values(ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat("pre-flight response does not include " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN,
                allowOrigins, not(empty()));
        assertThat("Header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN
                        + " should contain '*' but does not; " + allowOrigins,
                allowOrigins, hasItem("http://foo.com"));

        // Send the follow-up request.

        builder = webClient.put();
        headers = builder.headers();
        headers.set(ORIGIN, "http://foo.com");
        headers.set(HOST, "here.com");
        headers.addAll(responseHeaders);

        r = putResponse("/greet/greeting", new GreetingMessage("Cheers"), builder);
        assertThat("HTTP response3", r.status().code(), is(204));
        responseHeaders = r.headers();
        allowOrigins = responseHeaders.values(ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat("Expected CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN + " has no value(s)",
                allowOrigins, not(empty()));
        assertThat("Header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN
                        + " should contain '*' but does not; " + allowOrigins,
                allowOrigins, hasItem("http://foo.com"));
    }

    @Order(12) // Run after CORS test changes greeting to Cheers.
    @Test
    void testNamedGreetWithCors() {
        WebClientRequestBuilder builder = webClient.get();
        WebClientRequestHeaders headers = builder.headers();
        headers.set(ORIGIN, "http://foo.com");
        headers.set(HOST, "here.com");

        WebClientResponse r = getResponse("/greet/Maria", builder);
        assertThat("HTTP response", r.status().code(), is(200));
        assertThat(fromPayload(r).getMessage(), containsString("Cheers Maria"));
        WebClientResponseHeaders responseHeaders = r.headers();
        Optional<String> allowOrigin = responseHeaders.value(ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat("Expected CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN + " is absent",
                allowOrigin.isPresent(), is(true));
        assertThat(allowOrigin.get(), is("*"));
    }

    @Order(100) // After all other tests so we can rely on deterministic greetings.
    @Test
    void testGreetingChangeWithCorsAndOtherOrigin() {
        WebClientRequestBuilder builder = webClient.put();
        WebClientRequestHeaders headers = builder.headers();
        headers.set(ORIGIN, "http://other.com");
        headers.set(HOST, "here.com");

        WebClientResponse r = putResponse("/greet/greeting", new GreetingMessage("Ahoy"), builder);
        // Result depends on whether we are using overrides or not.
        boolean isOverriding = Config.create().get("cors").exists();
        assertThat("HTTP response3", r.status().code(), is(isOverriding ? 204 : 403));
    }

    private static WebClientResponse getResponse(String path) {
        return getResponse(path, webClient.get());
    }

    private static WebClientResponse getResponse(String path, WebClientRequestBuilder builder) {
        return builder
                .accept(MediaTypes.APPLICATION_JSON)
                .path(path)
                .submit()
                .await();
    }

    private static WebClientResponse putResponse(String path, GreetingMessage payload) {
        return putResponse(path, payload, webClient.put());
    }

    private static WebClientResponse putResponse(String path, GreetingMessage payload, WebClientRequestBuilder builder) {
        return builder
                .accept(MediaTypes.APPLICATION_JSON)
                .path(path)
                .submit(payload.forRest())
                .await();
    }

    private static GreetingMessage fromPayload(WebClientResponse response) {
        JsonObject json = response
                .content()
                .as(JsonObject.class)
                .await();

        return GreetingMessage.fromRest(json);
    }
}
