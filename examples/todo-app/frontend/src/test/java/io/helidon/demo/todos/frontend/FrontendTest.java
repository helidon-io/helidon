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

import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.security.Security;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.config.ConfigSources.classpath;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class FrontendTest {

    private static WebServer serverBackend;
    private static WebServer serverFrontend;
    private static WebClient client;
    private static final JsonObject TODO = Json.createObjectBuilder().add("msg", "todo").build();
    private static final String ENCODED_ID = Base64.getEncoder().encodeToString("john:password".getBytes());

    @Path("/api/backend")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public static class FakeBackendService {

        @GET
        @Produces(MediaType.APPLICATION_JSON)
        public Response getAllTodo() {
            JsonArray jsonArray = Json.createArrayBuilder().add(TODO).build();
            return Response.ok(jsonArray, MediaType.APPLICATION_JSON).build();
        }

        @POST
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        public Response createTodo(JsonObject object) {
            return Response.ok(object, MediaType.APPLICATION_JSON).build();
        }

        @GET
        @Path("/{id}")
        @Produces(MediaType.APPLICATION_JSON)
        public Response getTodo() {
            return Response.ok(TODO, MediaType.APPLICATION_JSON).build();
        }

        @DELETE
        @Path("/{id}")
        @Produces(MediaType.APPLICATION_JSON)
        public Response deleteTodo() {
            return Response.ok(TODO, MediaType.APPLICATION_JSON).build();
        }

        @PUT
        @Path("/{id}")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        public Response updateTodo(JsonObject object) {
            return Response.ok(object, MediaType.APPLICATION_JSON).build();
        }
    }

    @BeforeAll
    public static void init() {
        startBackendServer();
        startFrontendServer();
        client = WebClient.builder()
                .baseUri("http://localhost:" + serverFrontend.port())
                .addMediaSupport(JsonpSupport.create())
                .build();
    }

    @AfterAll
    public static void stopServers() {
        serverBackend.shutdown();
        serverFrontend.shutdown();
    }

    private static void startBackendServer() {
        serverBackend = WebServer.builder(createRouting())
                .port(0)
                .addMediaSupport(JsonpSupport.create())
                .build();

        serverBackend.start();

        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

    private static Routing createRouting() {
        return Routing.builder()
                .register("/", JerseySupport.builder()
                        .register(FakeBackendService.class)
                        .build())
                .build();
    }

    private static void startFrontendServer() {
        Properties prop = new Properties();
        prop.put("services.backend.endpoint", "http://127.0.0.1:" + serverBackend.port());
        Config config = Config.builder()
                .sources(List.of(
                        classpath("frontend-application.yaml"),
                        ConfigSources.create(prop)
                ))
                .build();
        Client client = ClientBuilder.newClient();
        BackendServiceClient bsc = new BackendServiceClient(client, config);

        serverFrontend = WebServer.builder(createRouting(
                Security.create(config.get("security")),
                config,
                bsc))
                .config(config.get("webserver"))
                .addMediaSupport(JsonpSupport.create())
                .build();

        serverFrontend.start();
    }

    private static Routing createRouting(Security security, Config config, BackendServiceClient bsc) {
        return Routing.builder()
                .register(WebSecurity.create(security, config.get("security")))
                .register("/env", new EnvHandler(config))
                .register("/api", new TodosHandler(bsc))
                .build();
    }

    @Test
    public void testGetList() throws ExecutionException, InterruptedException {
        client.get()
                .path("/api/todo")
                .headers(headers -> {
                    headers.add(Http.Header.AUTHORIZATION, "Basic " + ENCODED_ID);
                    return headers;
                })
                .request(JsonArray.class)
                .thenAccept(jsonValues -> {
                    assertThat(jsonValues.getJsonObject(0), is(TODO));
                })
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testPostTodo() throws ExecutionException, InterruptedException {
        client.post()
                .path("/api/todo")
                .headers(headers -> {
                    headers.add(Http.Header.AUTHORIZATION, "Basic " + ENCODED_ID);
                    return headers;
                })
                .submit(TODO, JsonObject.class)
                .thenAccept(jsonObject -> {
                    assertThat(jsonObject, is(TODO));
                })
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testGetTodo() throws ExecutionException, InterruptedException {
        client.get()
                .path("/api/todo/1")
                .headers(headers -> {
                    headers.add(Http.Header.AUTHORIZATION, "Basic " + ENCODED_ID);
                    return headers;
                })
                .request(JsonObject.class)
                .thenAccept(jsonObject -> {
                    assertThat(jsonObject, is(TODO));
                })
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testDeleteTodo() throws ExecutionException, InterruptedException {
        client.delete()
                .path("/api/todo/1")
                .headers(headers -> {
                    headers.add(Http.Header.AUTHORIZATION, "Basic " + ENCODED_ID);
                    return headers;
                })
                .request(JsonObject.class)
                .thenAccept(jsonObject -> {
                    assertThat(jsonObject, is(TODO));
                })
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testUpdateTodo() throws ExecutionException, InterruptedException {
        client.put()
                .path("/api/todo/1")
                .headers(headers -> {
                    headers.add(Http.Header.AUTHORIZATION, "Basic " + ENCODED_ID);
                    return headers;
                })
                .submit(TODO, JsonObject.class)
                .thenAccept(jsonObject -> {
                    assertThat(jsonObject, is(TODO));
                })
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testEnvHandler() throws ExecutionException, InterruptedException {
        client.get()
                .path("/env")
                .request(String.class)
                .thenAccept(s -> {
                    assertThat(s, is("docker"));
                })
                .toCompletableFuture()
                .get();
    }

}
