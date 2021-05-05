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

import javax.json.JsonArray;

import io.helidon.tests.integration.dbclient.appl.it.LogData;
import io.helidon.tests.integration.dbclient.appl.it.VerifyData;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.dbclient.appl.tools.QueryParams;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test set of basic DbClient queries in transaction.
 */
public class TransactionQueriesIT {

    /** Local logger instance. */
    static final Logger LOGGER = Logger.getLogger(TransactionQueriesIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("TransactionQueries")
            .build();

    // Test executor method
    private void executeTest(final String testName, final Pokemon pokemon) {
        LOGGER.fine(() -> String.format("Running %s", testName));
        JsonArray data = testClient.callServiceAndGetData(
                testName,
                QueryParams.single(QueryParams.NAME, pokemon.getName()))
                .asJsonArray();
        LogData.logJsonArray(Level.FINER, data);
        assertThat(data.size(), Matchers.equalTo(1));
        VerifyData.verifyPokemon(data.getJsonObject(0), pokemon);
    }

    /**
     * Verify {@code createNamedQuery(String, String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedQueryStrStrOrderArgs() {
        executeTest("testCreateNamedQueryStrStrOrderArgs", Pokemon.POKEMONS.get(1));
    }

    /**
     * Verify {@code createNamedQuery(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedQueryStrNamedArgs() {
        executeTest("testCreateNamedQueryStrNamedArgs", Pokemon.POKEMONS.get(2));
    }

    /**
     * Verify {@code createNamedQuery(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedQueryStrOrderArgs() {
        executeTest("testCreateNamedQueryStrOrderArgs", Pokemon.POKEMONS.get(3));
    }

    /**
     * Verify {@code createQuery(String)} API method with named parameters.
     */
    @Test
    public void testCreateQueryNamedArgs() {
        executeTest("testCreateQueryNamedArgs", Pokemon.POKEMONS.get(4));
    }

    /**
     * Verify {@code createQuery(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateQueryOrderArgs() {
        executeTest("testCreateQueryOrderArgs", Pokemon.POKEMONS.get(5));
    }

    /**
     * Verify {@code namedQuery(String)} API method with ordered parameters passed directly to the {@code namedQuery} method.
     */
    @Test
    public void testNamedQueryOrderArgs() {
        executeTest("testNamedQueryOrderArgs", Pokemon.POKEMONS.get(6));
    }

    /**
     * Verify {@code query(String)} API method with ordered parameters passed directly to the {@code query} method.
     */
    @Test
    public void testQueryOrderArgs() {
        executeTest("testQueryOrderArgs", Pokemon.POKEMONS.get(7));
    }

}
