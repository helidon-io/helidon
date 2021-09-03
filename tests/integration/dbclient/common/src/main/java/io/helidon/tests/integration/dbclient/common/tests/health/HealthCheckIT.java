/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common.tests.health;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.dbclient.health.DbClientHealthCheck;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.CONFIG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Verify that health check works.
 */
public class HealthCheckIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(HealthCheckIT.class.getName());

    private static boolean pingDml = true;

    @BeforeAll
    public static void setup() {
        Config cfgPingDml = CONFIG.get("test.ping-dml");
        pingDml = cfgPingDml.exists() ? cfgPingDml.asBoolean().get() : true;
    }

    /**
     * Verify health check implementation with default settings.
     */
    @Test
    public void testHealthCheck() {
        LOGGER.log(Level.INFO, "Running test testHealthCheck");
        HealthCheck check = DbClientHealthCheck.create(DB_CLIENT, CONFIG.get("db.health-check"));
        HealthCheckResponse response = check.call();
        HealthCheckResponse.State state = response.getState();
        assertThat("Healthcheck failed, response: " + response.getData(), state, equalTo(HealthCheckResponse.State.UP));
    }

    /**
     * Verify health check implementation with builder and custom name.
     */
    @Test
    public void testHealthCheckWithName() {
        LOGGER.log(Level.INFO, "Running test testHealthCheckWithName");
        final String hcName = "TestHC";
        HealthCheck check = DbClientHealthCheck.builder(DB_CLIENT).config(CONFIG.get("db.health-check")).name(hcName).build();
        HealthCheckResponse response = check.call();
        String name = response.getName();
        HealthCheckResponse.State state = response.getState();
        assertThat(name, equalTo(hcName));
        assertThat(state, equalTo(HealthCheckResponse.State.UP));
    }

    /**
     * Verify health check implementation using custom DML named statement.
     */
    @Test
    public void testHealthCheckWithCustomNamedDML() {
        LOGGER.log(Level.INFO, "Running test testHealthCheckWithCustomNamedDML");
        if (!pingDml) {
            LOGGER.log(Level.INFO, () -> String.format("Database %s does not support DML ping, skipping this test", DB_CLIENT.dbType()));
            return;
        }
        HealthCheck check = DbClientHealthCheck.builder(DB_CLIENT).dml().statementName("ping-dml").build();
        HealthCheckResponse response = check.call();
        HealthCheckResponse.State state = response.getState();
        assertThat("Healthcheck failed, response: " + response.getData(), state, equalTo(HealthCheckResponse.State.UP));
    }

    /**
     * Verify health check implementation using custom DML statement.
     */
    @Test
    public void testHealthCheckWithCustomDML() {
        LOGGER.log(Level.INFO, "Running test testHealthCheckWithCustomDML");
        if (!pingDml) {
            LOGGER.log(Level.INFO, () -> String.format("Database %s does not support DML ping, skipping this test", DB_CLIENT.dbType()));
            return;
        }
        Config cfgStatement = CONFIG.get("db.statements.ping-dml");
        assertThat("Missing ping-dml statement in database configuration!", cfgStatement.exists(), equalTo(true));
        String statement = cfgStatement.asString().get();
        assertThat("Missing ping-dml statement String in database configuration!", statement, is(notNullValue()));
        LOGGER.log(Level.INFO, () -> String.format("Using db.statements.ping-dml value %s", statement));
        HealthCheck check = DbClientHealthCheck.builder(DB_CLIENT).dml().statement(statement).build();
        HealthCheckResponse response = check.call();
        HealthCheckResponse.State state = response.getState();
        assertThat("Healthcheck failed, response: " + response.getData(), state, equalTo(HealthCheckResponse.State.UP));
    }

    /**
     * Verify health check implementation using custom query named statement.
     */
    @Test
    public void testHealthCheckWithCustomNamedQuery() {
        LOGGER.log(Level.INFO, "Running test testHealthCheckWithCustomNamedQuery");
        HealthCheck check = DbClientHealthCheck.builder(DB_CLIENT).query().statementName("ping-query").build();
        HealthCheckResponse response = check.call();
        HealthCheckResponse.State state = response.getState();
        assertThat("Healthcheck failed, response: " + response.getData(), state, equalTo(HealthCheckResponse.State.UP));
    }

    /**
     * Verify health check implementation using custom query statement.
     */
    @Test
    public void testHealthCheckWithCustomQuery() {
        LOGGER.log(Level.INFO, "Running test testHealthCheckWithCustomQuery");
        Config cfgStatement = CONFIG.get("db.statements.ping-query");
        assertThat("Missing ping-query statement in database configuration!", cfgStatement.exists(), equalTo(true));
        String statement = cfgStatement.asString().get();
        assertThat("Missing ping-query statement String in database configuration!", statement, is(notNullValue()));
        LOGGER.log(Level.INFO, () -> String.format("Using db.statements.ping-query value %s", statement));
        HealthCheck check = DbClientHealthCheck.builder(DB_CLIENT).query().statement(statement).build();
        HealthCheckResponse response = check.call();
        HealthCheckResponse.State state = response.getState();
        assertThat("Healthcheck failed, response: " + response.getData(), state, equalTo(HealthCheckResponse.State.UP));
    }


}
