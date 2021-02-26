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

package io.helidon.dbclient.health;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;

import io.helidon.common.reactive.Subscribable;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbTransaction;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Test database health check functionality.
 */
public class DbClientHealthCheckTest {

    private static final Logger LOGGER = Logger.getLogger(DbClientHealthCheckTest.class.getName());

    private static final class HealthCheckTestException extends RuntimeException {

        private HealthCheckTestException(final String message) {
            super(message);
        }

        private HealthCheckTestException(final String message, final Throwable t) {
            super(message, t);
        }

    }

    // DbClient mock
    private static final class DbClientMock implements DbClient {

        @Override
        public <U, T extends Subscribable<U>> T inTransaction(Function<DbTransaction, T> executor) {
            throw new UnsupportedOperationException("Not supported in tests.");
        }

        @Override
        public <U, T extends Subscribable<U>> T execute(Function<DbExecute, T> executor) {
            throw new UnsupportedOperationException("Not supported in tests.");
        }

        @Override
        public String dbType() {
            return "mysql";
        }

    }

    /**
     * Test health check builder with custom DML statement
     */
    @Test
    void testBuilderDmlFromConfig() {

        final Config config = Config.create(ConfigSources.classpath("testBuilderFromConfig.yaml"));

        final DbClient dbClient = new DbClientMock();

        final Config healthConfig = config.get("health-check-dml");
        final DbClientHealthCheck.Builder builder = DbClientHealthCheck
                .builder(dbClient)
                .config(healthConfig);

        // Config file value
        final String configName = healthConfig.get("name").as(String.class).get();
        final String configStatement = healthConfig.get("statement").as(String.class).get();
        final Long configTimeout = healthConfig.get("timeout").as(Long.class).get();
        final String configTimeUnit = healthConfig.get("timeUnit").as(String.class).get();

        // Builder instance content
        final String name = builder.name();
        final boolean isDML = builder.isDML();
        final String statement = builder.statement();
        final String statementName = builder.statementName();
        final boolean isNamedstatement = builder.isNamedstatement();
        final long timeoutDuration = builder.timeoutDuration();
        final TimeUnit timeoutUnit = builder.timeoutUnit();

        // Verify builder internal content
        assertThat(name, equalTo(configName));
        assertThat(isDML, equalTo(true));
        assertThat(statement, equalTo(configStatement));
        assertThat(statementName, nullValue());
        assertThat(isNamedstatement, equalTo(false));
        assertThat(timeoutDuration, equalTo(configTimeout));
        assertThat(timeoutUnit.name().toLowerCase(), equalTo(configTimeUnit.toLowerCase()));

        DbClientHealthCheck check = builder.build();

        // Health check instance content
        final String checkName = check.name();
        final long checkTimeoutDuration = check.timeoutDuration();
        final TimeUnit checkTimeoutUnit = check.timeoutUnit();

        // Verify health check class and internal content
        assertThat(check.getClass().getSimpleName(), equalTo("DbClientHealthCheckAsDml"));
        assertThat(checkName, equalTo(configName));
        assertThat(((DbClientHealthCheck.DbClientHealthCheckAsDml)check).statement(), equalTo(configStatement));
        assertThat(checkTimeoutDuration, equalTo(configTimeout));
        assertThat(checkTimeoutUnit.name().toLowerCase(), equalTo(configTimeUnit.toLowerCase()));


    }

    /**
     * Test health check builder with named DML statement
     */
    @Test
    void testBuilderNamedDmlFromConfig() {

        final Config config = Config.create(ConfigSources.classpath("testBuilderFromConfig.yaml"));

        final DbClient dbClient = new DbClientMock();

        final Config healthConfig = config.get("health-check-dml-named");
        final DbClientHealthCheck.Builder builder = DbClientHealthCheck
                .builder(dbClient)
                .config(healthConfig);

        // Config file value
        final String configName = healthConfig.get("name").as(String.class).get();
        final String configType = healthConfig.get("type").as(String.class).get();
        final String configStatementName = healthConfig.get("statementName").as(String.class).get();
        final Long configTimeout = healthConfig.get("timeout").as(Long.class).get();
        final String configTimeUnit = healthConfig.get("timeUnit").as(String.class).get();

        // Builder instance content
        final String name = builder.name();
        final boolean isDML = builder.isDML();
        final String statement = builder.statement();
        final String statementName = builder.statementName();
        final boolean isNamedstatement = builder.isNamedstatement();
        final long timeoutDuration = builder.timeoutDuration();
        final TimeUnit timeoutUnit = builder.timeoutUnit();

        // Verify builder internal state
        assertThat(name, equalTo(configName));
        assertThat(isDML, equalTo(true));
        assertThat(statement, nullValue());
        assertThat(statementName, equalTo(configStatementName));
        assertThat(isNamedstatement, equalTo(true));
        assertThat(timeoutDuration, equalTo(configTimeout));
        assertThat(timeoutUnit.name().toLowerCase(), equalTo(configTimeUnit.toLowerCase()));

        DbClientHealthCheck check = builder.build();
        assertThat(check.getClass().getSimpleName(), equalTo("DbClientHealthCheckAsNamedDml"));


        // Health check instance content
        final String checkName = check.name();
        final long checkTimeoutDuration = check.timeoutDuration();
        final TimeUnit checkTimeoutUnit = check.timeoutUnit();

        // Verify health check class and internal content
        assertThat(check.getClass().getSimpleName(), equalTo("DbClientHealthCheckAsNamedDml"));
        assertThat(checkName, equalTo(configName));
        assertThat(((DbClientHealthCheck.DbClientHealthCheckAsNamedDml)check).statementName(), equalTo(configStatementName));
        assertThat(checkTimeoutDuration, equalTo(configTimeout));
        assertThat(checkTimeoutUnit.name().toLowerCase(), equalTo(configTimeUnit.toLowerCase()));

    }


