/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common.tests;

import java.lang.System.Logger.Level;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Verify that health check works.
 */
@ExtendWith(DbClientParameterResolver.class)
public class HealthCheckIT {

    private static final System.Logger LOGGER = System.getLogger(HealthCheckIT.class.getName());

    private final DbClient dbClient;
    private final Config config;
    private final boolean pingDml;

    HealthCheckIT(DbClient dbClient, Config config) {
        this.dbClient = dbClient;
        this.config = config;
        Config cfgPingDml = config.get("test.ping-dml");
        this.pingDml = cfgPingDml.exists() ? cfgPingDml.asBoolean().get() : true;
    }

    /**
     * Verify health check implementation with default settings.
     */
    @Test
    public void testHealthCheck() {
        HealthCheck check = DbClientHealthCheck.create(dbClient, config.get("db.health-check"));
        HealthCheckResponse response = check.call();
        HealthCheckResponse.Status state = response.status();
        assertThat("Health check failed, response: " + response.details(), state, equalTo(HealthCheckResponse.Status.UP));
    }

    /**
     * Verify health check implementation with builder and custom name.
     */
    @Test
    public void testHealthCheckWithName() {
        String hcName = "TestHC";
        HealthCheck check = DbClientHealthCheck.builder(dbClient).config(config.get("db.health-check")).name(hcName).build();
        HealthCheckResponse response = check.call();
        String name = check.name();
        HealthCheckResponse.Status state = response.status();
        assertThat(name, equalTo(hcName));
        assertThat(state, equalTo(HealthCheckResponse.Status.UP));
    }

    /**
     * Verify health check implementation using custom DML named statement.
     */
    @Test
    public void testHealthCheckWithCustomNamedDML() {
        if (!pingDml) {
            LOGGER.log(Level.DEBUG, () -> String.format("Database %s does not support DML ping, skipping this test", dbClient.dbType()));
            return;
        }
        HealthCheck check = DbClientHealthCheck.builder(dbClient).dml().statementName("ping-dml").build();
        HealthCheckResponse response = check.call();
        HealthCheckResponse.Status state = response.status();
        assertThat("Health check failed, response: " + response.details(), state, equalTo(HealthCheckResponse.Status.UP));
    }

    /**
     * Verify health check implementation using custom DML statement.
     */
    @Test
    public void testHealthCheckWithCustomDML() {
        if (!pingDml) {
            LOGGER.log(Level.DEBUG, () -> String.format("Database %s does not support DML ping, skipping this test", dbClient.dbType()));
            return;
        }
        Config cfgStatement = config.get("db.statements.ping-dml");
        assertThat("Missing ping-dml statement in database configuration!", cfgStatement.exists(), equalTo(true));
        String statement = cfgStatement.asString().get();
        assertThat("Missing ping-dml statement String in database configuration!", statement, is(notNullValue()));
        LOGGER.log(Level.DEBUG, () -> String.format("Using db.statements.ping-dml value %s", statement));
        HealthCheck check = DbClientHealthCheck.builder(dbClient).dml().statement(statement).build();
        HealthCheckResponse response = check.call();
        HealthCheckResponse.Status state = response.status();
        assertThat("Health check failed, response: " + response.details(), state, equalTo(HealthCheckResponse.Status.UP));
    }

    /**
     * Verify health check implementation using custom query named statement.
     */
    @Test
    public void testHealthCheckWithCustomNamedQuery() {
        HealthCheck check = DbClientHealthCheck.builder(dbClient).query().statementName("ping-query").build();
        HealthCheckResponse response = check.call();
        HealthCheckResponse.Status state = response.status();
        assertThat("Health check failed, response: " + response.details(), state, equalTo(HealthCheckResponse.Status.UP));
    }

    /**
     * Verify health check implementation using custom query statement.
     */
    @Test
    public void testHealthCheckWithCustomQuery() {
        Config cfgStatement = config.get("db.statements.ping-query");
        assertThat("Missing ping-query statement in database configuration!", cfgStatement.exists(), equalTo(true));
        String statement = cfgStatement.asString().get();
        assertThat("Missing ping-query statement String in database configuration!", statement, is(notNullValue()));
        LOGGER.log(Level.DEBUG, () -> String.format("Using db.statements.ping-query value %s", statement));
        HealthCheck check = DbClientHealthCheck.builder(dbClient).query().statement(statement).build();
        HealthCheckResponse response = check.call();
        HealthCheckResponse.Status state = response.status();
        assertThat("Health check failed, response: " + response.details(), state, equalTo(HealthCheckResponse.Status.UP));
    }

}
