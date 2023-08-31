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
package io.helidon.tests.integration.dbclient.app.tests;

import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.dbclient.app.tools.QueryParams;
import io.helidon.tests.integration.harness.TestClient;

import io.helidon.tests.integration.harness.TestServiceClient;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test JDBC metrics.
 */
class ServerMetricsCheckIT {

    private static final System.Logger LOGGER = System.getLogger(ServerMetricsCheckIT.class.getName());

    private final TestClient testClient;

    ServerMetricsCheckIT(int serverPort) {
        this.testClient = TestServiceClient.builder()
                .port(serverPort)
                .build();
    }

    /**
     * Read and check Database Client metrics from Helidon Web Server.
     *
     */
    // TODO metrics
    @Disabled
    @Test
    void testHttpMetrics() {
        LOGGER.log(System.Logger.Level.DEBUG, () -> String.format("Running %s.%s on client", getClass().getSimpleName(), "testHttpMetrics"));

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

        JsonObject application;
        application = testClient.callServiceAndGetRawData("observe/metrics", "application");
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
        assertThat(insertTimer.containsKey("max"), equalTo(true));
        int timerCount = insertTimer.getInt("count");
        assertThat(timerCount, greaterThan(0));
    }
}
