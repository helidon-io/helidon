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
import java.lang.System.Logger.Level;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.tests.common.model.Critter;
import io.helidon.dbclient.tests.common.utils.TestConfig;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;

import static io.helidon.dbclient.tests.common.model.Kind.KINDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Verify metrics check in web server environment.
 */
@ServerTest
public abstract class ServerMetricsCheckIT {

    private static final System.Logger LOGGER = System.getLogger(ServerMetricsCheckIT.class.getName());
    private static final int BASE_ID = TestConfig.LAST_POKEMON_ID + 300;

    private final DbClient dbClient;
    private final WebServer server;
    private final WebClient webClient;

    public ServerMetricsCheckIT(DbClient dbClient) {
        this.dbClient = dbClient;
        this.server = WebServer.builder()
                .build()
                .start();
        this.webClient = WebClient.builder()
                .baseUri("http://localhost:" + server.port())
                .build();
    }

    /**
     * Read and check Database Client metrics from Helidon Web Server.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws IOException          if an I/O error occurs when sending or receiving HTTP request
     */
    @Test
    void testHttpMetrics() throws IOException, InterruptedException {
        // Call select-pokemons to trigger it

        dbClient.execute()
                .namedQuery("select-pokemons")
                .forEach(p -> {
                });

        // Call insert-pokemon to trigger it
        Critter pokemon = new Critter(BASE_ID + 1, "Lickitung", KINDS.get(1));
        long count = dbClient.execute()
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName());
        assertThat(count, equalTo(1L));

        // Read and process metrics response
        ClientResponseTyped<JsonObject> response = webClient.get("/observe/metrics/application")
                .header(HeaderNames.ACCEPT, "application/json")
                .request(JsonObject.class);
        assertThat(response.status(), is(Status.OK_200));

        JsonObject application = response.entity();
        assertThat(application, notNullValue());
        assertThat(application.getValueType(), equalTo(JsonValue.ValueType.OBJECT));
        assertThat(application.size(), greaterThan(0));
        assertThat(application.containsKey("db.counter.select-pokemons"), equalTo(true));
        assertThat(application.containsKey("db.counter.insert-pokemon"), equalTo(true));

        int selectCritters = application.getInt("db.counter.select-pokemons");
        int insertCritters = application.getInt("db.counter.insert-pokemon");
        assertThat(selectCritters, greaterThan(0));
        assertThat(insertCritters, greaterThan(0));
        assertThat(application.containsKey("db.timer.insert-pokemon"), equalTo(true));
        JsonObject insertTimer = application.getJsonObject("db.timer.insert-pokemon");
        assertThat(insertTimer.containsKey("count"), equalTo(true));
        assertThat(insertTimer.containsKey("mean"), equalTo(true));
        assertThat(insertTimer.containsKey("max"), equalTo(true));

        int timerCount = insertTimer.getInt("count");
        assertThat(timerCount, greaterThan(0));
    }

}
