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
package io.helidon.tests.integration.dbclient.appl.it;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonObject;
import javax.json.JsonValue;

import io.helidon.tests.integration.dbclient.appl.it.tools.JsonTools;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.HelidonTestException;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Initialize DB Client Integration Tests.
 */
@TestMethodOrder(OrderAnnotation.class)
public class ApplInitIT {

    private static final Logger LOGGER = Logger.getLogger(ApplInitIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("Init")
            .build();

    // Test executor methods

    private void executeTest(final String testName) {
        LOGGER.fine(() -> String.format("Running %s", testName));
        JsonObject data = testClient
                .callServiceAndGetData(testName)
                .asJsonObject();
        LogData.logJsonObject(Level.FINER, data);
    }

    private void executeInit(final String testName) {
        LOGGER.fine(() -> String.format("Running %s", testName));
        JsonValue data = testClient
                .callServiceAndGetData(testName);
        Long count = JsonTools.getLong(data);
        LOGGER.finer(() -> String.format("Rows modified: %d", count));
    }

    @Test
    @Order(1)
    public void setup() {
        executeTest("setup");
    }

    @Test
    @Order(2)
    public void testHealthCheck() {
        executeTest("testPing");
    }

    @Test
    @Order(3)
    public void testDropSchema() {
        try {
            executeInit("testDropSchema");
        // This remote call will fail on fresh database without existing tables.
        } catch (HelidonTestException ex){
            LOGGER.info(() -> "Remote database tables did not exist.");
        }
    }

    @Test
    @Order(4)
    public void testInitSchema() {
        executeInit("testInitSchema");
    }

    @Test
    @Order(5)
    public void testInitTypes() {
        executeInit("testInitTypes");
    }

    @Test
    @Order(6)
    public void testInitPokemons() {
        executeInit("testInitPokemons");
    }

    @Test
    @Order(7)
    public void testInitPokemonTypes() {
        executeInit("testInitPokemonTypes");
    }

}
