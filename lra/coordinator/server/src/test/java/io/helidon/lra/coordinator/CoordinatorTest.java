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
package io.helidon.lra.coordinator;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Multi;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.SocketConfiguration;
import io.helidon.webserver.WebServer;

import jakarta.json.JsonArray;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CoordinatorTest {

    private static final String CONTEXT_PATH = "/test";
    private static final String COORDINATOR_ROUTING_NAME = "coordinator";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
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
                .config(Config.builder(
                                () -> ConfigSources.create(Map.of(
                                        "helidon.lra.coordinator.db.connection.url", "jdbc:h2:file:./target/lra-coordinator"
                                )).build(),
                                () -> ConfigSources.classpath("application.yaml").build()
                        )
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
                .routing(r -> r.register(CONTEXT_PATH, rules -> rules.put((req, res) -> res.send())))
                .build();
        server.start().await(TIMEOUT);
        serverUrl = "http://localhost:" + server.port();
        webClient = WebClient.builder()
                .keepAlive(false)
                .baseUri("http://localhost:" + server.port(COORDINATOR_ROUTING_NAME) + "/lra-coordinator")
                .build();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.shutdown();
        }
        if (coordinatorService != null) {
            coordinatorService.shutdown();
        }
    }

    @Test
    void startAndClose() {
        String lraId = start();

        assertThat(getParsedStatusOfLra(lraId), is(LRAStatus.Active));
        assertThat(status(lraId), is(LRAStatus.Active));

        close(lraId);

        assertThat(getParsedStatusOfLra(lraId), is(LRAStatus.Closed));
        assertThat(status(lraId), is(LRAStatus.Closed));
    }

    @Test
    void startAndCancel() {
        String lraId = start();

        assertThat(getParsedStatusOfLra(lraId), is(LRAStatus.Active));
        assertThat(status(lraId), is(LRAStatus.Active));

        close(lraId);

        assertThat(getParsedStatusOfLra(lraId), is(LRAStatus.Closed));
        assertThat(status(lraId), is(LRAStatus.Closed));
    }

    private String start() {
        return webClient
                .post()
                .path("start")
                .submit()
                .flatMapSingle(res -> res.content().as(String.class))
                .await(TIMEOUT);
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
                .await(TIMEOUT);
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
                .await(TIMEOUT);
    }

    private void close(String lraId) {
        WebClient.builder()
                .addMediaSupport(JsonpSupport.create())
                // Lra id is already whole url
                .baseUri(lraId + "/close")
                .build()
                .put()
                .submit()
                .await(TIMEOUT);
    }

    private void cancel(String lraId) {
        WebClient.builder()
                .addMediaSupport(JsonpSupport.create())
                // Lra id is already whole url
                .baseUri(lraId + "/cancel")
                .build()
                .put()
                .submit()
                .await(TIMEOUT);
    }
}
