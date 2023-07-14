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
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.metrics.DbClientMetrics;
import io.helidon.nima.observe.ObserveFeature;
import io.helidon.nima.webserver.WebServer;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;

import io.helidon.tests.integration.harness.SetUp;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParsingException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.model.Type.TYPES;
import static io.helidon.tests.integration.dbclient.common.tests.AbstractIT.LAST_POKEMON_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verify metrics check in web server environment.
 */
@SuppressWarnings("SpellCheckingInspection")
public class ServerMetricsCheckIT {

    private static final System.Logger LOGGER = System.getLogger(ServerMetricsCheckIT.class.getName());
    private static final int BASE_ID = LAST_POKEMON_ID + 300;

    private static DbClient DB_CLIENT;
    private static WebServer SERVER;
    private static String URL;

    private static DbClient initDbClient(Config config) {
        Config dbConfig = config.get("db");
        return DbClient.builder(dbConfig)
                // add an interceptor to named statement(s)
                .addService(DbClientMetrics.counter()
                        .statementNames("select-pokemons", "insert-pokemon"))
                // add an interceptor to statement type(s)
                .addService(DbClientMetrics.timer()
                        .statementTypes(DbStatementType.INSERT))
                .build();
    }

    @SetUp
    public static void startup(Config config) {
        DB_CLIENT = initDbClient(config);
        SERVER = WebServer.builder()
                .routing(routing -> routing.addFeature(ObserveFeature.create()))
                .config(config.get("server"))
                .build()
                .start();
        URL = "http://localhost:" + SERVER.port();
        System.out.println("WEB server is running at " + URL);
    }

    @AfterAll
    public static void shutdown() {
        if (null != SERVER) {
            SERVER.stop();
        }
    }

    /**
     * Retrieve server metrics status from Helidon Web Server.
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
     * Read and check Database Client metrics from Helidon Web Server.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws IOException          if an I/O error occurs when sending or receiving HTTP request
     */
    @Test
    public void testHttpMetrics() throws IOException, InterruptedException {
        // Call select-pokemons to trigger it

        DB_CLIENT.execute()
                .namedQuery("select-pokemons")
                .forEach(p -> {
                });

        // Call insert-pokemon to trigger it
        Pokemon pokemon = new Pokemon(BASE_ID + 1, "Lickitung", TYPES.get(1));
        DB_CLIENT.execute()
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName());
        // Read and process metrics response
        String response = get(URL + "/metrics/application");
        LOGGER.log(Level.DEBUG, () -> String.format("RESPONSE: %s", response));
        JsonObject application = null;
        try (JsonReader jr = Json.createReader(new StringReader(response))) {
            application = jr.readObject();
        } catch (JsonParsingException | IllegalStateException ex) {
            fail(String.format("Error parsing response: %s", ex.getMessage()));
        }
        assertThat(application, notNullValue());
        assertThat(application.getValueType(), equalTo(JsonValue.ValueType.OBJECT));

        assertThat(application.size(), greaterThan(0));
        assertThat(application.containsKey("db.counter.select-pokemons"), equalTo(true));
        assertThat(application.containsKey("db.counter.insert-pokemon"), equalTo(true));
        int selectPokemons = application.getInt("db.counter.select-pokemons");
        int insertPokemons = application.getInt("db.counter.insert-pokemon");
        assertThat(selectPokemons, equalTo(1));
        assertThat(insertPokemons, equalTo(1));
        assertThat(application.containsKey("db.timer.insert-pokemon"), equalTo(true));
        JsonObject insertTimer = application.getJsonObject("db.timer.insert-pokemon");
        assertThat(insertTimer.containsKey("count"), equalTo(true));
        assertThat(insertTimer.containsKey("min"), equalTo(true));
        assertThat(insertTimer.containsKey("max"), equalTo(true));
        int timerCount = insertTimer.getInt("count");
        assertThat(timerCount, equalTo(1));
    }

}
