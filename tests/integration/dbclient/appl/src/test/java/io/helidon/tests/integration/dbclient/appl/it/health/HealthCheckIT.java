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
package io.helidon.tests.integration.dbclient.appl.it.health;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonObject;

import io.helidon.tests.integration.dbclient.appl.it.LogData;
import io.helidon.tests.integration.dbclient.appl.tools.QueryParams;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
/**
 * Verify that health check works.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HealthCheckIT {

    private static final Logger LOGGER = Logger.getLogger(HealthCheckIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("HealthCheck")
            .build();

    private String dbType = null;
    private boolean pingDml = true;

    @BeforeAll
    public void setup() {
        LOGGER.info(() -> "Running HealthCheckIT setup");
        final JsonObject dbTypeValue = testClient
                .callServiceAndGetData("Verify", "getDatabaseType", null)
                .asJsonObject();
        LogData.logJsonObject(Level.FINER, dbTypeValue);
        dbType = dbTypeValue.getString("type");
        final JsonObject pingDmlValue = testClient
                .callServiceAndGetData(
                        "Verify", "getConfigParam",
                        QueryParams.single(QueryParams.NAME, "test.ping-dml"))
                .asJsonObject();
        LogData.logJsonObject(Level.FINER, pingDmlValue);
        if (pingDmlValue.containsKey("config")) {
            pingDml = Boolean.valueOf(pingDmlValue.getString("config"));
        }
    }

    /**
     * Verify health check implementation with default settings.
     */
    @Test
    public void testHealthCheck() {
        LOGGER.info(() -> "Running test testHealthCheck");
        JsonObject data = testClient
                .callServiceAndGetData("testHealthCheck")
                .asJsonObject();
        LogData.logJsonObject(Level.FINER, data);
        assertThat(data.getString("status"), equalTo(HealthCheckResponse.State.UP.name()));
    }

    /**
     * Verify health check implementation with builder and custom name.
     */
    @Test
    public void testHealthCheckWithName() {
        LOGGER.info(() -> "Running test testHealthCheckWithName");
        final String hcName = "TestHC";
        JsonObject data = testClient
                .callServiceAndGetData(
                        "testHealthCheckWithName",
                        QueryParams.single(QueryParams.NAME, hcName))
                .asJsonObject();
        LogData.logJsonObject(Level.FINER, data);
        assertThat(data.getString("status"), equalTo(HealthCheckResponse.State.UP.name()));
        assertThat(data.getString("name"), equalTo(hcName));
    }

    /**
     * Verify health check implementation using custom DML named statement.
     */
    @Test
    public void testHealthCheckWithCustomNamedDML() {
        LOGGER.info(() -> "Running test testHealthCheckWithCustomNamedDML");
        if (!pingDml) {
            LOGGER.info(() -> String.format("Database %s does not support DML ping, skipping this test", dbType));
            return;
        }
        JsonObject data = testClient
                .callServiceAndGetData("testHealthCheckWithCustomNamedDML")
                .asJsonObject();
        LogData.logJsonObject(Level.FINER, data);
        assertThat(data.getString("status"), equalTo(HealthCheckResponse.State.UP.name()));
    }

    /**
     * Verify health check implementation using custom DML statement.
     */
    @Test
    public void testHealthCheckWithCustomDML() {
        LOGGER.info(() -> "Running test testHealthCheckWithCustomDML");
        if (!pingDml) {
            LOGGER.info(() -> String.format("Database %s does not support DML ping, skipping this test", dbType));
            return;
        }
        JsonObject data = testClient
                .callServiceAndGetData("testHealthCheckWithCustomDML")
                .asJsonObject();
        LogData.logJsonObject(Level.FINER, data);
        assertThat(data.getString("status"), equalTo(HealthCheckResponse.State.UP.name()));
    }

    /**
     * Verify health check implementation using custom query named statement.
     */
    @Test
    public void testHealthCheckWithCustomNamedQuery() {
        LOGGER.info(() -> "Running test testHealthCheckWithCustomNamedQuery");
        JsonObject data = testClient
                .callServiceAndGetData("testHealthCheckWithCustomNamedQuery")
                .asJsonObject();
        LogData.logJsonObject(Level.FINER, data);
        assertThat(data.getString("status"), equalTo(HealthCheckResponse.State.UP.name()));
    }

    /**
     * Verify health check implementation using custom query statement.
     */
    @Test
    public void testHealthCheckWithCustomQuery() {
        LOGGER.info(() -> "Running test testHealthCheckWithCustomQuery");
        JsonObject data = testClient
                .callServiceAndGetData("testHealthCheckWithCustomQuery")
                .asJsonObject();
        LogData.logJsonObject(Level.FINER, data);
        assertThat(data.getString("status"), equalTo(HealthCheckResponse.State.UP.name()));
    }


}
