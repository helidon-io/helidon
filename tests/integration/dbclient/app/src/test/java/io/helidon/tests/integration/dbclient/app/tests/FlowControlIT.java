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
import io.helidon.tests.integration.harness.TestClient;
import io.helidon.tests.integration.harness.TestServiceClient;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Verify proper flow control handling in query processing.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowControlIT {

    private static final System.Logger LOGGER = System.getLogger(FlowControlIT.class.getName());

    private final TestServiceClient testClient;

    FlowControlIT(int serverPort) {
        this.testClient = TestClient.builder()
                .port(serverPort)
                .service("FlowControl")
                .build();
    }

    private void executeTest(String testName) {
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on client", getClass().getSimpleName(), testName));
        JsonObject data = testClient
                .callServiceAndGetData(testName)
                .asJsonObject();
        LogData.logJsonObject(Level.DEBUG, data);
    }

    /**
     * Source data verification test.
     */
    @Test
    @Order(1)
    void testSourceData() {
        executeTest("testSourceData");
    }

    /**
     * Flow control test.
     */
    @Test
    @Order(2)
    void testFlowControl() {
        executeTest("testFlowControl");
    }
}
