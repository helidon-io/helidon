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
package io.helidon.tests.integration.dbclient.appl.it.mapping;

import java.lang.System.Logger.Level;

import io.helidon.tests.integration.dbclient.appl.it.LogData;
import io.helidon.tests.integration.dbclient.appl.it.VerifyData;
import io.helidon.tests.integration.dbclient.appl.it.tools.JsonTools;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.dbclient.appl.tools.QueryParams;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;


/**
 * Verify mapping interface.
 * Pokemon POJO mapper is defined in Pokemon class.
 */
public class MapperIT {

    private static final System.Logger LOGGER = System.getLogger(MapperIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("Mapper")
            .build();

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
        final Pokemon pokemon = Pokemon.POKEMONS.get(1);
        JsonArray data = testClient.callServiceAndGetData(
                "testQueryWithMapping",
                QueryParams.single(QueryParams.NAME, pokemon.getName()))
                .asJsonArray();
        LogData.logJsonArray(Level.DEBUG, data);
        assertThat(data.size(), equalTo(1));
        VerifyData.verifyPokemon(data.getJsonObject(0), pokemon);
    }

    /**
     * Verify get of PoJo instance as a result using mapping.
     */
    @Test
    public void testGetWithMapping() {
        final Pokemon pokemon = Pokemon.POKEMONS.get(2);
        JsonObject data = testClient.callServiceAndGetData(
                "testGetWithMapping",
                QueryParams.single(QueryParams.NAME, pokemon.getName())
        ).asJsonObject();
        LogData.logJsonObject(Level.DEBUG, data);
        VerifyData.verifyPokemon(data, pokemon);
    }

    private void executeInsertTest(String testName, int id) {
        JsonObject data = testClient.callServiceAndGetData(
                        testName,
                        QueryParams.single(QueryParams.ID, String.valueOf(id)))
                .asJsonObject();
        LogData.logJsonObject(Level.DEBUG, data);
        JsonObject pokemonData = VerifyData.getPokemon(testClient, id);
        LogData.logJsonObject(Level.DEBUG, pokemonData);
        VerifyData.verifyPokemon(pokemonData, data);
    }

    private void executeUpdateTest(String testName, int id, String newName) {
        Pokemon pokemon = Pokemon.POKEMONS.get(id);
        Pokemon updatedPokemon = new Pokemon(pokemon.getId(), newName, pokemon.getTypes());
        JsonValue data = testClient.callServiceAndGetData(
                testName,
                QueryParams.builder()
                        .add(QueryParams.NAME, newName)
                        .add(QueryParams.ID, String.valueOf(id))
                        .build());
        Long count = JsonTools.getLong(data);
        LOGGER.log(Level.DEBUG, () -> String.format("Rows updated: %d", count));
        JsonObject pokemonData = VerifyData.getPokemon(testClient, pokemon.getId());
        LogData.logJsonObject(Level.DEBUG, pokemonData);
        assertThat(count, equalTo(1L));
        VerifyData.verifyPokemon(pokemonData, updatedPokemon);
    }

    private void executeDeleteTest(String testName, int id) {
        JsonValue data = testClient.callServiceAndGetData(
                        testName,
                        QueryParams.single(QueryParams.ID, String.valueOf(id)));
        Long count = JsonTools.getLong(data);
        LOGGER.log(Level.DEBUG, () -> String.format("Rows deleted: %d", count));
        JsonObject pokemonData = VerifyData.getPokemon(testClient, id);
        LogData.logJsonObject(Level.DEBUG, pokemonData);
        assertThat(count, equalTo(1L));
        assertThat(pokemonData.isEmpty(), equalTo(true));
    }

}
