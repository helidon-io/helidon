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

/**
 * Test simple get statements.
 */
class SimpleGetIT {

    private static final System.Logger LOGGER = System.getLogger(SimpleGetIT.class.getName());

    private final TestServiceClient testClient;

    SimpleGetIT(int serverPort) {
        this.testClient = TestClient.builder()
                .port(serverPort)
                .service("SimpleGet")
                .build();
    }

    void executeTest(String testName, Pokemon pokemon) {
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on client", getClass().getSimpleName(), testName));
        JsonObject data = testClient
                .callServiceAndGetData(
                        testName,
                        QueryParams.single(QueryParams.NAME, pokemon.getName()))
                .asJsonObject();
        LogData.logJsonObject(Level.DEBUG, data);
        VerifyData.verifyPokemon(data, pokemon);
    }

    /**
     * Verify {@code createNamedGet(String, String)} API method with named
     * parameters.
     */
    @Test
    void testCreateNamedGetStrStrNamedArgs() {
        executeTest("testCreateNamedGetStrStrNamedArgs", Pokemon.POKEMONS.get(1));
    }

    /**
     * Verify {@code createNamedGet(String)} API method with named parameters.
     */
    @Test
    void testCreateNamedGetStrNamedArgs() {
        executeTest("testCreateNamedGetStrNamedArgs", Pokemon.POKEMONS.get(2));
    }

    /**
     * Verify {@code createNamedGet(String)} API method with ordered parameters.
     */
    @Test
    void testCreateNamedGetStrOrderArgs() {
        executeTest("testCreateNamedGetStrOrderArgs", Pokemon.POKEMONS.get(3));
    }

    /**
     * Verify {@code createGet(String)} API method with named parameters.
     */
    @Test
    void testCreateGetNamedArgs() {
        executeTest("testCreateGetNamedArgs", Pokemon.POKEMONS.get(4));
    }

    /**
     * Verify {@code createGet(String)} API method with ordered parameters.
     */
    @Test
    void testCreateGetOrderArgs() {
        executeTest("testCreateGetOrderArgs", Pokemon.POKEMONS.get(5));
    }

    /**
     * Verify {@code namedGet(String)} API method with ordered parameters passed
     * directly to the {@code query} method.
     */
    @Test
    void testNamedGetStrOrderArgs() {
        executeTest("testNamedGetStrOrderArgs", Pokemon.POKEMONS.get(6));
    }

    /**
     * Verify {@code get(String)} API method with ordered parameters passed
     * directly to the {@code query} method.
     */
    @Test
    void testGetStrOrderArgs() {
        executeTest("testGetStrOrderArgs", Pokemon.POKEMONS.get(7));
    }

}
