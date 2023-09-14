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

package io.helidon.webclient.tests;

import java.net.URI;
import java.util.Collections;

import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests of basic requests.
 */
class RequestTest extends TestParent {

    private static final JsonBuilderFactory JSON_BUILDER = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonObject JSON_NEW_GREETING;
    private static final JsonObject JSON_OLD_GREETING;

    static {
        JSON_NEW_GREETING = JSON_BUILDER.createObjectBuilder()
                .add("greeting", "Hola")
                .build();
        JSON_OLD_GREETING = JSON_BUILDER.createObjectBuilder()
                .add("greeting", CONFIG.get("app.greeting").asString().orElse("Hello"))
                .build();
    }

    RequestTest(WebServer server) {
        super(server);
    }

    @Test
    public void testHelloWorld() {
        JsonObject jsonObject = client.get().requestEntity(JsonObject.class);
        assertThat(jsonObject.getString("message"), is("Hello World!"));
    }

    @Test
    public void testIncorrect() {
        try (Http1ClientResponse response = client.get("/incorrect").request()) {
            if (response.status() != Status.NOT_FOUND_404) {
                fail("This request should be 404!");
            }
        }
    }

    @Test
    public void testFollowRedirect() {
        JsonObject jsonObject = client.get()
                .path("/redirect")
                .requestEntity(JsonObject.class);
        assertThat(jsonObject.getString("message"), is("Hello World!"));

        try (Http1ClientResponse response = client.get()
                .path("/redirect")
                .followRedirects(false)
                .request()) {
            assertThat(response.status(), is(Status.MOVED_PERMANENTLY_301));
        }
    }

    @Test
    public void testFollowRedirectPath() {
        JsonObject jsonObject = client.get("/redirectPath").requestEntity(JsonObject.class);
        assertThat(jsonObject.getString("message"), is("Hello World!"));
    }

    @Test
    public void testFollowRedirectInfinite() {
        try {
            client.get("/redirect/infinite").request(JsonObject.class);
            fail("This should have failed!");
        } catch (Throwable ex) {
            assertThat(ex.getMessage(), startsWith("Maximum number of request redirections (5) reached."));
        }
    }

    @Test
    public void testPut() {
        try (Http1ClientResponse response = client.put("/greeting").submit(JSON_NEW_GREETING)) {
            assertThat(response.status().code(), is(204));
        }

        JsonObject jsonObject = client.get().requestEntity(JsonObject.class);
        assertThat(jsonObject.getString("message"), is("Hola World!"));

        try (Http1ClientResponse response = client.put("/greeting").submit(JSON_OLD_GREETING)) {
            assertThat(response.status().code(), is(204));
        }
    }

    @Test
    public void testEntityNotHandled() {
        try (HttpClientResponse response = client.get("/incorrect").request()) {
            assertThat(response.status(), is(Status.NOT_FOUND_404));
        }
    }

    @Test
    public void testResponseLastUri() {
        URI defaultTemplate = URI.create("http://localhost:" + server.port() + "/greet");
        URI redirectTemplate = URI.create("http://localhost:" + server.port() + "/greet/redirect");

        try (Http1ClientResponse response = client.get("/redirect").request()) {
            assertThat(response.lastEndpointUri().toUri(), is(defaultTemplate));
        }

        try (Http1ClientResponse response = client.get()
                .path("/redirect")
                .followRedirects(false)
                .request()) {

            assertThat(response.lastEndpointUri().toUri(), is(redirectTemplate));
        }

        try (Http1ClientResponse response = client.get().request()) {
            assertThat(response.lastEndpointUri().toUri(), is(defaultTemplate));
        }
    }

    @Test
    public void reuseRequestBuilder() {
        Http1ClientRequest request = client.get();
        JsonObject response = request.requestEntity(JsonObject.class);
        assertThat(response, notNullValue());
        assertThat(response.getString("message"), is("Hello World!"));
        response = request.requestEntity(JsonObject.class);
        assertThat(response, notNullValue());
        assertThat(response.getString("message"), is("Hello World!"));
    }

}
