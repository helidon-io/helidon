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
package io.helidon.tests.integration.dbclient.app;

import java.lang.System.Logger.Level;

import io.helidon.tests.integration.harness.HelidonTestException;
import io.helidon.tests.integration.harness.JsonValues;
import io.helidon.tests.integration.harness.TestClient;
import io.helidon.tests.integration.harness.TestServiceClient;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Database initializer.
 */
class DbInit {

    private static final System.Logger LOGGER = System.getLogger(DbInit.class.getName());

    private final TestServiceClient testClient;

    /**
     * Create a new instance
     *
     * @param port port
     */
    DbInit(int port) {
        this.testClient = TestClient.builder()
                .port(port)
                .service("Init")
                .build();
    }

    /**
     * Invoke {@code /Init/testPing}.
     */
    public void testPing() {
        executeTest("testPing");
    }

    /**
     * Invoke {@code /Init/testDropSchema}.
     */
    public void dropSchema() {
        try {
            executeInit("testDropSchema");
        } catch (HelidonTestException ex) {
            LOGGER.log(Level.INFO, "Remote database tables did not exist.");
        }
    }

    /**
     * Invoke {@code /Init/testHealthCheck}.
     */
    public void testHealthCheck() {
        executeTest("testHealthCheck");
    }

    /**
     * Invoke {@code /Init/testInitSchema}.
     */
    public void initSchema() {
        executeInit("testInitSchema");
    }

    /**
     * Invoke {@code /Init/testInitTypes}.
     */
    public void initTypes() {
        executeInit("testInitTypes");
    }

    /**
     * Invoke {@code /Init/testInitPokemons}.
     */
    public void initPokemons() {
        executeInit("testInitPokemons");
    }

    /**
     * Invoke {@code /Init/testInitPokemonTypes}.
     */
    public void initPokemonTypes() {
        executeInit("testInitPokemonTypes");
    }

    private void executeTest(String testName) {
        JsonObject data = testClient
                .callServiceAndGetData(testName)
                .asJsonObject();
        LogData.logJsonObject(Level.DEBUG, data);
    }

    private void executeInit(String testName) {
        JsonValue data = testClient
                .callServiceAndGetData(testName);
        Long count = JsonValues.asLong(data);
        LOGGER.log(Level.DEBUG, () -> String.format("Rows modified: %d", count));
    }
}
