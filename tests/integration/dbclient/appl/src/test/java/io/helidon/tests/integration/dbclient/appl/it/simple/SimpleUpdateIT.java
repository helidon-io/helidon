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
package io.helidon.tests.integration.dbclient.appl.it.simple;

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
 * Test set of basic DbClient updates.
 */
public class SimpleUpdateIT {

    private static final Logger LOGGER = Logger.getLogger(SimpleQueriesIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("SimpleUpdate")
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
            LOGGER.fine(() -> String.format("Rows updated: %d", count));
            JsonObject pokemonData = VerifyData.getPokemon(testClient, pokemon.getId());
            LogData.logJsonObject(Level.FINER, pokemonData);
            assertThat(count, equalTo(1L));
            VerifyData.verifyPokemon(pokemonData, updatedPokemon);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e, () -> String.format("Exception in %s: %s", testName, e.getMessage()));
        }
    }

    /**
     * Verify {@code createNamedUpdate(String, String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedUpdateStrStrNamedArgs() {
        executeTest("testCreateNamedUpdateStrStrNamedArgs", 8, "Fearow");
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedUpdateStrNamedArgs() {
        executeTest("testCreateNamedUpdateStrNamedArgs", 9, "Spearow");
    }

    /**
     * Verify {@code createNamedUpdate(String, String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedUpdateStrOrderArgs() {
        executeTest("testCreateNamedUpdateStrOrderArgs", 10, "Arbok");
    }

    /**
     * Verify {@code createUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateUpdateNamedArgs() {
        executeTest("testCreateUpdateNamedArgs", 11, "Ekans");
    }


    /**
     * Verify {@code createUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateUpdateOrderArgs() {
        executeTest("testCreateUpdateOrderArgs", 12, "Diglett");
    }

    /**
     * Verify {@code namedUpdate(String, String)} API method with ordered parameters.
     */
    @Test
    public void testNamedUpdateNamedArgs() {
        executeTest("testNamedUpdateNamedArgs", 13, "Sandshrew");
    }

    /**
     * Verify {@code update(String)} API method with named parameters.
     */
    @Test
    public void testUpdateOrderArgs() {
        executeTest("testUpdateOrderArgs", 14, "Sandslash");
    }

    // DML update

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with update with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrStrNamedArgs() {
        executeTest("testCreateNamedDmlWithUpdateStrStrNamedArgs", 29, "Prinplup");
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrNamedArgs() {
        executeTest("testCreateNamedDmlWithUpdateStrNamedArgs", 30, "Empoleon");
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrOrderArgs() {
        executeTest("testCreateNamedDmlWithUpdateStrOrderArgs", 31, "Piplup");
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    public void testCreateDmlWithUpdateNamedArgs() {
        executeTest("testCreateDmlWithUpdateNamedArgs", 32, "Starmie");
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    public void testCreateDmlWithUpdateOrderArgs() {
        executeTest("testCreateDmlWithUpdateOrderArgs", 33, "Staryu");
    }

    /**
     * Verify {@code namedDml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testNamedDmlWithUpdateOrderArgs() {
        executeTest("testNamedDmlWithUpdateOrderArgs", 34, "Seadra");
    }

    /**
     * Verify {@code dml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testDmlWithUpdateOrderArgs() {
        executeTest("testDmlWithUpdateOrderArgs", 35, "Horsea");
    }

}
