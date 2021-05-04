/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.appl.it.mapping;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonArray;
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
 * Verify mapping interface.
 * Pokemon POJO mapper is defined in Pokemon class.
 */
public class MapperIT {

    private static final Logger LOGGER = Logger.getLogger(MapperIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("Mapper")
            .build();

    private void executeInsertTest(final String testName, final int id) {
        LOGGER.fine(() -> String.format("Running %s", testName));
        try {
            JsonObject data = testClient.callServiceAndGetData(
                    testName,
                    QueryParams.single(QueryParams.ID, String.valueOf(id)))
                    .asJsonObject();
            LogData.logJsonObject(Level.FINER, data);
            JsonObject pokemonData = VerifyData.getPokemon(testClient, id);
            LogData.logJsonObject(Level.FINER, pokemonData);
            VerifyData.verifyPokemon(pokemonData, data);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e, () -> String.format("Exception in %s: %s", testName, e.getMessage()));
        }
    }

    private void executeUpdateTest(final String testName, final int id, final String newName) {
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
            LOGGER.fine(() -> String.format("Rows updated: %d", count));
            JsonObject pokemonData = VerifyData.getPokemon(testClient, pokemon.getId());
            LogData.logJsonObject(Level.FINER, pokemonData);
            assertThat(count, equalTo(1L));
            VerifyData.verifyPokemon(pokemonData, updatedPokemon);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e, () -> String.format("Exception in %s: %s", testName, e.getMessage()));
        }
    }

    private void executeDeleteTest(final String testName, final int id) {
        LOGGER.fine(() -> String.format("Running %s", testName));
        try {
            JsonValue data = testClient.callServiceAndGetData(
                    testName,
                    QueryParams.single(QueryParams.ID, String.valueOf(id)))
                    .asJsonObject();
            Long count = JsonTools.getLong(data);
            LOGGER.fine(() -> String.format("Rows deleted: %d", count));
            JsonObject pokemonData = VerifyData.getPokemon(testClient, id);
            LogData.logJsonObject(Level.FINER, pokemonData);
            assertThat(count, equalTo(1));
            assertThat(pokemonData.isEmpty(), equalTo(true));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e, () -> String.format("Exception in %s: %s", testName, e.getMessage()));
        }
    }

    /**
     * Verify insertion of PoJo instance using indexed mapping.
     */
    @Test
    public void testInsertWithOrderMapping() {
        executeInsertTest("testInsertWithOrderMapping", 103);
    }

    /**
     * Verify insertion of PoJo instance using named mapping.
     */
    @Test
    public void testInsertWithNamedMapping() {
        executeInsertTest("testInsertWithNamedMapping", 104);
    }

    /**
     * Verify update of PoJo instance using indexed mapping.
     */
    @Test
    public void testUpdateWithOrderMapping() {
        executeUpdateTest("testUpdateWithOrderMapping", 99, "Masquerain");
    }

    /**
     * Verify update of PoJo instance using named mapping.
     */
    @Test
    public void testUpdateWithNamedMapping() {
        executeUpdateTest("testUpdateWithNamedMapping", 100, "Moltres");
    }

    /**
     * Verify delete of PoJo instance using indexed mapping.
     */
    @Test
    public void testDeleteWithOrderMapping() {
        executeDeleteTest("testDeleteWithOrderMapping", 101);
    }

    /**
     * Verify delete of PoJo instance using named mapping.
     */
    @Test
    public void testDeleteWithNamedMapping() {
        executeDeleteTest("testDeleteWithNamedMapping", 102);
    }

    // Query and Get calls are here just once so no common executor code is needed.

    /**
     * Verify query of PoJo instance as a result using mapping.
     */
    @Test
    public void testQueryWithMapping() {
        LOGGER.fine(() -> "Running testQueryWithMapping");
        final Pokemon pokemon = Pokemon.POKEMONS.get(1);
        JsonArray data = testClient.callServiceAndGetData(
                "testQueryWithMapping",
                QueryParams.single(QueryParams.NAME, pokemon.getName()))
                .asJsonArray();
        LogData.logJsonArray(Level.FINER, data);
        assertThat(data.size(), equalTo(1));
        VerifyData.verifyPokemon(data.getJsonObject(0), pokemon);
    }

    /**
     * Verify get of PoJo instance as a result using mapping.
     */
    @Test
    public void testGetWithMapping() {
        LOGGER.fine(() -> "Running testGetWithMapping");
        final Pokemon pokemon = Pokemon.POKEMONS.get(2);
        JsonObject data = testClient.callServiceAndGetData(
                "testGetWithMapping",
                QueryParams.single(QueryParams.NAME, pokemon.getName())
        ).asJsonObject();
        LogData.logJsonObject(Level.FINER, data);
        VerifyData.verifyPokemon(data, pokemon);
    }

}
