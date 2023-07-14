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
import io.helidon.tests.integration.harness.JsonValues;
import io.helidon.tests.integration.harness.TestClient;
import io.helidon.tests.integration.harness.TestServiceClient;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test DML statements.
 */
class StatementDmlIT {

    private static final System.Logger LOGGER = System.getLogger(StatementDmlIT.class.getName());

    private final TestServiceClient testClient;

    StatementDmlIT(int serverPort) {
        this.testClient = TestClient.builder()
                .port(serverPort)
                .service("StatementDml")
                .build();
    }

    private void executeTest(String testName, int id, String newName) {
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on client", getClass().getSimpleName(), testName));
        Pokemon pokemon = Pokemon.POKEMONS.get(id);
        Pokemon updatedPokemon = new Pokemon(pokemon.getId(), newName, pokemon.getTypes());
        JsonValue data = testClient
                .callServiceAndGetData(
                        testName,
                        QueryParams.builder()
                                .add(QueryParams.NAME, newName)
                                .add(QueryParams.ID, String.valueOf(id))
                                .build());
        Long count = JsonValues.asLong(data);
        LOGGER.log(Level.DEBUG, () -> String.format("Rows modified: %d", count));
        JsonObject pokemonData = VerifyData.getPokemon(testClient, pokemon.getId());
        LogData.logJsonObject(Level.DEBUG, pokemonData);
        assertThat(count, equalTo(1L));
        VerifyData.verifyPokemon(pokemonData, updatedPokemon);
    }

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     */
    @Test
    void testDmlArrayParams() {
        executeTest("testDmlArrayParams", 50, "Shinx");
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     */
    @Test
    void testDmlListParams() {
        executeTest("testDmlListParams", 51, "Luxio");
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     */
    @Test
    void testDmlMapParams() {
        executeTest("testDmlMapParams", 52, "Luxray");
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     */
    @Test
    void testDmlOrderParam() {
        executeTest("testDmlOrderParam", 53, "Kricketot");
    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     */
    @Test
    void testDmlNamedParam() {
        executeTest("testDmlNamedParam", 54, "Kricketune");
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    void testDmlMappedNamedParam() {
        executeTest("testDmlMappedNamedParam", 55, "Phione");
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    void testDmlMappedOrderParam() {
        executeTest("testDmlMappedOrderParam", 56, "Chatot");
    }

}
