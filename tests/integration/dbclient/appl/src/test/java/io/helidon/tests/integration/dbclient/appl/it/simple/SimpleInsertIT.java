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
import io.helidon.tests.integration.dbclient.appl.tools.QueryParams;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import org.junit.jupiter.api.Test;

/**
 * Test set of basic DbClient inserts.
 */
public class SimpleInsertIT {

    private static final Logger LOGGER = Logger.getLogger(SimpleQueriesIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("SimpleInsert")
            .build();

    // Test executor method
    private void executeTest(final String testName, final int id) {
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

    /**
     * Verify {@code createNamedInsert(String, String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedInsertStrStrNamedArgs() {
        executeTest("testCreateNamedInsertStrStrNamedArgs", 22);
    }

    /**
     * Verify {@code createNamedInsert(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedInsertStrNamedArgs() {
        executeTest("testCreateNamedInsertStrNamedArgs", 23);
    }

    /**
     * Verify {@code createNamedInsert(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedInsertStrOrderArgs() {
        executeTest("testCreateNamedInsertStrOrderArgs", 24);
    }

    /**
     * Verify {@code createInsert(String)} API method with named parameters.
     */
    @Test
    public void testCreateInsertNamedArgs() {
        executeTest("testCreateInsertNamedArgs", 25);
    }

    /**
     * Verify {@code createInsert(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateInsertOrderArgs() {
       executeTest("testCreateInsertOrderArgs", 26);
    }

    /**
     * Verify {@code namedInsert(String)} API method with ordered parameters passed directly to the {@code insert} method.
      */
    @Test
    public void testNamedInsertOrderArgs() {
        executeTest("testNamedInsertOrderArgs", 27);
    }

    /**
     * Verify {@code insert(String)} API method with ordered parameters passed directly to the {@code insert} method.
     */
    @Test
    public void testInsertOrderArgs() {
        executeTest("testInsertOrderArgs", 28);
    }

    // DML update

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with update with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrStrNamedArgs() {
        executeTest("testCreateNamedDmlWithUpdateStrStrNamedArgs", 43);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrNamedArgs() {
        executeTest("testCreateNamedDmlWithUpdateStrNamedArgs", 44);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrOrderArgs() {
        executeTest("testCreateNamedDmlWithUpdateStrOrderArgs", 45);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    public void testCreateDmlWithUpdateNamedArgs() {
        executeTest("testCreateDmlWithUpdateNamedArgs", 46);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    public void testCreateDmlWithUpdateOrderArgs() {
        executeTest("testCreateDmlWithUpdateOrderArgs", 47);
    }

    /**
     * Verify {@code namedDml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testNamedDmlWithUpdateOrderArgs() {
        executeTest("testNamedDmlWithUpdateOrderArgs", 48);
    }

    /**
     * Verify {@code dml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testDmlWithUpdateOrderArgs() {
        executeTest("testDmlWithUpdateOrderArgs", 49);
    }

}
