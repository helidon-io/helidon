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
package io.helidon.tests.integration.dbclient.appl.it.simple;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonObject;

import io.helidon.tests.integration.dbclient.appl.it.LogData;
import io.helidon.tests.integration.dbclient.appl.it.VerifyData;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.dbclient.appl.tools.QueryParams;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import org.junit.jupiter.api.Test;

/**
 * Test set of basic DbClient gets.
 */
public class SimpleGetIT {

    private static final Logger LOGGER = Logger.getLogger(SimpleGetIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("SimpleGet")
            .build();

    // Test executor method
    public void executeTest(final String testName, final Pokemon pokemon) {
        LOGGER.fine(() -> String.format("Running %s", testName));
        JsonObject data = testClient.callServiceAndGetData(
                testName,
                QueryParams.single(QueryParams.NAME, pokemon.getName())
        ).asJsonObject();
        LogData.logJsonObject(Level.FINER, data);
        VerifyData.verifyPokemon(data, pokemon);
    }

    /**
     * Verify {@code createNamedGet(String, String)} API method with named
     * parameters.
     */
    @Test
    public void testCreateNamedGetStrStrNamedArgs() {
        executeTest("testCreateNamedGetStrStrNamedArgs", Pokemon.POKEMONS.get(1));
    }

    /**
     * Verify {@code createNamedGet(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedGetStrNamedArgs() {
        executeTest("testCreateNamedGetStrNamedArgs", Pokemon.POKEMONS.get(2));
    }

    /**
     * Verify {@code createNamedGet(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedGetStrOrderArgs() {
        executeTest("testCreateNamedGetStrOrderArgs", Pokemon.POKEMONS.get(3));
    }

    /**
     * Verify {@code createGet(String)} API method with named parameters.
     */
    @Test
    public void testCreateGetNamedArgs() {
        executeTest("testCreateGetNamedArgs", Pokemon.POKEMONS.get(4));
    }

    /**
     * Verify {@code createGet(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateGetOrderArgs() {
        executeTest("testCreateGetOrderArgs", Pokemon.POKEMONS.get(5));
    }

    /**
     * Verify {@code namedGet(String)} API method with ordered parameters passed
     * directly to the {@code query} method.
     */
    @Test
    public void testNamedGetStrOrderArgs() {
        executeTest("testNamedGetStrOrderArgs", Pokemon.POKEMONS.get(6));
    }

    /**
     * Verify {@code get(String)} API method with ordered parameters passed
     * directly to the {@code query} method.
     */
    @Test
    public void testGetStrOrderArgs() {
        executeTest("testGetStrOrderArgs", Pokemon.POKEMONS.get(7));
    }

}
