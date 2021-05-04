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
package io.helidon.tests.integration.dbclient.appl.it.transaction;

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
 * Test set of basic DbClient updates in transaction.
 */
public class TransactionUpdateIT {

    private static final Logger LOGGER = Logger.getLogger(TransactionUpdateIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("TransactionUpdate")
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
        executeTest("testCreateNamedUpdateStrStrNamedArgs", 57, "Ursaring");
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedUpdateStrNamedArgs() {
        executeTest("testCreateNamedUpdateStrNamedArgs", 58, "Teddiursa");
    }

    /**
     * Verify {@code createNamedUpdate(String, String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedUpdateStrOrderArgs() {
        executeTest("testCreateNamedUpdateStrOrderArgs", 59, "Magcargo");
    }

    /**
     * Verify {@code createUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateUpdateNamedArgs() {
        executeTest("testCreateUpdateNamedArgs", 60, "Slugma");
    }


    /**
     * Verify {@code createUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateUpdateOrderArgs() {
        executeTest("testCreateUpdateOrderArgs", 61, "Lombre");
    }

    /**
     * Verify {@code namedUpdate(String, String)} API method with ordered parameters.
     */
    @Test
    public void testNamedUpdateNamedArgs() {
        executeTest("testNamedUpdateNamedArgs", 62, "Ludicolo");
    }

    /**
     * Verify {@code update(String)} API method with named parameters.
     */
    @Test
    public void testUpdateOrderArgs() {
        executeTest("testUpdateOrderArgs", 63, "Lotad");
    }

    // DML update

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with update with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrStrNamedArgs() {
        executeTest("testCreateNamedDmlWithUpdateStrStrNamedArgs", 64, "Xatu");
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrNamedArgs() {
        executeTest("testCreateNamedDmlWithUpdateStrNamedArgs", 65, "Natu");
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrOrderArgs() {
        executeTest("testCreateNamedDmlWithUpdateStrOrderArgs", 66, "Granbull");
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    public void testCreateDmlWithUpdateNamedArgs() {
        executeTest("testCreateDmlWithUpdateNamedArgs", 67, "Snubbull");
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    public void testCreateDmlWithUpdateOrderArgs() {
        executeTest("testCreateDmlWithUpdateOrderArgs", 68, "Raikou");
    }

    /**
     * Verify {@code namedDml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testNamedDmlWithUpdateOrderArgs() {
        executeTest("testNamedDmlWithUpdateOrderArgs", 69, "Suicune");
    }

    /**
     * Verify {@code dml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testDmlWithUpdateOrderArgs() {
        executeTest("testDmlWithUpdateOrderArgs", 70, "Entei");
    }
}
