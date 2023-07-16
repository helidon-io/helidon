/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.HttpMediaType;
import io.helidon.config.Config;
import io.helidon.microprofile.server.Server;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.http.media.jsonp.JsonpSupport;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
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
@Disabled("3.0.0-JAKARTA") // OpenAPI: Caused by: java.lang.NoSuchMethodError:
// 'java.util.List io.smallrye.jandex.ClassInfo.unsortedFields()'
public class TestCORS {

    private static final String JSON_MESSAGE_RESPONSE_LABEL = "message";
    private static final String JSON_NEW_GREETING_LABEL = "greeting";

    private static final JsonBuilderFactory JSON_BF = Json.createBuilderFactory(Collections.emptyMap());

    private static Http1Client client;
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
        client = Http1Client.builder()
                .baseUri("http://localhost:" + server.port())
                .addMediaSupport(JsonpSupport.create())
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
                .header(Header.ORIGIN, "http://foo.com")
                .header(Header.HOST, "here.com");

        Http1ClientResponse r = getResponse("/greet", req);
        assertThat("HTTP response", r.status().code(), is(200));
        String payload = fromPayload(r);
        assertThat("HTTP response payload", payload, is("Hola World!"));
        Headers responseHeaders = r.headers();
        Optional<String> allowOrigin = responseHeaders.value(Header.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat("Expected CORS header " + ACCESS_CONTROL_ALLOW_ORIGIN + " is present",
                allowOrigin.isPresent(), is(true));
        assertThat("CORS header " + ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigin.get(), is("*"));
    }

    @Order(11) // Run after the non-CORS tests but before other CORS tests.
    @Test
    void testGreetingChangeWithCors() {

        // Send the pre-flight request and check the response.

        Http1ClientRequest req = client.method(Http.Method.OPTIONS)
                .header(Header.ORIGIN, "http://foo.com")
                .header(Header.HOST, "here.com")
                .header(Header.ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        List<String> allowOrigins;
        Headers responseHeaders;
        Headers preflightResponseHeaders;
        try (Http1ClientResponse res = req.path("/greet/greeting").request()) {
            assertThat("pre-flight status", res.status().code(), is(200));
            preflightResponseHeaders = res.headers();
        }

        List<String> allowMethods = preflightResponseHeaders.values(Header.ACCESS_CONTROL_ALLOW_METHODS);
        assertThat("pre-flight response check for " + ACCESS_CONTROL_ALLOW_METHODS, allowMethods, is(not(empty())));
        assertThat("Header " + ACCESS_CONTROL_ALLOW_METHODS, allowMethods, contains("PUT"));

        allowOrigins = preflightResponseHeaders.values(Header.ACCESS_CONTROL_ALLOW_ORIGIN);

        assertThat("pre-flight response check for " + ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigins, is(not(empty())));
        assertThat("Header " + ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigins, contains("http://foo.com"));

        // Send the follow-up request.

        req = client.put()
                .header(Header.ORIGIN, "http://foo.com")
                .header(Header.HOST, "here.com");
        preflightResponseHeaders.forEach(req.headers()::add);

        try (Http1ClientResponse res = putResponse("/greet/greeting", "Cheers", req)) {
            assertThat("HTTP response3", res.status().code(), is(204));
            responseHeaders = res.headers();
        }

        allowOrigins = responseHeaders.values(Header.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat("Expected CORS header " + ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigins, is(not(empty())));
        assertThat("Header " + ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigins, contains("http://foo.com"));
    }

    @Order(12) // Run after CORS test changes greeting to Cheers.
    @Test
    void testNamedGreetWithCors() {
        Http1ClientRequest req = client.get()
                .header(Header.ORIGIN, "http://foo.com")
                .header(Header.HOST, "here.com");

        Http1ClientResponse r = getResponse("/greet/Maria", req);
        assertThat("HTTP response", r.status().code(), is(200));
        assertThat(fromPayload(r), containsString("Cheers Maria"));
        Headers responseHeaders = r.headers();
        Optional<String> allowOrigin = responseHeaders.value(Header.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat("Expected CORS header " + ACCESS_CONTROL_ALLOW_ORIGIN + " presence check", allowOrigin.isPresent(), is(true));
        assertThat(allowOrigin.get(), is("*"));
    }

    @Order(100) // After all other tests, so we can rely on deterministic greetings.
    @Test
    void testGreetingChangeWithCorsAndOtherOrigin() {
        Http1ClientRequest req = client.put()
                .header(Header.ORIGIN, "http://foo.com")
                .header(Header.HOST, "here.com");

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
        return builder.accept(HttpMediaType.APPLICATION_JSON)
                      .path(path)
                      .request();
    }

    private static String fromPayload(Http1ClientResponse response) {
        JsonObject json = response.entity().as(JsonObject.class);
        return json.getString(JSON_MESSAGE_RESPONSE_LABEL);
    }

    private static JsonObject toPayload(String message) {
        JsonObjectBuilder builder = JSON_BF.createObjectBuilder();
        return builder.add(JSON_NEW_GREETING_LABEL, message)
                      .build();
    }

    private static Http1ClientResponse putResponse(String path, String message) {
        return putResponse(path, message, client.put());
    }

    private static Http1ClientResponse putResponse(String path, String message, Http1ClientRequest builder) {
        return builder.accept(HttpMediaType.APPLICATION_JSON)
                      .path(path)
                      .submit(toPayload(message));
    }
}
