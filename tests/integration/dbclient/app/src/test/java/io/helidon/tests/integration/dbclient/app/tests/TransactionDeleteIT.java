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
 * Test simple delete statements in transaction.
 */
class TransactionDeleteIT {

    private static final System.Logger LOGGER = System.getLogger(TransactionDeleteIT.class.getName());

    private final TestServiceClient testClient;

    TransactionDeleteIT(int serverPort) {
        this.testClient = TestClient.builder()
                .port(serverPort)
                .service("TransactionDelete")
                .build();
    }

    private void executeTest(String testName, int id) {
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on client", getClass().getSimpleName(), testName));
        JsonValue data = testClient
                .callServiceAndGetData(
                        testName,
                        QueryParams.single(QueryParams.ID, String.valueOf(id)));
        Long count = JsonValues.asLong(data);
        LOGGER.log(Level.DEBUG, () -> String.format("Rows deleted: %d", count));
        JsonObject pokemonData = VerifyData.getPokemon(testClient, id);
        LogData.logJsonObject(Level.DEBUG, pokemonData);
        assertThat(count, equalTo(1L));
        assertThat(pokemonData.isEmpty(), equalTo(true));
    }

    /**
     * Verify {@code createNamedDelete(String, String)} API method with ordered parameters.
     */
    @Test
    void testCreateNamedDeleteStrStrOrderArgs() {
        executeTest("testCreateNamedDeleteStrStrOrderArgs", 71);
    }

    /**
     * Verify {@code createNamedDelete(String)} API method with named parameters.
     */
    @Test
    void testCreateNamedDeleteStrNamedArgs() {
        executeTest("testCreateNamedDeleteStrNamedArgs", 72);
    }

    /**
     * Verify {@code createNamedDelete(String)} API method with ordered parameters.
     */
    @Test
    void testCreateNamedDeleteStrOrderArgs() {
        executeTest("testCreateNamedDeleteStrOrderArgs", 73);
    }

    /**
     * Verify {@code createDelete(String)} API method with named parameters.
     */
    @Test
    void testCreateDeleteNamedArgs() {
        executeTest("testCreateDeleteNamedArgs", 74);
    }

    /**
     * Verify {@code createDelete(String)} API method with ordered parameters.
     */
    @Test
    void testCreateDeleteOrderArgs() {
        executeTest("testCreateDeleteOrderArgs", 75);
    }

    /**
     * Verify {@code namedDelete(String)} API method with ordered parameters.
     */
    @Test
    void testNamedDeleteOrderArgs() {
        executeTest("testNamedDeleteOrderArgs", 76);
    }

    /**
     * Verify {@code delete(String)} API method with ordered parameters.
     */
    @Test
    void testDeleteOrderArgs() {
        executeTest("testDeleteOrderArgs", 77);
    }

    // DML delete

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with delete with ordered parameters.
     */
    @Test
    void testCreateNamedDmlWithDeleteStrStrOrderArgs() {
        executeTest("testCreateNamedDmlWithDeleteStrStrOrderArgs", 78);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with delete with named parameters.
     */
    @Test
    void testCreateNamedDmlWithDeleteStrNamedArgs() {
        executeTest("testCreateNamedDmlWithDeleteStrNamedArgs", 79);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with delete with ordered parameters.
     */
    @Test
    void testCreateNamedDmlWithDeleteStrOrderArgs() {
        executeTest("testCreateNamedDmlWithDeleteStrOrderArgs", 80);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with delete with named parameters.
     */
    @Test
    void testCreateDmlWithDeleteNamedArgs() {
        executeTest("testCreateDmlWithDeleteNamedArgs", 81);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with delete with ordered parameters.
     */
    @Test
    void testCreateDmlWithDeleteOrderArgs() {
        executeTest("testCreateDmlWithDeleteOrderArgs", 82);
    }

    /**
     * Verify {@code namedDml(String)} API method with delete with ordered parameters.
     */
    @Test
    void testNamedDmlWithDeleteOrderArgs() {
        executeTest("testNamedDmlWithDeleteOrderArgs", 83);
    }

    /**
     * Verify {@code dml(String)} API method with delete with ordered parameters.
     */
    @Test
    void testDmlWithDeleteOrderArgs() {
        executeTest("testDmlWithDeleteOrderArgs", 84);
    }

}
