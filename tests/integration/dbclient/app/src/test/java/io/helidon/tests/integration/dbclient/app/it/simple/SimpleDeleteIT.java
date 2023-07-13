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
import io.helidon.tests.integration.dbclient.app.it.tools.JsonTools;
import io.helidon.tests.integration.dbclient.app.tools.QueryParams;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test simple delete statements.
 */
public class SimpleDeleteIT {

    private static final System.Logger LOGGER = System.getLogger(SimpleDeleteIT.class.getName());

    private final LazyValue<TestServiceClient> testClient = LazyValue.create(() -> TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("SimpleDelete")
            .build());

    private void executeTest(String testName, int id) {
        LOGGER.log(Level.DEBUG, () -> String.format("Running SimpleDeleteIT.%s on client", testName));
        JsonValue data = testClient.get()
                .callServiceAndGetData(
                        testName,
                        QueryParams.single(QueryParams.ID, String.valueOf(id)));
        Long count = JsonTools.getLong(data);
        LOGGER.log(Level.DEBUG, () -> String.format("Rows deleted: %d", count));
        JsonObject pokemonData = VerifyData.getPokemon(testClient.get(), id);
        LogData.logJsonObject(Level.DEBUG, pokemonData);
        assertThat(count, equalTo(1L));
        assertThat(pokemonData.isEmpty(), equalTo(true));
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
