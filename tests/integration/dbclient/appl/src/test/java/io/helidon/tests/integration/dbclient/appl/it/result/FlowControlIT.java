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
package io.helidon.tests.integration.dbclient.appl.it.result;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonObject;

import io.helidon.tests.integration.dbclient.appl.it.LogData;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Verify proper flow control handling in query processing.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FlowControlIT {

    private static final Logger LOGGER = Logger.getLogger(FlowControlIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("FlowControl")
            .build();

    // Test executor method
    private void executeTest(final String testName) {
        LOGGER.fine(() -> String.format("Running %s", testName));
        try {
            JsonObject data = testClient
                    .callServiceAndGetData(testName)
                    .asJsonObject();
            LogData.logJsonObject(Level.FINER, data);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e, () -> String.format("Exception in %s: %s", testName, e.getMessage()));
        }
    }

    /**
     * Source data verification test.
     */
    @Test
    @Order(1)
    public void testSourceData() {
        executeTest("testSourceData");
    }

    /**
     * Flow control test.
     */
    @Test
    @Order(2)
    public void testFlowControl() {
        executeTest("testFlowControl");
    }



}
