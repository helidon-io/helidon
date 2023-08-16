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

import java.util.Base64;

import io.helidon.http.Http;
import io.helidon.config.Config;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.security.Security;
import io.helidon.webserver.security.SecurityFeature;
import io.helidon.tracing.Tracer;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static io.helidon.config.ConfigSources.classpath;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
public class FrontendTest {

    private static final JsonObject TODO = Json.createObjectBuilder().add("msg", "todo").build();
    private static final String ENCODED_ID = Base64.getEncoder().encodeToString("john:password".getBytes());

    private final Http1Client client;

    FrontendTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        Http1Client client = Http1Client.builder().baseUri("http://localhost:8081").build();
        BackendServiceClient bsc = new BackendServiceClient(client);
        Config config = Config.create(classpath("frontend-application.yaml"));
        Security security = Security.create(config.get("security"));

        server.routing(routing -> routing
                        .addFeature(ContextFeature.create())
                        .addFeature(SecurityFeature.create(security, config.get("security")))
                        .register("/env", new EnvHandler(config))
                        .register("/api", new TodosHandler(bsc, Tracer.noOp())))
                .putSocket("backend", socket -> socket
                        .port(8081)
                        .routing(routing -> routing
                                .register("/api/backend", new FakeBackendService())));
    }

    public static class FakeBackendService implements HttpService {

        @Override
        public void routing(HttpRules rules) {
            rules.get("/", (req, res) -> res.send(Json.createArrayBuilder().add(TODO).build()))
                    .post((req, res) -> res.send(req.content().as(JsonObject.class)))
                    .route(HttpRoute.builder()
                            .methods(Http.Method.GET, Http.Method.DELETE, Http.Method.PUT)
                            .path("/{id}")
                            .handler((req, res) -> res.send(TODO)));
        }
    }

    @Test
    public void testGetList() {
        try (Http1ClientResponse response = client.get("/api/todo")
                .header(Http.HeaderNames.AUTHORIZATION, "Basic " + ENCODED_ID)
                .request()) {

            assertThat(response.status().code(), is(200));
            assertThat(response.as(JsonArray.class).getJsonObject(0), is(TODO));
        }
    }

    @Test
    public void testPostTodo() {
        try (Http1ClientResponse response = client.post("/api/todo")
                .header(Http.HeaderNames.AUTHORIZATION, "Basic " + ENCODED_ID)
                .submit(TODO)) {

            assertThat(response.status().code(), is(200));
            assertThat(response.as(JsonObject.class), is(TODO));
        }
    }

    @Test
    public void testGetTodo() {
        try (Http1ClientResponse response = client.get("/api/todo/1")
                .header(Http.HeaderNames.AUTHORIZATION, "Basic " + ENCODED_ID)
                .request()) {

            assertThat(response.status().code(), is(200));
            assertThat(response.as(JsonObject.class), is(TODO));
        }
    }

    @Test
    public void testDeleteTodo() {
        try (Http1ClientResponse response = client.delete("/api/todo/1")
                .header(Http.HeaderNames.AUTHORIZATION, "Basic " + ENCODED_ID)
                .request()) {

            assertThat(response.status().code(), is(200));
            assertThat(response.as(JsonObject.class), is(TODO));
        }
    }

    @Test
    public void testUpdateTodo() {
        try (Http1ClientResponse response = client.put("/api/todo/1")
                .header(Http.HeaderNames.AUTHORIZATION, "Basic " + ENCODED_ID)
                .submit(TODO)) {

            assertThat(response.status().code(), is(200));
            assertThat(response.as(JsonObject.class), is(TODO));
        }
    }

    @Test
    public void testEnvHandler() {
        try (Http1ClientResponse response = client.get("/env").request()) {
            assertThat(response.status().code(), is(200));
            assertThat(response.as(String.class), is("docker"));
        }
    }
}
