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
package io.helidon.tests.integration.dbclient.appl.it.statement;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonObject;
import javax.json.JsonValue;

import io.helidon.tests.integration.dbclient.appl.it.LogData;
import io.helidon.tests.integration.dbclient.appl.it.VerifyData;
import io.helidon.tests.integration.dbclient.appl.it.tools.JsonTools;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.dbclient.appl.tools.QueryParams;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test DbStatementDml methods.
 */
public class DmlStatementIT {

    private static final Logger LOGGER = Logger.getLogger(DmlStatementIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("DmlStatement")
            .build();

    // Test executor method
    private void executeTest(final String testName, final int id, final String newName) {
        LOGGER.fine(() -> String.format("Running %s", testName));
        try {
            Pokemon pokemon = Pokemon.POKEMONS.get(id);
            Pokemon updatedPokemon = new Pokemon(pokemon.getId(), newName, pokemon.getTypes());
            JsonValue data = testClient.callServiceAndGetData(
                    testName,
                    QueryParams.builder()
                            .add(QueryParams.NAME, newName)
                            .add(QueryParams.ID, String.valueOf(id))
                            .build());
            Long count = JsonTools.getLong(data);
            LOGGER.fine(() -> String.format("Rows modified: %d", count));
            JsonObject pokemonData = VerifyData.getPokemon(testClient, pokemon.getId());
            LogData.logJsonObject(Level.FINER, pokemonData);
            assertThat(count, equalTo(1L));
            VerifyData.verifyPokemon(pokemonData, updatedPokemon);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e, () -> String.format("Exception in %s: %s", testName, e.getMessage()));
        }
    }

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     */
    @Test
    public void testDmlArrayParams() {
        executeTest("testDmlArrayParams", 50, "Shinx");
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     */
    @Test
    public void testDmlListParams() {
        executeTest("testDmlListParams", 51, "Luxio");
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     */
    @Test
    public void testDmlMapParams() {
        executeTest("testDmlMapParams", 52, "Luxray");
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     */
    @Test
    public void testDmlOrderParam() {
        executeTest("testDmlOrderParam", 53, "Kricketot");
    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     */
    @Test
    public void testDmlNamedParam() {
        executeTest("testDmlNamedParam", 54, "Kricketune");
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    public void testDmlMappedNamedParam() {
        executeTest("testDmlMappedNamedParam", 55, "Phione");
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    public void testDmlMappedOrderParam() {
        executeTest("testDmlMappedOrderParam", 56, "Chatot");
    }

}
