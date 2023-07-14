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

import java.lang.System.Logger.Level;

import io.helidon.tests.integration.dbclient.app.LogData;
import io.helidon.tests.integration.dbclient.app.VerifyData;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.dbclient.app.tools.QueryParams;
import io.helidon.tests.integration.harness.TestClient;
import io.helidon.tests.integration.harness.TestServiceClient;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

/**
 * Test DML query statements.
 */
class StatementQueryIT {

    private static final System.Logger LOGGER = System.getLogger(StatementQueryIT.class.getName());

    private final TestServiceClient testClient;

    StatementQueryIT(int serverPort) {
        this.testClient = TestClient.builder()
                .port(serverPort)
                .service("StatementQuery")
                .build();
    }

    private void executeTest(String testName, int fromId, int toId) {
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on client", getClass().getSimpleName(), testName));
        JsonArray data = testClient
                .callServiceAndGetData(
                        testName,
                        QueryParams.builder()
                                .add(QueryParams.FROM_ID, String.valueOf(fromId))
                                .add(QueryParams.TO_ID, String.valueOf(toId))
                                .build())
                .asJsonArray();
        LogData.logJsonArray(Level.DEBUG, data);
        assertThat(data.size(), equalTo(toId - fromId - 1));
        data.getValuesAs(JsonObject.class).forEach(dataPokemon -> {
            int id = dataPokemon.getInt("id");
            Pokemon pokemon = Pokemon.POKEMONS.get(id);
            assertThat(id, greaterThan(fromId));
            assertThat(id, lessThan(toId));
            VerifyData.verifyPokemon(dataPokemon, pokemon);
        });
    }

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     */
    @Test
    void testQueryArrayParams() {
        executeTest("testQueryArrayParams", 0, 7);
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     */
    @Test
    void testQueryListParams() {
        executeTest("testQueryListParams", 0, 7);
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     */
    @Test
    void testQueryMapParams() {
        executeTest("testQueryMapParams", 0, 7);
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     */
    @Test
    void testQueryOrderParam() {
        executeTest("testQueryOrderParam", 0, 7);
    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     */
    @Test
    void testQueryNamedParam() {
        executeTest("testQueryNamedParam", 0, 7);
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    void testQueryMappedNamedParam() {
        executeTest("testQueryMappedNamedParam", 0, 7);
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    void testQueryMappedOrderParam() {
        executeTest("testQueryMappedOrderParam", 0, 7);
    }

}