    /**
     * Test health check builder with custom query statement
     */
    @Test
    void testBuilderQueryFromConfig() {

        final Config config = Config.create(ConfigSources.classpath("testBuilderFromConfig.yaml"));

        final DbClient dbClient = new DbClientMock();

        final Config healthConfig = config.get("health-check-query");
        final DbClientHealthCheck.Builder builder = DbClientHealthCheck
                .builder(dbClient)
                .config(healthConfig);

        // Config file value
        final String configName = healthConfig.get("name").as(String.class).get();
        final String configType = healthConfig.get("type").as(String.class).get();
        final String configStatement = healthConfig.get("statement").as(String.class).get();
        //final String configStatementName = healthConfig.get("statementName").as(String.class).get();
        final Long configTimeout = healthConfig.get("timeout").as(Long.class).get();
        final String configTimeUnit = healthConfig.get("timeUnit").as(String.class).get();

        // Builder instance content
        final String name = builder.name();
        final boolean isDML = builder.isDML();
        final String statement = builder.statement();
        final String statementName = builder.statementName();
        final boolean isNamedstatement = builder.isNamedstatement();
        final long timeoutDuration = builder.timeoutDuration();
        final TimeUnit timeoutUnit = builder.timeoutUnit();

        // Verify builder internal state
        assertThat(name, equalTo(configName));
        assertThat(isDML, equalTo(false));
        assertThat(statement, equalTo(configStatement));
        assertThat(statementName, nullValue());
        assertThat(isNamedstatement, equalTo(false));
        assertThat(timeoutDuration, equalTo(configTimeout));
        assertThat(timeoutUnit.name().toLowerCase(), equalTo(configTimeUnit.toLowerCase()));

        DbClientHealthCheck check = builder.build();

        // Health check instance content
        final String checkName = check.name();
        final long checkTimeoutDuration = check.timeoutDuration();
        final TimeUnit checkTimeoutUnit = check.timeoutUnit();

        // Verify health check class and internal content
        assertThat(check.getClass().getSimpleName(), equalTo("DbClientHealthCheckAsQuery"));
        assertThat(checkName, equalTo(configName));
        assertThat(((DbClientHealthCheck.DbClientHealthCheckAsQuery)check).statement(), equalTo(configStatement));
        assertThat(checkTimeoutDuration, equalTo(configTimeout));
        assertThat(checkTimeoutUnit.name().toLowerCase(), equalTo(configTimeUnit.toLowerCase()));

    }

    /**
     * Test health check builder with named query statement
     */
    @Test
    void testBuilderNamedQueryFromConfig() {

        final Config config = Config.create(ConfigSources.classpath("testBuilderFromConfig.yaml"));

        final DbClient dbClient = new DbClientMock();

        final Config healthConfig = config.get("health-check-query-named");
        final DbClientHealthCheck.Builder builder = DbClientHealthCheck
                .builder(dbClient)
                .config(healthConfig);

        // Config file value
        final String configName = healthConfig.get("name").as(String.class).get();
        final String configType = healthConfig.get("type").as(String.class).get();
        //final String configStatement = healthConfig.get("statement").as(String.class).get();
        final String configStatementName = healthConfig.get("statementName").as(String.class).get();
        final Long configTimeout = healthConfig.get("timeout").as(Long.class).get();
        final String configTimeUnit = healthConfig.get("timeUnit").as(String.class).get();

        // Builder instance content
        final String name = builder.name();
        final boolean isDML = builder.isDML();
        final String statement = builder.statement();
        final String statementName = builder.statementName();
        final boolean isNamedstatement = builder.isNamedstatement();
        final long timeoutDuration = builder.timeoutDuration();
        final TimeUnit timeoutUnit = builder.timeoutUnit();

        // Verify builder internal state
        assertThat(name, equalTo(configName));
        assertThat(isDML, equalTo(false));
        assertThat(statement, nullValue());
        assertThat(statementName, equalTo(configStatementName));
        assertThat(isNamedstatement, equalTo(true));
        assertThat(timeoutDuration, equalTo(configTimeout));
        assertThat(timeoutUnit.name().toLowerCase(), equalTo(configTimeUnit.toLowerCase()));

        DbClientHealthCheck check = builder.build();

        // Health check instance content
        final String checkName = check.name();
        final long checkTimeoutDuration = check.timeoutDuration();
        final TimeUnit checkTimeoutUnit = check.timeoutUnit();

        // Verify health check class and internal content
        assertThat(check.getClass().getSimpleName(), equalTo("DbClientHealthCheckAsNamedQuery"));
        assertThat(checkName, equalTo(configName));
        assertThat(((DbClientHealthCheck.DbClientHealthCheckAsNamedQuery)check).statementName(), equalTo(configStatementName));
        assertThat(checkTimeoutDuration, equalTo(configTimeout));
        assertThat(checkTimeoutUnit.name().toLowerCase(), equalTo(configTimeUnit.toLowerCase()));

    }

}
