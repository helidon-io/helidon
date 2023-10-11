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

package io.helidon.examples.todos.frontend;

import java.net.URI;
import java.util.Base64;

import io.helidon.config.Config;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.Socket;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.config.ConfigSources.classpath;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class TodoServiceTest {

    private static final JsonObject TODO = Json.createObjectBuilder().add("msg", "todo").build();
    private static final String ENCODED_ID = Base64.getEncoder().encodeToString("john:password".getBytes());
    private static final Header BASIC_AUTH = HeaderValues.create(HeaderNames.AUTHORIZATION, "Basic " + ENCODED_ID);

    private static URI backendUri;
    private final Http1Client client;

    TodoServiceTest(URI uri) {
        this.client = Http1Client.builder()
                .servicesDiscoverServices(false)
                .baseUri(uri.resolve("/api/todo"))
                .addMediaSupport(JsonpSupport.create())
                .addService(WebClientSecurity.create())
                .addHeader(BASIC_AUTH)
                .build();
    }

    static final class BackendServiceMock implements HttpService {

        @Override
        public void routing(HttpRules rules) {
            rules.get("/", (req, res) -> res.send(Json.createArrayBuilder().add(TODO).build()))
                    .post((req, res) -> res.send(req.content().as(JsonObject.class)))
                    .get("/{id}", (req, res) -> res.send(TODO))
                    .put("/{id}", (req, res) -> res.send(TODO))
                    .delete("/{id}", (req, res) -> res.send(TODO));
        }
    }

    @BeforeAll
    static void init(@Socket("backend") URI uri) {
        backendUri = uri;
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        Config config = Config.create(classpath("application-test.yaml"));

        server.config(config.get("server"))
                .routing(routing -> routing
                        .register("/api", new TodoService(new BackendServiceClient(() -> backendUri))))
                .putSocket("backend", socket -> socket
                        .routing(routing -> routing
                                .register("/api/backend", new BackendServiceMock())));
    }

    @Test
    void testList() {
        try (Http1ClientResponse response = client.get().request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(JsonArray.class).getJsonObject(0), is(TODO));
        }
    }

    @Test
    void testCreate() {
        try (Http1ClientResponse response = client.post().submit(TODO)) {
            assertThat(response.status(), is(Status.CREATED_201));
            assertThat(response.as(JsonObject.class), is(TODO));
        }
    }

    @Test
    void testGet() {
        try (Http1ClientResponse response = client.get("1").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(JsonObject.class), is(TODO));
        }
    }

    @Test
    void testDelete() {
        try (Http1ClientResponse response = client.delete("1").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(JsonObject.class), is(TODO));
        }
    }

    @Test
    void testUpdate() {
        try (Http1ClientResponse response = client.put("1").submit(TODO)) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(JsonObject.class), is(TODO));
        }
    }
}
