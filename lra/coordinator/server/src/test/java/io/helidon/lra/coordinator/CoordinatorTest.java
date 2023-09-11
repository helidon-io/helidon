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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.Socket;

import jakarta.json.JsonArray;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class CoordinatorTest {

    private static CompletableFuture<Void> shutdownFuture;
    private static int coordinatorPort;

    private final Http1Client client;

    CoordinatorTest(WebServer server, @Socket("coordinator") Http1Client client) {
        this.client = client;
        coordinatorPort = server.port("coordinator");
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder serverBuilder) {
        Config config = Config.create(ConfigSources.create(Map.of(
                        "helidon.lra.coordinator.db.connection.url", "jdbc:h2:file:./target/lra-coordinator")),
                ConfigSources.classpath("application.yaml"));

        CoordinatorService coordinatorService = CoordinatorService.builder()
                .url(() -> URI.create("http://localhost:" + coordinatorPort + "/lra-coordinator"))
                .config(config.get(CoordinatorService.CONFIG_PREFIX))
                .build();

        shutdownFuture = new CompletableFuture<>();
        shutdownFuture.thenRun(coordinatorService::shutdown);

        serverBuilder.shutdownHook(true)
                .routing(r -> r.register("/test", () -> rules -> rules.put((req, res) -> res.send())))
                .putSocket("coordinator", socket -> socket
                        .routing(routing -> routing
                                .register("/lra-coordinator", coordinatorService)))
                .build();
    }

    @AfterAll
    static void afterAll() {
        shutdownFuture.complete(null);
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
        return client.post("/lra-coordinator/start").requestEntity(String.class);
    }

    private static LRAStatus getParsedStatusOfLra(String lraId) {
        return Http1Client.builder()
                .addMediaSupport(JsonpSupport.create())
                // Lra id is already whole url
                .baseUri(lraId)
                .build()
                .get()
                .requestEntity(JsonArray.class)
                .stream()
                .map(JsonValue::asJsonObject)
                .map(jo -> jo.getString("status"))
                .map(LRAStatus::valueOf)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Unable to get LRA status for id: " + lraId));
    }

    private static LRAStatus status(String lraId) {
        String lraStatus = Http1Client.builder()
                .addMediaSupport(JsonpSupport.create())
                // Lra id is already whole url
                .baseUri(lraId + "/status")
                .build()
                .get()
                .requestEntity(String.class);
        return LRAStatus.valueOf(lraStatus);
    }

    private static void close(String lraId) {
        Http1Client.builder()
                .addMediaSupport(JsonpSupport.create())
                // Lra id is already whole url
                .baseUri(lraId + "/close")
                .build()
                .put()
                .request()
                .close();
    }

    private static void cancel(String lraId) {
        Http1Client.builder()
                .addMediaSupport(JsonpSupport.create())
                // Lra id is already whole url
                .baseUri(lraId + "/cancel")
                .build()
                .put()
                .request()
                .close();
    }
}
