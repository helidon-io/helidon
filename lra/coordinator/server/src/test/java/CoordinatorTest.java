/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

import java.net.URI;
import java.util.concurrent.TimeUnit;

import javax.json.JsonArray;
import javax.json.JsonValue;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Multi;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.lra.coordinator.CoordinatorService;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.SocketConfiguration;
import io.helidon.webserver.WebServer;

import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CoordinatorTest {

    private static final String CONTEXT_PATH = "/test";
    private static final String COORDINATOR_ROUTING_NAME = "coordinator";
    private static WebServer server;
    private static String serverUrl;
    private static WebClient webClient;
    private static CoordinatorService coordinatorService;

    @BeforeAll
    static void beforeAll() {
        LazyValue<URI> coordinatorUri = LazyValue.create(() ->
                URI.create("http://localhost:" + server.port(COORDINATOR_ROUTING_NAME) + "/lra-coordinator"));

        coordinatorService = CoordinatorService.builder()
                .url(coordinatorUri::get)
                .config(Config.builder(() -> ConfigSources.classpath("application.yaml").build())
                        .build().get(CoordinatorService.CONFIG_PREFIX))
                .build();
        server = WebServer.builder()
                .host("localhost")
                .addSocket(SocketConfiguration.builder()
                        .name(COORDINATOR_ROUTING_NAME)
                        .port(8077)
                        .build())
                .addNamedRouting(COORDINATOR_ROUTING_NAME, Routing.builder()
                        .register("/lra-coordinator", coordinatorService)
                        .build())
                .routing(Routing.builder()
                        .register(CONTEXT_PATH, rules -> rules.put((req, res) -> {
                            res.send();
                        }))
                        .build())
                .build();
        server.start().await();
        serverUrl = "http://localhost:" + server.port();
        webClient = WebClient.builder()
                .keepAlive(false)
                .baseUri("http://localhost:" + server.port(COORDINATOR_ROUTING_NAME) + "/lra-coordinator")
                .build();
    }

    @AfterAll
    static void afterAll() {
        server.shutdown();
        coordinatorService.shutdown();
    }

    @Test
    void startAndClose() {
        String lraId = start();

        Assertions.assertEquals(LRAStatus.Active, getParsedStatusOfLra(lraId));
        Assertions.assertEquals(LRAStatus.Active, status(lraId));

        close(lraId);

        Assertions.assertEquals(LRAStatus.Closed, getParsedStatusOfLra(lraId));
        Assertions.assertEquals(LRAStatus.Closed, status(lraId));
    }

    @Test
    void startAndCancel() {
        String lraId = start();

        Assertions.assertEquals(LRAStatus.Active, getParsedStatusOfLra(lraId));
        Assertions.assertEquals(LRAStatus.Active, status(lraId));

        close(lraId);

        Assertions.assertEquals(LRAStatus.Closed, getParsedStatusOfLra(lraId));
        Assertions.assertEquals(LRAStatus.Closed, status(lraId));
    }

    private String start() {
        return webClient
                .post()
                .path("start")
                .submit()
                .flatMapSingle(res -> res.content().as(String.class))
                .await(500, TimeUnit.MILLISECONDS);
    }

    private LRAStatus getParsedStatusOfLra(String lraId) {
        return WebClient.builder()
                .addMediaSupport(JsonpSupport.create())
                // Lra id is already whole url
                .baseUri(lraId)
                .build()
                .get()
                .request()
                .flatMapSingle(res -> res.content().as(JsonArray.class))
                .flatMap(Multi::create)
                .map(JsonValue::asJsonObject)
                .map(jo -> jo.getString("status"))
                .map(LRAStatus::valueOf)
                .first()
                .await(500, TimeUnit.MILLISECONDS);
    }

    private LRAStatus status(String lraId) {
        return WebClient.builder()
                .addMediaSupport(JsonpSupport.create())
                // Lra id is already whole url
                .baseUri(lraId + "/status")
                .build()
                .get()
                .request()
                .flatMapSingle(res -> res.content().as(String.class))
                .map(LRAStatus::valueOf)
                .await(500, TimeUnit.MILLISECONDS);
    }

    private void close(String lraId) {
        WebClient.builder()
                .addMediaSupport(JsonpSupport.create())
                // Lra id is already whole url
                .baseUri(lraId + "/close")
                .build()
                .put()
                .submit()
                .await(500, TimeUnit.MILLISECONDS);
    }

    private void cancel(String lraId) {
        WebClient.builder()
                .addMediaSupport(JsonpSupport.create())
                // Lra id is already whole url
                .baseUri(lraId + "/cancel")
                .build()
                .put()
                .submit()
                .await(500, TimeUnit.MILLISECONDS);
    }
}
