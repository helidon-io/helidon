/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.dbclient.tests.common.tests;

import java.io.IOException;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

/**
 * Verify health check in web server environment.
 */
public abstract class ServerHealthCheckIT {

    private static final System.Logger LOGGER = System.getLogger(ServerHealthCheckIT.class.getName());

    private static String URL;
    private final DbClient dbClient;
    private final WebServer server;
    private final WebClient webClient;

    public ServerHealthCheckIT(Config config, DbClient dbClient) {
        this.dbClient = dbClient;
        this.server = WebServer.builder()
                .update(builder -> routing(config, dbClient, builder))
                .build()
                .start();
        this.webClient = WebClient.builder()
                .baseUri("http://localhost:" + server.port())
                .build();
    }

    // Add 2 endpoints:
    // - HealthCheck /noDetails/health with details turned off
    // - HealthCheck /details/health with details turned on
    private static void routing(Config config, DbClient dbClient, WebServerConfig.Builder router) {
        router.addFeature(
                createObserveFeature(dbClient, config, "healthNoDetails", "noDetails", false));
        router.addFeature(
                createObserveFeature(dbClient, config, "healthDetails", "details", true));
    }

    private static ObserveFeature createObserveFeature(DbClient dbClient, Config config, String name, String endpoint, boolean details) {
        return ObserveFeature.builder()
                .observersDiscoverServices(false)
                .endpoint(endpoint)
                .name(name)
                .addObserver(HealthObserver.builder()
                                     .addCheck(DbClientHealthCheck.builder(dbClient)
                                                       .config(config.get("db.health-check"))
                                                       .name(name)
                                                       .build())
                                     .details(details)
                                     .build())
                .build();
    }

    /**
     * Read and check Database Client health status from Helidon Web Server.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws IOException          if an I/O error occurs when sending or receiving HTTP request
     */
    @Test
    void testHttpHealthNoDetails() throws IOException, InterruptedException {
        // Call select-pokemons to warm up server
        dbClient.execute().namedQuery("select-pokemons").forEach(it -> {});
        // Read and process health check response
        ClientResponseTyped<String> response = webClient.get("/noDetails/health")
                .request(String.class);
        assertThat(response.status(), is(Status.NO_CONTENT_204));
    }

    /**
     * Read and check Database Client health status from Helidon Web Server.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws IOException          if an I/O error occurs when sending or receiving HTTP request
     */
    @Test
    void testHttpHealthDetails() throws IOException, InterruptedException {
        // Call select-pokemons to warm up server
        dbClient.execute().namedQuery("select-pokemons").forEach(it -> {});
        // Read and process health check response
        ClientResponseTyped<JsonObject> response = webClient.get("/details/health")
                .request(JsonObject.class);
        assertThat(response.status(), is(Status.OK_200));

        JsonObject jsonResponse = response.entity();
        JsonArray checks = jsonResponse.getJsonArray("checks");
        assertThat(checks.size(), greaterThan(0));
        checks.forEach((check) -> {
            String status = check.asJsonObject().getString("status");
            assertThat(status, equalTo("UP"));
        });
    }

}
