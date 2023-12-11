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
package io.helidon.microprofile.examples.cors;

import java.util.List;
import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.media.jsonb.JsonbSupport;
import io.helidon.microprofile.server.Server;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestCORS {

    private static Http1Client client;
    private static Server server;

    @BeforeAll
    static void init() {
        Config config = Config.create();
        Config serverConfig = config.get("server");
        Server.Builder serverBuilder = Server.builder();
        serverConfig.ifExists(serverBuilder::config);
        server = serverBuilder
                .port(-1) // override the port for testing
                .build()
                .start();
        client = Http1Client.builder()
                .baseUri("http://localhost:" + server.port())
                .addMediaSupport(JsonbSupport.create(config))
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
    public void testHelloWorld() {

        Http1ClientResponse r = getResponse("/greet");

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
    void testAnonymousGreetWithCors() {
        Http1ClientRequest req = client.get()
                .header(HeaderNames.ORIGIN, "http://foo.com")
                .header(HeaderNames.HOST, "here.com");

        Http1ClientResponse r = getResponse("/greet", req);
        assertThat("HTTP response", r.status().code(), is(200));
        String payload = fromPayload(r);
        assertThat("HTTP response payload", payload, is("Hola World!"));
        Headers responseHeaders = r.headers();
        Optional<String> allowOrigin = responseHeaders.value(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat("Expected CORS header " + ACCESS_CONTROL_ALLOW_ORIGIN + " is present",
                allowOrigin.isPresent(), is(true));
        assertThat("CORS header " + ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigin.get(), is("*"));
    }

    @Order(11) // Run after the non-CORS tests but before other CORS tests.
    @Test
    void testGreetingChangeWithCors() {

        // Send the pre-flight request and check the response.

        Http1ClientRequest req = client.method(Method.OPTIONS)
                .header(HeaderNames.ORIGIN, "http://foo.com")
                .header(HeaderNames.HOST, "here.com")
                .header(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        List<String> allowOrigins;
        Headers responseHeaders;
        Headers preflightResponseHeaders;
        try (Http1ClientResponse res = req.path("/greet/greeting").request()) {
            assertThat("pre-flight status", res.status().code(), is(200));
            preflightResponseHeaders = res.headers();
        }

        List<String> allowMethods = preflightResponseHeaders.values(HeaderNames.ACCESS_CONTROL_ALLOW_METHODS);
        assertThat("pre-flight response check for " + ACCESS_CONTROL_ALLOW_METHODS, allowMethods, is(not(empty())));
        assertThat("Header " + ACCESS_CONTROL_ALLOW_METHODS, allowMethods, contains("PUT"));

        allowOrigins = preflightResponseHeaders.values(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN);

        assertThat("pre-flight response check for " + ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigins, is(not(empty())));
        assertThat("Header " + ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigins, contains("http://foo.com"));

        // Send the follow-up request.

        req = client.put()
                .header(HeaderNames.ORIGIN, "http://foo.com")
                .header(HeaderNames.HOST, "here.com");
        preflightResponseHeaders.forEach(req.headers()::add);

        try (Http1ClientResponse res = putResponse("/greet/greeting", "Cheers", req)) {
            assertThat("HTTP response3", res.status().code(), is(204));
            responseHeaders = res.headers();
        }

        allowOrigins = responseHeaders.values(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat("Expected CORS header " + ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigins, is(not(empty())));
        assertThat("Header " + ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigins, contains("http://foo.com"));
    }

    @Order(12) // Run after CORS test changes greeting to Cheers.
    @Test
    void testNamedGreetWithCors() {
        Http1ClientRequest req = client.get()
                .header(HeaderNames.ORIGIN, "http://foo.com")
                .header(HeaderNames.HOST, "here.com");

        Http1ClientResponse r = getResponse("/greet/Maria", req);
        assertThat("HTTP response", r.status().code(), is(200));
        assertThat(fromPayload(r), containsString("Cheers Maria"));
        Headers responseHeaders = r.headers();
        Optional<String> allowOrigin = responseHeaders.value(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat("Expected CORS header " + ACCESS_CONTROL_ALLOW_ORIGIN + " presence check", allowOrigin.isPresent(), is(true));
        assertThat(allowOrigin.get(), is("*"));
    }

    @Order(100) // After all other tests, so we can rely on deterministic greetings.
    @Test
    void testGreetingChangeWithCorsAndOtherOrigin() {
        Http1ClientRequest req = client.put()
                .header(HeaderNames.ORIGIN, "http://other.com")
                .header(HeaderNames.HOST, "here.com");

        try (Http1ClientResponse r = putResponse("/greet/greeting", "Ahoy", req)) {
            // Result depends on whether we are using overrides or not.
            boolean isOverriding = Config.create().get("cors").exists();
            assertThat("HTTP response3", r.status().code(), is(isOverriding ? 204 : 403));
        }
    }


    private static Http1ClientResponse getResponse(String path) {
        return getResponse(path, client.get());
    }

    private static Http1ClientResponse getResponse(String path, Http1ClientRequest builder) {
        return builder.accept(MediaTypes.APPLICATION_JSON)
                      .path(path)
                      .request();
    }

    private static String fromPayload(Http1ClientResponse response) {
        GreetingMessage message = response
                .entity()
                .as(GreetingMessage.class);
        return message.getMessage();
    }

    private static GreetingMessage toPayload(String message) {
        return new GreetingMessage(message);
    }

    private static Http1ClientResponse putResponse(String path, String message) {
        return putResponse(path, message, client.put());
    }

    private static Http1ClientResponse putResponse(String path, String message, Http1ClientRequest builder) {
        return builder.accept(MediaTypes.APPLICATION_JSON)
                      .path(path)
                      .submit(toPayload(message));
    }
}
