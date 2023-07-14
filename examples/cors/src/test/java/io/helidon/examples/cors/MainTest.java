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

import io.helidon.common.http.Headers;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.http.WritableHeaders;
import io.helidon.config.Config;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;

import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServerConfig;

import jakarta.json.JsonObject;
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

@SuppressWarnings("HttpUrlsUsage")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ServerTest
public class MainTest {

    private final Http1Client client;

    MainTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder server) {
        server.routing(routing -> Main.routing(routing, Config.create()));
    }

    @Order(1) // Make sure this runs before the greeting message changes so responses are deterministic.
    @Test
    public void testHelloWorld() {

        try (Http1ClientResponse response = client.get()
                .path("/greet")
                .accept(HttpMediaType.APPLICATION_JSON)
                .request()) {

            assertThat("HTTP response1", response.status().code(), is(200));

            String payload = GreetingMessage.fromRest(response.entity().as(JsonObject.class)).getMessage();
            assertThat("default message", payload, is("Hello World!"));
        }

        try (Http1ClientResponse response = client.get()
                .path("/greet/Joe")
                .accept(HttpMediaType.APPLICATION_JSON)
                .request()) {

            assertThat("HTTP response2", response.status().code(), is(200));

            String payload = GreetingMessage.fromRest(response.entity().as(JsonObject.class)).getMessage();
            assertThat("default message", payload, is("Hello Joe!"));
        }

        try (Http1ClientResponse response = client.put()
                .path("/greet/greeting")
                .accept(HttpMediaType.APPLICATION_JSON)
                .submit(new GreetingMessage("Hola").forRest())) {

            assertThat("HTTP response3", response.status().code(), is(204));
        }

        try (Http1ClientResponse response = client.get()
                .path("/greet/Jose")
                .accept(HttpMediaType.APPLICATION_JSON)
                .request()) {

            assertThat("HTTP response4", response.status().code(), is(200));

            String payload = GreetingMessage.fromRest(response.entity().as(JsonObject.class)).getMessage();
            assertThat("hola Jose message", payload, is("Hola Jose!"));
        }

        try (Http1ClientResponse response = client.get()
                .path("/observe/health")
                .request()) {

            assertThat("HTTP response5", response.status().code(), is(200));
        }

        try (Http1ClientResponse response = client.get()
                .path("/observe/metrics")
                .request()) {

            assertThat("HTTP response6", response.status().code(), is(200));
        }
    }

    @Order(10)
    // Run after the non-CORS tests (so the greeting is Hola) but before the CORS test that changes the greeting again.
    @Test
    void testAnonymousGreetWithCors() {
        Http1ClientRequest request = client.get()
                .path("/greet")
                .accept(HttpMediaType.APPLICATION_JSON);
        request.headers(headers -> {
            headers.set(ORIGIN, "http://foo.com");
            headers.set(HOST, "here.com");
            return headers;
        });
        try (Http1ClientResponse response = request.request()) {
            assertThat("HTTP response", response.status().code(), is(200));
            String payload = GreetingMessage.fromRest(response.entity().as(JsonObject.class))
                    .getMessage();
            assertThat("HTTP response payload was " + payload, payload, containsString("Hola World"));
            Headers responseHeaders = response.headers();
            Optional<String> allowOrigin = responseHeaders.value(ACCESS_CONTROL_ALLOW_ORIGIN);
            assertThat("Expected CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN + " is absent",
                    allowOrigin.isPresent(), is(true));
            assertThat(allowOrigin.get(), is("*"));
        }
    }

    @Order(11) // Run after the non-CORS tests but before other CORS tests.
    @Test
    void testGreetingChangeWithCors() {

        // Send the pre-flight request and check the response.

        Http1ClientRequest request = client.options()
                .path("/greet/greeting");
        request.headers(headers -> {
            headers.set(ORIGIN, "http://foo.com");
            headers.set(HOST, "here.com");
            headers.set(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
            return headers;
        });

        WritableHeaders<?> preFlightHeaders = WritableHeaders.create();
        try (Http1ClientResponse response = request.request()) {
            response.headers().forEach(preFlightHeaders::add);
            List<String> allowMethods = preFlightHeaders.values(ACCESS_CONTROL_ALLOW_METHODS);
            assertThat("pre-flight response does not include " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_METHODS,
                    allowMethods, not(empty()));
            assertThat(allowMethods, hasItem("PUT"));
            List<String> allowOrigins = preFlightHeaders.values(ACCESS_CONTROL_ALLOW_ORIGIN);
            assertThat("pre-flight response does not include " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN,
                    allowOrigins, not(empty()));
            assertThat("Header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN
                            + " should contain '*' but does not; " + allowOrigins,
                    allowOrigins, hasItem("http://foo.com"));
        }

        // Send the follow-up request.
        request = client.put("/greet/greeting")
                .accept(HttpMediaType.APPLICATION_JSON);

        request.headers(headers -> {
            headers.set(ORIGIN, "http://foo.com");
            headers.set(HOST, "here.com");
            preFlightHeaders.forEach(headers::add);
            return headers;
        });

        GreetingMessage payload = new GreetingMessage("Cheers");
        try (Http1ClientResponse response = request.submit(payload.forRest())) {

            assertThat("HTTP response3", response.status().code(), is(204));
            List<String> allowOrigins = preFlightHeaders.values(ACCESS_CONTROL_ALLOW_ORIGIN);
            assertThat("Expected CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN + " has no value(s)",
                    allowOrigins, not(empty()));
            assertThat("Header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN
                            + " should contain '*' but does not; " + allowOrigins,
                    allowOrigins, hasItem("http://foo.com"));
        }
    }

    @Order(12) // Run after CORS test changes greeting to Cheers.
    @Test
    void testNamedGreetWithCors() {
        Http1ClientRequest request = client.get()
                .path("/greet/Maria");
        request.headers(headers -> {
            headers.set(ORIGIN, "http://foo.com");
            headers.set(HOST, "here.com");
            return headers;
        });

        try (Http1ClientResponse response = request.request()) {
            assertThat("HTTP response", response.status().code(), is(200));
            String payload = GreetingMessage.fromRest(response.entity().as(JsonObject.class)).getMessage();
            assertThat(payload, containsString("Cheers Maria"));
            Headers responseHeaders = response.headers();
            Optional<String> allowOrigin = responseHeaders.value(ACCESS_CONTROL_ALLOW_ORIGIN);
            assertThat("Expected CORS header " + CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN + " is absent",
                    allowOrigin.isPresent(), is(true));
            assertThat(allowOrigin.get(), is("*"));
        }
    }

    @Order(100) // After all other tests, so we can rely on deterministic greetings.
    @Test
    void testGreetingChangeWithCorsAndOtherOrigin() {
        Http1ClientRequest request = client.put()
                .path("/greet/greeting");
        request.headers(headers -> {
            headers.set(ORIGIN, "http://other.com");
            headers.set(HOST, "here.com");
            return headers;
        });

        GreetingMessage payload = new GreetingMessage("Ahoy");
        try (Http1ClientResponse response = request.submit(payload.forRest())) {
            // Result depends on whether we are using overrides or not.
            boolean isOverriding = Config.create().get("cors").exists();
            assertThat("HTTP response3", response.status().code(), is(isOverriding ? 204 : 403));
        }
    }
}
