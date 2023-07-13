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
package io.helidon.tests.integration.dbclient.app.it.simple;

import java.lang.System.Logger.Level;

import io.helidon.common.LazyValue;
import io.helidon.tests.integration.dbclient.app.it.LogData;
import io.helidon.tests.integration.dbclient.app.it.VerifyData;
import io.helidon.tests.integration.dbclient.app.tools.QueryParams;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

/**
 * Test simple insert statements.
 */
public class SimpleInsertIT {

    private static final System.Logger LOGGER = System.getLogger(SimpleInsertIT.class.getName());

    private final LazyValue<TestServiceClient> testClient = LazyValue.create(() ->
            TestClient.builder()
                    .port(HelidonProcessRunner.HTTP_PORT)
                    .service("SimpleInsert")
                    .build());

    private void executeTest(String testName, int id) {
        LOGGER.log(Level.DEBUG, () -> String.format("Running SimpleInsertIT.%s on client", testName));
        JsonObject data = testClient.get().callServiceAndGetData(
                        testName,
                        QueryParams.single(QueryParams.ID, String.valueOf(id)))
                .asJsonObject();
        LogData.logJsonObject(Level.DEBUG, data);
        JsonObject pokemonData = VerifyData.getPokemon(testClient.get(), id);
        LogData.logJsonObject(Level.DEBUG, pokemonData);
        VerifyData.verifyPokemon(pokemonData, data);
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

    // DML insert

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with insert with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithInsertStrStrNamedArgs() {
        executeTest("testCreateNamedDmlWithInsertStrStrNamedArgs", 43);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with insert with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithInsertStrNamedArgs() {
        executeTest("testCreateNamedDmlWithInsertStrNamedArgs", 44);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with insert with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithInsertStrOrderArgs() {
        executeTest("testCreateNamedDmlWithInsertStrOrderArgs", 45);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with insert with named parameters.
     */
    @Test
    public void testCreateDmlWithInsertNamedArgs() {
        executeTest("testCreateDmlWithInsertNamedArgs", 46);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with insert with ordered parameters.
     */
    @Test
    public void testCreateDmlWithInsertOrderArgs() {
        executeTest("testCreateDmlWithInsertOrderArgs", 47);
    }

    /**
     * Verify {@code namedDml(String)} API method with insert with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testNamedDmlWithInsertOrderArgs() {
        executeTest("testNamedDmlWithInsertOrderArgs", 48);
    }

    /**
     * Verify {@code dml(String)} API method with insert with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testDmlWithInsertOrderArgs() {
        executeTest("testDmlWithInsertOrderArgs", 49);
    }

}
