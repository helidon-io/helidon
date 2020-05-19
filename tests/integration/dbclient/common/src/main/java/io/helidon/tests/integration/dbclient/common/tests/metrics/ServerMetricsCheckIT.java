/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common.tests.metrics;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.stream.JsonParsingException;

import io.helidon.common.reactive.Multi;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.metrics.DbCounter;
import io.helidon.dbclient.metrics.DbTimer;
import io.helidon.metrics.MetricsSupport;
import io.helidon.tests.integration.dbclient.common.AbstractIT;
import io.helidon.tests.integration.dbclient.common.AbstractIT.Pokemon;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.CONFIG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.LAST_POKEMON_ID;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.TYPES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verify metrics check in web server environment.
 */
public class ServerMetricsCheckIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(ServerMetricsCheckIT.class.getName());

    /** Maximum Pokemon ID. */
    private static final int BASE_ID = LAST_POKEMON_ID + 300;

    private static DbClient DB_CLIENT;
    private static WebServer SERVER;
    private static String URL;

    private static Routing createRouting() {
        return Routing.builder()
                .register(MetricsSupport.create()) // Metrics at "/metrics"
                .build();
    }

    /**
     * Initialize DB Client from test configuration.
     *
     * @return DB Client instance
     */
    private static DbClient initDbClient() {
        Config dbConfig = AbstractIT.CONFIG.get("db");
        return DbClient.builder(dbConfig)
                // add an interceptor to named statement(s)
                .addInterceptor(DbCounter.create(), "select-pokemons", "insert-pokemon")
                // add an interceptor to statement type(s)
                .addInterceptor(DbTimer.create(), DbStatementType.INSERT)
                .build();
    }

    /**
     * Start Helidon Web Server with DB Client metrics support.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @BeforeAll
    public static void startup() throws InterruptedException, ExecutionException {
        DB_CLIENT = initDbClient();
        final WebServer server = WebServer.create(createRouting(), CONFIG.get("server"));
        final CompletionStage<WebServer> serverFuture = server.start();
        serverFuture.thenAccept(srv -> {
            LOGGER.info(() -> String
                    .format("WEB server is running at http://%s:%d", srv.configuration().bindAddress(), srv.port()));
            URL = String.format("http://localhost:%d", srv.port());
        });
        SERVER = serverFuture.toCompletableFuture().get();
    }

    /**
     * Stop Helidon Web Server with DB Client metrics support.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @AfterAll
    public static void shutdown() throws InterruptedException, ExecutionException {
        SERVER.shutdown().toCompletableFuture().get();
    }

    /**
     * Retrieve server metrics status from Helidon Web Server.
     *
     * @param url server health status URL
     * @return server health status response (JSON)
     * @throws IOException if an I/O error occurs when sending or receiving HTTP request
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
     * Read and check DB Client metrics from Helidon Web Server.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws IOException if an I/O error occurs when sending or receiving HTTP request
     * @throws ExecutionException when database query failed
     */
    @Test
    public void testHttpMetrics() throws IOException, InterruptedException, ExecutionException {
        // Call select-pokemons to trigger it
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-pokemons"));

        List<DbRow> pokemonList = rows.collectList().await();
        // Call insert-pokemon to trigger it
        Pokemon pokemon = new Pokemon(BASE_ID + 1, "Lickitung", TYPES.get(1));
        Long result = DB_CLIENT.execute(exec -> exec
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName())
        ).toCompletableFuture().get();
        // Read and process metrics response
        String response = get(URL + "/metrics");
        LOGGER.info("RESPONSE: " + response);
        JsonStructure jsonResponse = null;
        try (JsonReader jr = Json.createReader(new StringReader(response))) {
            jsonResponse = jr.read();
        } catch (JsonParsingException | IllegalStateException ex) {
            fail(String.format("Error parsing response: %s", ex.getMessage()));
        }
        assertThat(jsonResponse, notNullValue());
        assertThat(jsonResponse.getValueType(), equalTo(JsonValue.ValueType.OBJECT));
        assertThat(jsonResponse.asJsonObject().containsKey("application"), equalTo(true));
        JsonObject application = jsonResponse.asJsonObject().getJsonObject("application");
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
