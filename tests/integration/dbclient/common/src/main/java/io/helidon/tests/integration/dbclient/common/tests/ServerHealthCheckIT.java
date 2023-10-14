/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common.tests;

import java.io.IOException;
import java.io.StringReader;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.http.Status;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.stream.JsonParsingException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verify health check in web server environment.
 */
@ExtendWith(DbClientParameterResolver.class)
public class ServerHealthCheckIT {

    private static final System.Logger LOGGER = System.getLogger(ServerHealthCheckIT.class.getName());

    private static WebServer SERVER;
    private static String URL;
    private final DbClient dbClient;

    public ServerHealthCheckIT(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @BeforeAll
    public static void setup(DbClient dbClient, Config config) {
        SERVER = WebServer.builder()
                .update(builder -> routing(dbClient, config, builder))
                .config(config.get("server"))
                .build()
                .start();
        URL = "http://localhost:" + SERVER.port();
        LOGGER.log(Level.TRACE, () -> "WEB server is running at " + URL);
    }

    // Add 2 endpoints:
    // - HealthCheck /noDetails/health with details turned off
    // - HealthCheck /details/health with details turned on
    private static void routing(DbClient dbClient, Config config, WebServerConfig.Builder router) {
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

    @AfterAll
    public static void shutdown() {
        SERVER.stop();
        LOGGER.log(Level.TRACE, () -> "WEB server stopped");
    }

    /**
     * Retrieve server health status from Helidon Web Server.
     *
     * @param url server health status URL
     * @return server health status response (JSON)
     * @throws IOException          if an I/O error occurs when sending or receiving HTTP request
     * @throws InterruptedException if the current thread was interrupted
     */
    private static HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }
        return response;
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
        String url = URL + "/noDetails/health";
        // Read and process health check response
        HttpResponse<String> response = get(url);
        LOGGER.log(Level.TRACE, () -> String.format("%s RESPONSE: %d", response.statusCode()));
        assertThat(response.statusCode(), equalTo(Status.NO_CONTENT_204.code()));
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
        String url = URL + "/details/health";
        // Read and process health check response
        HttpResponse<String> response = get(url);
        assertThat(response.statusCode(), equalTo(Status.OK_200.code()));
            String jsonSrc = response.body();
            LOGGER.log(Level.TRACE, () -> String.format("%s RESPONSE: %s", url, jsonSrc));
            JsonStructure jsonResponse = null;
            try (JsonReader jr = Json.createReader(new StringReader(jsonSrc))) {
                jsonResponse = jr.read();
            } catch (JsonParsingException | IllegalStateException ex) {
                fail(String.format("Error parsing response: %s", ex.getMessage()));
            }
            JsonArray checks = jsonResponse.asJsonObject().getJsonArray("checks");
            assertThat(checks.size(), greaterThan(0));
            checks.forEach((check) -> {
                String status = check.asJsonObject().getString("status");
                assertThat(status, equalTo("UP"));
            });
    }

}
