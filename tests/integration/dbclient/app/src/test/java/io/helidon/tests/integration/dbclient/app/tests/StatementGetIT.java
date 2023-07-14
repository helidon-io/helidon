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

import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test DML get statements.
 */
class StatementGetIT {

    private static final System.Logger LOGGER = System.getLogger(StatementGetIT.class.getName());

    private final TestServiceClient testClient;

    StatementGetIT(int serverPort) {
        this.testClient = TestClient.builder()
                .port(serverPort)
                .service("StatementGet")
                .build();
    }

    private void executeTest(String testName, int fromId, int toId) {
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on client", getClass().getSimpleName(), testName));
        JsonObject data = testClient
                .callServiceAndGetData(
                        testName,
                        QueryParams.builder()
                                .add(QueryParams.FROM_ID, String.valueOf(fromId))
                                .add(QueryParams.TO_ID, String.valueOf(toId))
                                .build())
                .asJsonObject();
        LogData.logJsonObject(Level.DEBUG, data);
        int[] counter = {0};
        Pokemon.POKEMONS.keySet().forEach(id -> {
            if (id > fromId && id < toId) {
                Pokemon pokemon = Pokemon.POKEMONS.get(id);
                VerifyData.verifyPokemon(data, pokemon);
                counter[0]++;
            }
        });
        assertThat(counter[0], equalTo(1));
    }

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     */
    @Test
    void testGetArrayParams() {
        executeTest("testGetArrayParams", 0, 2);
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     */
    @Test
    void testGetListParams() {
        executeTest("testGetListParams", 1, 3);
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     */
    @Test
    void testGetMapParams() {
        executeTest("testGetMapParams", 2, 4);
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     */
    @Test
    void testGetOrderParam() {
        executeTest("testGetOrderParam", 3, 5);

    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     */
    @Test
    void testGetNamedParam() {
        executeTest("testGetNamedParam", 4, 6);
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    void testGetMappedNamedParam() {
        executeTest("testGetMappedNamedParam", 5, 7);
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    void testGetMappedOrderParam() {
        executeTest("testGetMappedOrderParam", 6, 8);
    }

}
