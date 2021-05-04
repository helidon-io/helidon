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
import io.helidon.tests.integration.dbclient.appl.tools.QueryParams;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test set of basic DbClient deletes.
 */
public class SimpleDeleteIT {

    private static final Logger LOGGER = Logger.getLogger(SimpleQueriesIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("SimpleDelete")
            .build();

    // Test executor method
    private void executeTest(final String testName, final int id) {
        LOGGER.fine(() -> String.format("Running %s", testName));
        try {
            JsonValue data = testClient.callServiceAndGetData(
                    testName,
                    QueryParams.single(QueryParams.ID, String.valueOf(id)));
            Long count = JsonTools.getLong(data);
            LOGGER.fine(() -> String.format("Rows deleted: %d", count));
            JsonObject pokemonData = VerifyData.getPokemon(testClient, id);
            LogData.logJsonObject(Level.FINER, pokemonData);
            assertThat(count, equalTo(1L));
            assertThat(pokemonData.isEmpty(), equalTo(true));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e, () -> String.format("Exception in %s: %s", testName, e.getMessage()));
        }
    }

    /**
     * Verify {@code createNamedDelete(String, String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedDeleteStrStrOrderArgs() {
        executeTest("testCreateNamedDeleteStrStrOrderArgs", 15);
    }

    /**
     * Verify {@code createNamedDelete(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedDeleteStrNamedArgs() {
        executeTest("testCreateNamedDeleteStrNamedArgs", 16);
    }

    /**
     * Verify {@code createNamedDelete(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedDeleteStrOrderArgs() {
        executeTest("testCreateNamedDeleteStrOrderArgs", 17);
    }

    /**
     * Verify {@code createDelete(String)} API method with named parameters.
     */
    @Test
    public void testCreateDeleteNamedArgs() {
        executeTest("testCreateDeleteNamedArgs", 18);
    }

    /**
     * Verify {@code createDelete(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateDeleteOrderArgs() {
        executeTest("testCreateDeleteOrderArgs", 19);
    }

    /**
     * Verify {@code namedDelete(String)} API method with ordered parameters.
     */
    @Test
    public void testNamedDeleteOrderArgs() {
        executeTest("testNamedDeleteOrderArgs", 20);
    }

    /**
     * Verify {@code delete(String)} API method with ordered parameters.
     */
    @Test
    public void testDeleteOrderArgs() {
        executeTest("testDeleteOrderArgs", 21);
    }

    // DML delete

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with delete with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithDeleteStrStrOrderArgs() {
        executeTest("testCreateNamedDmlWithDeleteStrStrOrderArgs", 36);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with delete with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithDeleteStrNamedArgs() {
        executeTest("testCreateNamedDmlWithDeleteStrNamedArgs", 37);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithDeleteStrOrderArgs() {
        executeTest("testCreateNamedDmlWithDeleteStrOrderArgs", 38);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with delete with named parameters.
     */
    @Test
    public void testCreateDmlWithDeleteNamedArgs() {
        executeTest("testCreateDmlWithDeleteNamedArgs", 39);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testCreateDmlWithDeleteOrderArgs() {
        executeTest("testCreateDmlWithDeleteOrderArgs", 40);
    }

    /**
     * Verify {@code namedDml(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testNamedDmlWithDeleteOrderArgs() {
        executeTest("testNamedDmlWithDeleteOrderArgs", 41);
    }

    /**
     * Verify {@code dml(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testDmlWithDeleteOrderArgs() {
        executeTest("testDmlWithDeleteOrderArgs", 42);
    }

}
