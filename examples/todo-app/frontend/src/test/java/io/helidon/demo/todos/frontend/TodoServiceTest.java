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

package io.helidon.demo.todos.frontend;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.security.Security;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.config.ConfigSources.classpath;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class TodoServiceTest {

    private static final JsonObject TODO = Json.createObjectBuilder().add("msg", "todo").build();
    private static final JsonArray TODOS = Json.createArrayBuilder().add(TODO).build();
    private static final String ENCODED_ID = Base64.getEncoder().encodeToString("john:password".getBytes());

    private static WebServer serverBackend;
    private static WebServer serverFrontend;
    private static WebClient client;

    private static class BackendServiceMock implements Service {

        @Override
        public void update(Routing.Rules rules) {
            rules.get("/", this::list)
                    .get("/{id}", this::get)
                    .delete("/{id}", this::get)
                    .post(this::echo)
                    .put("/{id}", this::echo);
        }

        void list(ServerRequest req, ServerResponse res) {
            res.send(TODOS);
        }

        void get(ServerRequest req, ServerResponse res) {
            res.send(TODO);
        }

        void echo(ServerRequest req, ServerResponse res) {
            req.content().as(JsonObject.class)
                    .onError(res::send)
                    .forSingle(res::send);
        }
    }

    @BeforeAll
    public static void init() {
        serverBackend = WebServer.builder()
                .addRouting(Routing.builder()
                                    .register("/api/backend", new BackendServiceMock()))
                .addMediaSupport(JsonpSupport.create())
                .build()
                .start()
                .await(Duration.ofMinutes(1));

        Config config = Config.builder()
                .sources(List.of(
                        classpath("application-test.yaml"),
                        ConfigSources.create(Map.of("services.backend.endpoint", "http://127.0.0.1:" + serverBackend.port()))))
                .build();

        Security security = Security.create(config.get("security"));

        serverFrontend = WebServer.builder()
                .addRouting(Routing.builder()
                                    .register(WebSecurity.create(security, config.get("security")))
                                    .register("/api", new TodoService(new BackendServiceClient(config))))
                .config(config.get("webserver"))
                .addMediaSupport(JsonpSupport.create())
                .build()
                .start()
                .await(Duration.ofMinutes(1));

        client = WebClient.builder()
                .baseUri("http://localhost:" + serverFrontend.port())
                .addMediaSupport(JsonpSupport.create())
                .useSystemServiceLoader(false)
                .addService(WebClientSecurity.create(security))
                .addHeader(Http.Header.AUTHORIZATION, "Basic " + ENCODED_ID)
                .build();
    }

    @AfterAll
    public static void stopServers() {
        if (serverBackend != null) {
            serverBackend.shutdown();
        }
        if (serverFrontend != null) {
            serverFrontend.shutdown();
        }
    }

    @Test
    public void testList() {
        WebClientResponse response = client.get()
                .path("/api/todo")
                .request()
                .await();
        assertThat(response.status(), is(Http.Status.OK_200));
        JsonArray jsonValues = response.content().as(JsonArray.class).await();
        assertThat(jsonValues.getJsonObject(0), is(TODO));
    }

    @Test
    public void testCreate() {
        WebClientResponse response = client.post()
                .path("/api/todo")
                .submit(TODO)
                .await();
        assertThat(response.status(), is(Http.Status.CREATED_201));
        JsonObject jsonObject = response.content().as(JsonObject.class).await();
        assertThat(jsonObject, is(TODO));
    }

    @Test
    public void testGet() {
        WebClientResponse response = client.get()
                .path("/api/todo/1")
                .request()
                .await();

        assertThat(response.status(), is(Http.Status.OK_200));
        JsonObject jsonObject = response.content().as(JsonObject.class).await();
        assertThat(jsonObject, is(TODO));
    }

    @Test
    public void testDelete() {
        WebClientResponse response = client.delete()
                .path("/api/todo/1")
                .request()
                .await();

        assertThat(response.status(), is(Http.Status.OK_200));
        JsonObject jsonObject = response.content().as(JsonObject.class).await();
        assertThat(jsonObject, is(TODO));
    }

    @Test
    public void testUpdate() {
        WebClientResponse response = client.put()
                .path("/api/todo/1")
                .submit(TODO)
                .await();

        assertThat(response.status(), is(Http.Status.OK_200));
        JsonObject jsonObject = response.content().as(JsonObject.class).await();
        assertThat(jsonObject, is(TODO));
    }
}
