/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.dbclient.appl.it.metrics;

import java.io.IOException;
import java.util.logging.Logger;

import javax.json.JsonObject;
import javax.json.JsonValue;

import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.dbclient.appl.tools.QueryParams;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.TestClient;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
/**
 * Verify metrics check in web server environment.
 */
public class ServerMetricsCheckIT {

    private static final Logger LOGGER = Logger.getLogger(ServerMetricsCheckIT.class.getName());

    private final TestClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .build();

    /**
     * Read and check DB Client metrics from Helidon Web Server.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws IOException if an I/O error occurs when sending or receiving HTTP request
     */
    @Test
    public void testHttpMetrics() throws IOException, InterruptedException {

        // Call select-pokemon-named-arg query to initialize counter
        testClient.callServiceAndGetData(
                "SimpleGet",
                "testCreateNamedGetStrNamedArgs",
                QueryParams.single(QueryParams.NAME, Pokemon.POKEMONS.get(1).getName()));
        // Call select-pokemon-order-arg query  to initialize counter
        testClient.callServiceAndGetData(
                "SimpleGet",
                "testCreateNamedGetStrOrderArgs",
                QueryParams.single(QueryParams.NAME, Pokemon.POKEMONS.get(2).getName()));

        JsonObject application = null;
        application = testClient.callServiceAndGetRawData("metrics", "application");
        assertThat(application, notNullValue());
        assertThat(application.getValueType(), equalTo(JsonValue.ValueType.OBJECT));
        assertThat(application.size(), greaterThan(0));
        assertThat(application.containsKey("db.counter.select-pokemon-named-arg"), equalTo(true));
        assertThat(application.containsKey("db.counter.select-pokemon-order-arg"), equalTo(true));
        assertThat(application.containsKey("db.counter.insert-pokemon"), equalTo(true));
        int selectPokemons = application.getInt("db.counter.select-pokemon-named-arg");
        int insertPokemons = application.getInt("db.counter.insert-pokemon");
        assertThat(selectPokemons, greaterThan(0));
        assertThat(insertPokemons, greaterThan(0));
        assertThat(application.containsKey("db.timer.select-pokemon-named-arg"), equalTo(true));
        assertThat(application.containsKey("db.timer.select-pokemon-named-arg"), equalTo(true));
        JsonObject insertTimer = application.getJsonObject("db.timer.select-pokemon-named-arg");
        assertThat(insertTimer.containsKey("count"), equalTo(true));
        assertThat(insertTimer.containsKey("min"), equalTo(true));
        assertThat(insertTimer.containsKey("max"), equalTo(true));
        int timerCount = insertTimer.getInt("count");
        assertThat(timerCount, greaterThan(0));

    }
}
