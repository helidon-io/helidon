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
 * Test simple update statements.
 */
class SimpleUpdateIT {

    private static final System.Logger LOGGER = System.getLogger(SimpleUpdateIT.class.getName());

    private final TestServiceClient testClient;

    SimpleUpdateIT(int serverPort) {
        this.testClient = TestClient.builder()
                .port(serverPort)
                .service("SimpleUpdate")
                .build();
    }

    private void executeTest(String testName, int id, String newName) {
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on client", getClass().getSimpleName(), testName));
        Pokemon pokemon = Pokemon.POKEMONS.get(id);
        Pokemon updatedPokemon = new Pokemon(pokemon.getId(), newName, pokemon.getTypes());
        JsonValue data = testClient.callServiceAndGetData(
                testName,
                QueryParams.builder()
                        .add(QueryParams.NAME, newName)
                        .add(QueryParams.ID, String.valueOf(id))
                        .build());
        Long count = JsonValues.asLong(data);
        LOGGER.log(Level.DEBUG, () -> String.format("Rows updated: %d", count));
        JsonObject pokemonData = VerifyData.getPokemon(testClient, pokemon.getId());
        LogData.logJsonObject(Level.DEBUG, pokemonData);
        assertThat(count, equalTo(1L));
        VerifyData.verifyPokemon(pokemonData, updatedPokemon);
    }

    /**
     * Verify {@code createNamedUpdate(String, String)} API method with ordered parameters.
     */
    @Test
    void testCreateNamedUpdateStrStrNamedArgs() {
        executeTest("testCreateNamedUpdateStrStrNamedArgs", 8, "Fearow");
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with named parameters.
     */
    @Test
    void testCreateNamedUpdateStrNamedArgs() {
        executeTest("testCreateNamedUpdateStrNamedArgs", 9, "Spearow");
    }

    /**
     * Verify {@code createNamedUpdate(String, String)} API method with ordered parameters.
     */
    @Test
    void testCreateNamedUpdateStrOrderArgs() {
        executeTest("testCreateNamedUpdateStrOrderArgs", 10, "Arbok");
    }

    /**
     * Verify {@code createUpdate(String)} API method with named parameters.
     */
    @Test
    void testCreateUpdateNamedArgs() {
        executeTest("testCreateUpdateNamedArgs", 11, "Ekans");
    }


    /**
     * Verify {@code createUpdate(String)} API method with named parameters.
     */
    @Test
    void testCreateUpdateOrderArgs() {
        executeTest("testCreateUpdateOrderArgs", 12, "Diglett");
    }

    /**
     * Verify {@code namedUpdate(String, String)} API method with ordered parameters.
     */
    @Test
    void testNamedUpdateNamedArgs() {
        executeTest("testNamedUpdateNamedArgs", 13, "Sandshrew");
    }

    /**
     * Verify {@code update(String)} API method with named parameters.
     */
    @Test
    void testUpdateOrderArgs() {
        executeTest("testUpdateOrderArgs", 14, "Sandslash");
    }

    // DML update

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with update with named parameters.
     */
    @Test
    void testCreateNamedDmlWithUpdateStrStrNamedArgs() {
        executeTest("testCreateNamedDmlWithUpdateStrStrNamedArgs", 29, "Prinplup");
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    void testCreateNamedDmlWithUpdateStrNamedArgs() {
        executeTest("testCreateNamedDmlWithUpdateStrNamedArgs", 30, "Empoleon");
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    void testCreateNamedDmlWithUpdateStrOrderArgs() {
        executeTest("testCreateNamedDmlWithUpdateStrOrderArgs", 31, "Piplup");
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    void testCreateDmlWithUpdateNamedArgs() {
        executeTest("testCreateDmlWithUpdateNamedArgs", 32, "Starmie");
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    void testCreateDmlWithUpdateOrderArgs() {
        executeTest("testCreateDmlWithUpdateOrderArgs", 33, "Staryu");
    }

    /**
     * Verify {@code namedDml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    void testNamedDmlWithUpdateOrderArgs() {
        executeTest("testNamedDmlWithUpdateOrderArgs", 34, "Seadra");
    }

    /**
     * Verify {@code dml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    void testDmlWithUpdateOrderArgs() {
        executeTest("testDmlWithUpdateOrderArgs", 35, "Horsea");
    }

}
