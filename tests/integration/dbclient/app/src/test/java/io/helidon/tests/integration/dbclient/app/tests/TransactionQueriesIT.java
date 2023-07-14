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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test simple query statements in transaction.
 */
class TransactionQueriesIT {

    /**
     * Local logger instance.
     */
    static final System.Logger LOGGER = System.getLogger(TransactionQueriesIT.class.getName());

    private final TestServiceClient testClient;

    TransactionQueriesIT(int serverPort) {
        this.testClient = TestClient.builder()
                .port(serverPort)
                .service("TransactionQueries")
                .build();
    }

    private void executeTest(String testName, Pokemon pokemon) {
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on client", getClass().getSimpleName(), testName));
        JsonArray data = testClient
                .callServiceAndGetData(
                        testName,
                        QueryParams.single(QueryParams.NAME, pokemon.getName()))
                .asJsonArray();
        LogData.logJsonArray(Level.DEBUG, data);
        assertThat(data.size(), Matchers.equalTo(1));
        VerifyData.verifyPokemon(data.getJsonObject(0), pokemon);
    }

    /**
     * Verify {@code createNamedQuery(String, String)} API method with ordered parameters.
     */
    @Test
    void testCreateNamedQueryStrStrOrderArgs() {
        executeTest("testCreateNamedQueryStrStrOrderArgs", Pokemon.POKEMONS.get(1));
    }

    /**
     * Verify {@code createNamedQuery(String)} API method with named parameters.
     */
    @Test
    void testCreateNamedQueryStrNamedArgs() {
        executeTest("testCreateNamedQueryStrNamedArgs", Pokemon.POKEMONS.get(2));
    }

    /**
     * Verify {@code createNamedQuery(String)} API method with ordered parameters.
     */
    @Test
    void testCreateNamedQueryStrOrderArgs() {
        executeTest("testCreateNamedQueryStrOrderArgs", Pokemon.POKEMONS.get(3));
    }

    /**
     * Verify {@code createQuery(String)} API method with named parameters.
     */
    @Test
    void testCreateQueryNamedArgs() {
        executeTest("testCreateQueryNamedArgs", Pokemon.POKEMONS.get(4));
    }

    /**
     * Verify {@code createQuery(String)} API method with ordered parameters.
     */
    @Test
    void testCreateQueryOrderArgs() {
        executeTest("testCreateQueryOrderArgs", Pokemon.POKEMONS.get(5));
    }

    /**
     * Verify {@code namedQuery(String)} API method with ordered parameters passed directly to the {@code namedQuery} method.
     */
    @Test
    void testNamedQueryOrderArgs() {
        executeTest("testNamedQueryOrderArgs", Pokemon.POKEMONS.get(6));
    }

    /**
     * Verify {@code query(String)} API method with ordered parameters passed directly to the {@code query} method.
     */
    @Test
    void testQueryOrderArgs() {
        executeTest("testQueryOrderArgs", Pokemon.POKEMONS.get(7));
    }

}
