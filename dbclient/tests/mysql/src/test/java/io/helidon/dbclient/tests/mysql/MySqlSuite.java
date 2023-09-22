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
package io.helidon.dbclient.tests.mysql;

import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.metrics.DbClientMetrics;
import io.helidon.dbclient.tests.common.utils.InitUtils;
import io.helidon.tests.integration.junit5.AfterSuite;
import io.helidon.tests.integration.junit5.BeforeSuite;
import io.helidon.tests.integration.junit5.ConfigUpdate;
import io.helidon.tests.integration.junit5.ContainerInfo;
import io.helidon.tests.integration.junit5.ContainerTest;
import io.helidon.tests.integration.junit5.DatabaseContainer;
import io.helidon.tests.integration.junit5.DatabaseContainerConfig;
import io.helidon.tests.integration.junit5.DbClientTest;
import io.helidon.tests.integration.junit5.MySqlContainer;
import io.helidon.tests.integration.junit5.SetUpContainer;
import io.helidon.tests.integration.junit5.SetUpDbClient;
import io.helidon.tests.integration.junit5.Suite;
import io.helidon.tests.integration.junit5.TestConfig;
import io.helidon.tests.integration.junit5.spi.SuiteProvider;

@TestConfig
@DbClientTest
@ContainerTest(provider = MySqlContainer.class, image = "mysql:8")
public class MySqlSuite implements SuiteProvider {

    static final String DB_NODE = "db";
    private static final int STARTUP_TIMEOUT = 60;
    private static final int CONNECTION_CHECK_TIMEOUT = 1;

    @SetUpContainer
    public void setupContainer(Config config, DatabaseContainerConfig.Builder dbConfigBuilder) {
        config.get(DB_NODE + ".connection").asNode().ifPresent(dbConfigBuilder::dbClient);
    }

    @SetUpDbClient
    public void setupDbClient(Config config, ConfigUpdate update, ContainerInfo info, DbClient.Builder builder) {
        // Merges ContainerInfo into DbClient config
        Map<String, String> updatedNodes = new HashMap<>(1);
        config.get(DB_NODE + ".connection.url").as(String.class).ifPresent(value -> updatedNodes.put(
                DB_NODE + ".connection.url",
                DatabaseContainer.replacePortInUrl(
                        value,
                        info.portMappings().get(info.config().exposedPorts()[0]))));
        Config containerConfig = Config.create(ConfigSources.create(updatedNodes), ConfigSources.create(config));
        // This replaces config stored by @TestConfig provider
        update.config(containerConfig);
        builder.config(containerConfig.get(DB_NODE));
        builder.addService(DbClientMetrics.counter()
                                   .statementNames("select-pokemons", "insert-pokemon"))
               .addService(DbClientMetrics.timer()
                                   .statementTypes(DbStatementType.INSERT));
    }

    // Value of config was updated in DbClient config hook and contains valid connection properties
    @BeforeSuite
    public void beforeSuite(DbClient dbClient, Config config) {
        InitUtils.waitForStart(
                () -> DriverManager.getConnection(config.get("db.connection.url").asString().get(),
                                                  config.get("db.connection.username").asString().get(),
                                                  config.get("db.connection.password").asString().get()),
                STARTUP_TIMEOUT,
                CONNECTION_CHECK_TIMEOUT);
        InitUtils.initSchema(dbClient);
        InitUtils.initData(dbClient);
    }

    @AfterSuite
    public void afterSuite(DbClient dbClient) {
        InitUtils.dropSchema(dbClient);
    }

    @Suite(MySqlSuite.class)
    public static class ExceptionalStmtIT extends io.helidon.dbclient.tests.common.tests.ExceptionalStmtIT {

        public ExceptionalStmtIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class FlowControlIT extends io.helidon.dbclient.tests.common.tests.FlowControlIT {

        public FlowControlIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class GetStatementIT extends io.helidon.dbclient.tests.common.tests.GetStatementIT {

        public GetStatementIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class HealthCheckIT extends io.helidon.dbclient.tests.common.tests.HealthCheckIT {

        public HealthCheckIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class InterceptorIT extends io.helidon.dbclient.tests.common.tests.InterceptorIT {

        public InterceptorIT(Config config) {
            super(config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class MapperIT extends io.helidon.dbclient.tests.common.tests.MapperIT {

        public MapperIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class QueryStatementIT extends io.helidon.dbclient.tests.common.tests.QueryStatementIT {

        public QueryStatementIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class ServerHealthCheckIT extends io.helidon.dbclient.tests.common.tests.ServerHealthCheckIT {

        public ServerHealthCheckIT(Config config, DbClient dbClient) {
            super(config, dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class ServerMetricsCheckIT extends io.helidon.dbclient.tests.common.tests.ServerMetricsCheckIT {

        public ServerMetricsCheckIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class SimpleDeleteIT extends io.helidon.dbclient.tests.common.tests.SimpleDeleteIT {

        public SimpleDeleteIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class SimpleDmlIT extends io.helidon.dbclient.tests.common.tests.SimpleDmlIT {

        public SimpleDmlIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class SimpleGetIT extends io.helidon.dbclient.tests.common.tests.SimpleGetIT {

        public SimpleGetIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class SimpleInsertIT extends io.helidon.dbclient.tests.common.tests.SimpleInsertIT {

        public SimpleInsertIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class SimpleQueriesIT extends io.helidon.dbclient.tests.common.tests.SimpleQueriesIT {

        public SimpleQueriesIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class SimpleUpdateIT extends io.helidon.dbclient.tests.common.tests.SimpleUpdateIT {

        public SimpleUpdateIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class StatementDmlIT extends io.helidon.dbclient.tests.common.tests.StatementDmlIT {

        public StatementDmlIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class TransactionDeleteIT extends io.helidon.dbclient.tests.common.tests.TransactionDeleteIT {

        public TransactionDeleteIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class TransactionExceptionalStmtIT extends io.helidon.dbclient.tests.common.tests.TransactionExceptionalStmtIT {

        public TransactionExceptionalStmtIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class TransactionGetIT extends io.helidon.dbclient.tests.common.tests.TransactionGetIT {

        public TransactionGetIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class TransactionInsertIT extends io.helidon.dbclient.tests.common.tests.TransactionInsertIT {

        public TransactionInsertIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class TransactionQueriesIT extends io.helidon.dbclient.tests.common.tests.TransactionQueriesIT {

        public TransactionQueriesIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class TransactionUpdateIT extends io.helidon.dbclient.tests.common.tests.TransactionUpdateIT {

        public TransactionUpdateIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

}
