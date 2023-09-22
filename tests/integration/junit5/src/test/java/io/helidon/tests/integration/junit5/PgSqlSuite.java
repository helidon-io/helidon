/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.junit5;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.junit5.spi.SuiteProvider;

@DbClientTest
@TestConfig(file = "pgsql.yaml")
@ContainerTest(provider  = PgSqlContainer.class, image = "postgres:16.0")
public class PgSqlSuite implements SuiteProvider {

    private static final Logger LOGGER = System.getLogger(PgSqlSuite.class.getName());

    static final String BEFORE_KEY = "PgSqlSuite.before";
    static final String AFTER_KEY = "PgSqlSuite.after";
    static final String SETUP_CONFIG_KEY = "PgSqlSuite.config";
    static final String SETUP_CONTAINER_KEY = "PgSqlSuite.container";
    static final String SETUP_DBCLIENT_KEY = "PgSqlSuite.dbClient";

    static final String DB_NODE = "db";

    private SuiteContext suiteContext;
    private int counter;

    public PgSqlSuite() {
        suiteContext = null;
        counter = 1;
    }

    // Store shared suite context when passed from suite initialization
    @Override
    public void suiteContext(SuiteContext suiteContext) {
        this.suiteContext = suiteContext;
    }

    @SetUpConfig
    public void setupConfig(Config.Builder builder) {
        LOGGER.log(Level.TRACE,
                   () -> String.format("Running setupConfig of PgSqlSuite test class, order %d", counter));
        // Modify target Config content with additional node
        builder.addSource(ConfigSources.create(Map.of("id", "TEST")));
        suiteContext.storage().put(SETUP_CONFIG_KEY, counter++);
    }

    @SetUpContainer
    public void setupContainer(Config config, SuiteContext context, DatabaseContainerConfig.Builder dbConfigBuilder, ContainerConfig.Builder builder) {
        LOGGER.log(Level.TRACE,
                   () -> String.format("Running setupContainer of PgSqlSuite test class, order %d", counter));
        config.get(DB_NODE + ".connection").asNode().ifPresent(dbConfigBuilder::dbClient);
        // Modify target ContainerConfig content with additional system variable
        builder.environment().put("MY_VARIABLE", "myValue");
        suiteContext.storage().put(SETUP_CONTAINER_KEY, counter++);
    }

    @SetUpDbClient
    public void setupDbClient(Config config, SuiteContext context, ContainerInfo info, DbClient.Builder builder) {
        LOGGER.log(Level.TRACE,
                   () -> String.format("Running setupDbClient of PgSqlSuite test class, order %d", counter));
        Map<String, String> updatedNodes = new HashMap<>(1);
        config.get(DB_NODE + ".connection.url").as(String.class).ifPresent(value -> updatedNodes.put(
                DB_NODE + ".connection.url",
                DatabaseContainer.replacePortInUrl(
                        value,
                        info.portMappings().get(info.config().exposedPorts()[0]))));
        Config updatedConfig = Config.create(ConfigSources.create(updatedNodes), ConfigSources.create(config));
        builder.config(updatedConfig.get("db"));
        // Validate order of provider's methods execution
        suiteContext.storage().put(SETUP_DBCLIENT_KEY, counter++);
    }

    // Initialize database schema and data
    // Validate that @BeforeSuite is executed
    @BeforeSuite
    public void beforeSuite(DbClient dbClient) {
        LOGGER.log(Level.TRACE,
                   () -> String.format("Running beforeSuite of PgSqlSuite test class, order %d", counter));
        // Database initialization goes here
        // Validate order of provider's methods execution
        suiteContext.storage().put(BEFORE_KEY, counter++);
    }

    // Validate that @AfterSuite is executed
    @AfterSuite
    public void afterSuite() {
        LOGGER.log(Level.TRACE,
                   () -> String.format("Running afterSuite of PgSqlSuite test class, order %d", counter));
        suiteContext.storage().put(AFTER_KEY, counter);
    }

}
