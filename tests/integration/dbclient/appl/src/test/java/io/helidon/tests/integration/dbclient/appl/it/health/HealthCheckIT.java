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
package io.helidon.tests.integration.dbclient.appl.it.health;

import java.lang.System.Logger.Level;

import io.helidon.health.HealthCheckResponse;
import io.helidon.tests.integration.dbclient.appl.it.LogData;
import io.helidon.tests.integration.dbclient.appl.tools.QueryParams;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import jakarta.json.JsonObject;
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

    private static final System.Logger LOGGER = System.getLogger(HealthCheckIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("HealthCheck")
            .build();

    private String dbType = null;
    private boolean pingDml = true;

    @BeforeAll
    public void setup() {
        JsonObject dbTypeValue = testClient
                .callServiceAndGetData("Verify", "getDatabaseType", null)
                .asJsonObject();
        LogData.logJsonObject(Level.DEBUG, dbTypeValue);
        dbType = dbTypeValue.getString("type");
        JsonObject pingDmlValue = testClient
                .callServiceAndGetData(
                        "Verify", "getConfigParam",
                        QueryParams.single(QueryParams.NAME, "test.ping-dml"))
                .asJsonObject();
        LogData.logJsonObject(Level.DEBUG, pingDmlValue);
        if (pingDmlValue.containsKey("config")) {
            pingDml = Boolean.parseBoolean(pingDmlValue.getString("config"));
        }
    }

    /**
     * Verify health check implementation with default settings.
     */
    @Test
    public void testHealthCheck() {
        JsonObject data = testClient
                .callServiceAndGetData("testHealthCheck")
                .asJsonObject();
        LogData.logJsonObject(Level.DEBUG, data);
        assertThat(data.getString("status"), equalTo(HealthCheckResponse.Status.UP.name()));
    }

    /**
     * Verify health check implementation with builder and custom name.
     */
    @Test
    public void testHealthCheckWithName() {
        final String hcName = "TestHC";
        JsonObject data = testClient
                .callServiceAndGetData(
                        "testHealthCheckWithName",
                        QueryParams.single(QueryParams.NAME, hcName))
                .asJsonObject();
        LogData.logJsonObject(Level.DEBUG, data);
        assertThat(data.getString("status"), equalTo(HealthCheckResponse.Status.UP.name()));
        assertThat(data.getString("name"), equalTo(hcName));
    }

    /**
     * Verify health check implementation using custom DML named statement.
     */
    @Test
    public void testHealthCheckWithCustomNamedDML() {
        if (!pingDml) {
            LOGGER.log(Level.INFO, () -> String.format("Database %s does not support DML ping, skipping this test", dbType));
            return;
        }
        JsonObject data = testClient
                .callServiceAndGetData("testHealthCheckWithCustomNamedDML")
                .asJsonObject();
        LogData.logJsonObject(Level.DEBUG, data);
        assertThat(data.getString("status"), equalTo(HealthCheckResponse.Status.UP.name()));
    }

    /**
     * Verify health check implementation using custom DML statement.
     */
    @Test
    public void testHealthCheckWithCustomDML() {
        if (!pingDml) {
            LOGGER.log(Level.DEBUG, () -> String.format("Database %s does not support DML ping, skipping this test", dbType));
            return;
        }
        JsonObject data = testClient
                .callServiceAndGetData("testHealthCheckWithCustomDML")
                .asJsonObject();
        LogData.logJsonObject(Level.DEBUG, data);
        assertThat(data.getString("status"), equalTo(HealthCheckResponse.Status.UP.name()));
    }

    /**
     * Verify health check implementation using custom query named statement.
     */
    @Test
    public void testHealthCheckWithCustomNamedQuery() {
        JsonObject data = testClient
                .callServiceAndGetData("testHealthCheckWithCustomNamedQuery")
                .asJsonObject();
        LogData.logJsonObject(Level.DEBUG, data);
        assertThat(data.getString("status"), equalTo(HealthCheckResponse.Status.UP.name()));
    }

    /**
     * Verify health check implementation using custom query statement.
     */
    @Test
    public void testHealthCheckWithCustomQuery() {
        JsonObject data = testClient
                .callServiceAndGetData("testHealthCheckWithCustomQuery")
                .asJsonObject();
        LogData.logJsonObject(Level.DEBUG, data);
        assertThat(data.getString("status"), equalTo(HealthCheckResponse.Status.UP.name()));
    }


}
