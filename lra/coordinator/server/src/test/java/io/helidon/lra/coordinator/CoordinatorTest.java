/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Multi;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void rejectUnsafeParticipantLink() {
        String lraId = start();
        String link = "<http://127.0.0.1:" + server.port() + CONTEXT_PATH + ">; rel=\"compensate\"";

        for (int i = 0; i < 2; i++) {
            WebClientResponse response = WebClient.builder()
                    .addMediaSupport(JsonpSupport.create())
                    .baseUri(lraId)
                    .build()
                    .put()
                    .addHeader("Link", link)
                    .submit()
                    .await(TIMEOUT);
            try {
                assertThat(response.status().code(), is(400));
            } finally {
                response.close();
            }
        }
    }

    @Test
    void participantUriValidationAllowsConfiguredHost() {
        AtomicInteger redirectedCalls = new AtomicInteger();
        AtomicReference<WebServer> allowedServerRef = new AtomicReference<>();
        LazyValue<URI> coordinatorUri = LazyValue.create(() -> URI.create("http://localhost:"
                + allowedServerRef.get().port(COORDINATOR_ROUTING_NAME) + "/lra-coordinator"));
        CoordinatorService allowedCoordinator = CoordinatorService.builder()
                .url(coordinatorUri::get)
                .config(Config.builder(
                                () -> ConfigSources.create(Map.of(
                                        "helidon.lra.coordinator.db.connection.url",
                                        "jdbc:h2:file:./target/lra-coordinator-allowed",
                                        "helidon.lra.coordinator.participant-url.validation.allowed-hosts.0",
                                        "127.0.0.1"
                                )).build(),
                                () -> ConfigSources.classpath("application.yaml").build()
                        )
                        .build().get(CoordinatorService.CONFIG_PREFIX))
                .build();
        WebServer allowedServer = WebServer.builder()
                .host("localhost")
                .addSocket(SocketConfiguration.builder()
                        .name(COORDINATOR_ROUTING_NAME)
                        .port(0)
                        .build())
                .addNamedRouting(COORDINATOR_ROUTING_NAME, Routing.builder()
                        .register("/lra-coordinator", allowedCoordinator)
                        .build())
                .routing(r -> r
                        .put("/redirect", (req, res) -> {
                            res.headers().put("Location",
                                    "http://127.0.0.1:" + allowedServerRef.get().port() + CONTEXT_PATH);
                            res.status(302).send();
                        })
                        .register(CONTEXT_PATH, rules -> rules.put((req, res) -> {
                            redirectedCalls.incrementAndGet();
                            res.send();
                        })))
                .build();
        allowedServerRef.set(allowedServer);

        try {
            allowedServer.start().await(TIMEOUT);
            String coordinatorBaseUri = "http://localhost:" + allowedServer.port(COORDINATOR_ROUTING_NAME)
                    + "/lra-coordinator";
            WebClient allowedClient = WebClient.builder()
                    .keepAlive(false)
                    .baseUri(coordinatorBaseUri)
                    .build();
            String lraId = allowedClient
                    .post()
                    .path("start")
                    .submit()
                    .flatMapSingle(res -> res.content().as(String.class))
                    .await(TIMEOUT);
            String link = "<http://127.0.0.1:" + allowedServer.port() + CONTEXT_PATH + ">; rel=\"compensate\"";
            WebClientResponse response = WebClient.builder()
                    .addMediaSupport(JsonpSupport.create())
                    .baseUri(lraId)
                    .build()
                    .put()
                    .addHeader("Link", link)
                    .submit()
                    .await(TIMEOUT);
            try {
                assertThat(response.status().code(), is(200));
            } finally {
                response.close();
            }

            String rejectedLraId = allowedClient
                    .post()
                    .path("start")
                    .submit()
                    .flatMapSingle(res -> res.content().as(String.class))
                    .await(TIMEOUT);
            String rejectedLink = "<http://localhost:" + allowedServer.port() + CONTEXT_PATH + ">; rel=\"compensate\"";
            WebClientResponse rejectedResponse = WebClient.builder()
                    .addMediaSupport(JsonpSupport.create())
                    .baseUri(rejectedLraId)
                    .build()
                    .put()
                    .addHeader("Link", rejectedLink)
                    .submit()
                    .await(TIMEOUT);
            try {
                assertThat(rejectedResponse.status().code(), is(400));
            } finally {
                rejectedResponse.close();
            }

            String redirectLraId = allowedClient
                    .post()
                    .path("start")
                    .submit()
                    .flatMapSingle(res -> res.content().as(String.class))
                    .await(TIMEOUT);
            String redirectLink = "<http://127.0.0.1:" + allowedServer.port() + "/redirect>; rel=\"complete\"";
            WebClientResponse redirectJoinResponse = WebClient.builder()
                    .addMediaSupport(JsonpSupport.create())
                    .baseUri(redirectLraId)
                    .build()
                    .put()
                    .addHeader("Link", redirectLink)
                    .submit()
                    .await(TIMEOUT);
            try {
                assertThat(redirectJoinResponse.status().code(), is(200));
            } finally {
                redirectJoinResponse.close();
            }

            WebClientResponse closeResponse = WebClient.builder()
                    .addMediaSupport(JsonpSupport.create())
                    .baseUri(redirectLraId + "/close")
                    .build()
                    .put()
                    .submit()
                    .await(TIMEOUT);
            try {
                assertThat(closeResponse.status().code(), is(200));
                assertThat(redirectedCalls.get(), is(0));
            } finally {
                closeResponse.close();
            }
        } finally {
            allowedServer.shutdown();
            allowedCoordinator.shutdown();
        }
    }

    @Test
    void participantUriValidationRejectsLocalAddressByDefault() {
        ParticipantUriValidator validator = ParticipantUriValidator.create(Config.empty());

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(URI.create("http://127.0.0.1:8080/compensate")));
    }

    @Test
    void participantUriValidationRejectsSpecialUseAddressByDefault() {
        ParticipantUriValidator validator = ParticipantUriValidator.create(Config.empty());

        for (String host : List.of(
                "0.1.2.3",
                "100.64.0.1",
                "192.0.0.1",
                "192.0.2.1",
                "192.88.99.1",
                "198.18.0.1",
                "198.19.0.1",
                "198.51.100.1",
                "203.0.113.1",
                "240.0.0.1",
                "[fc00::1]")) {
            assertThrows(IllegalArgumentException.class,
                    () -> validator.validate(URI.create("http://" + host + ":8080/compensate")));
        }
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
