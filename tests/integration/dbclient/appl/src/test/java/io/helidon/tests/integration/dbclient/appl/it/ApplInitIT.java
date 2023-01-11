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
package io.helidon.tests.integration.dbclient.appl.it;

import java.lang.System.Logger.Level;

import io.helidon.tests.integration.dbclient.appl.it.tools.JsonTools;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.HelidonTestException;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Initialize DB Client Integration Tests.
 */
@TestMethodOrder(OrderAnnotation.class)
public class ApplInitIT {

    private static final System.Logger LOGGER = System.getLogger(ApplInitIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("Init")
            .build();

    // Test executor methods

    private void executeTest(String testName) {
        JsonObject data = testClient
                .callServiceAndGetData(testName)
                .asJsonObject();
        LogData.logJsonObject(Level.DEBUG, data);
    }

    private void executeInit(String testName) {
        JsonValue data = testClient
                .callServiceAndGetData(testName);
        Long count = JsonTools.getLong(data);
        LOGGER.log(Level.DEBUG, () -> String.format("Rows modified: %d", count));
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

    // Called from LifeCycleExtension in setup phase
    public void dropSchema() {
        try {
            executeInit("testDropSchema");
        // This remote call will fail on fresh database without existing tables.
        } catch (HelidonTestException ex){
            LOGGER.log(Level.INFO, "Remote database tables did not exist.");
        }
    }

    // Called from LifeCycleExtension in setup phase
    public void initSchema() {
        executeInit("testInitSchema");
    }

    // Called from LifeCycleExtension in setup phase
    public void initTypes() {
        executeInit("testInitTypes");
    }

    // Called from LifeCycleExtension in setup phase
    public void initPokemons() {
        executeInit("testInitPokemons");
    }

    // Called from LifeCycleExtension in setup phase
    public void initPokemonTypes() {
        executeInit("testInitPokemonTypes");
    }

}
