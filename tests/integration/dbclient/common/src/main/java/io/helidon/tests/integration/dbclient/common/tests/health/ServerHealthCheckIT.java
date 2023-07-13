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
package io.helidon.tests.integration.dbclient.common.tests.health;

import java.io.IOException;
import java.io.StringReader;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import io.helidon.health.HealthCheck;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.nima.observe.ObserveFeature;
import io.helidon.nima.observe.health.HealthFeature;
import io.helidon.nima.observe.health.HealthObserveProvider;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.stream.JsonParsingException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.CONFIG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verify health check in web server environment.
 */
public class ServerHealthCheckIT {

    private static final System.Logger LOGGER = System.getLogger(ServerHealthCheckIT.class.getName());

    private static WebServer SERVER;
    private static String URL;

    private static void routing(HttpRouting.Builder routing) {
        HealthCheck check = DbClientHealthCheck.create(DB_CLIENT, CONFIG.get("db.health-check"));
        HealthFeature health = HealthFeature.builder()
                .addCheck(check)
                .build();
        ObserveFeature observe = ObserveFeature.builder()
                .addProvider(HealthObserveProvider.create(health))
                .build();
        routing.addFeature(observe);
    }

    /**
     * Start Helidon Web Server with Database Client health check support.
     */
    @BeforeAll
    public static void startup() {
        SERVER = WebServer.builder()
                .routing(ServerHealthCheckIT::routing)
                .config(CONFIG.get("server"))
                .build();
        URL = "http://localhost:" + SERVER.port();
        System.out.println("WEB server is running at " + URL);
    }

    /**
     * Stop Helidon Web Server with Database Client health check support.
     */
    @AfterAll
    public static void shutdown() {
        SERVER.stop();
    }

    /**
     * Retrieve server health status from Helidon Web Server.
     *
     * @param url server health status URL
     * @return server health status response (JSON)
     * @throws IOException          if an I/O error occurs when sending or receiving HTTP request
     * @throws InterruptedException if the current thread was interrupted
     */
    private static String get(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * Read and check Database Client health status from Helidon Web Server.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws IOException          if an I/O error occurs when sending or receiving HTTP request
     */
    @Test
    public void testHttpHealth() throws IOException, InterruptedException {
        // Call select-pokemons to warm up server
        DB_CLIENT.execute()
                .namedQuery("select-pokemons")
                .forEach(it -> {});

        // Read and process health check response
        String response = get(URL + "/health");
        LOGGER.log(Level.DEBUG, () -> String.format("RESPONSE: %s", response));
        JsonStructure jsonResponse = null;
        try (JsonReader jr = Json.createReader(new StringReader(response))) {
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
