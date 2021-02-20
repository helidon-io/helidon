/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.security.Security;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static io.helidon.config.ConfigSources.classpath;

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
                .port(8854)
                .addMediaSupport(JsonpSupport.create())
                .build();

        serverBackend.start();
    }

    private static Routing createRouting() {
        return Routing.builder()
                .register("/", JerseySupport.builder()
                        .register(FakeBackendService.class)
                        .build())
                .build();
    }

    private static void startFrontendServer() {
        Config config = Config.builder()
                .sources(List.of(
                        classpath("frontend-application.yaml")
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
                    Assertions.assertEquals(TODO, jsonValues.getJsonObject(0));
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
                    Assertions.assertEquals(TODO, jsonObject);
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
                    Assertions.assertEquals(TODO, jsonObject);
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
                    Assertions.assertEquals(TODO, jsonObject);
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
                    Assertions.assertEquals(TODO, jsonObject);
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
                    Assertions.assertEquals("docker", s);
                })
                .toCompletableFuture()
                .get();
    }

}
