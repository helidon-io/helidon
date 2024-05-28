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
package io.helidon.tests.integration.dbclient.mysql;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.DriverManager;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.metrics.DbClientMetrics;
import io.helidon.tests.integration.dbclient.common.utils.InitUtils;
import io.helidon.tests.integration.junit5.AfterSuite;
import io.helidon.tests.integration.junit5.BeforeSuite;
import io.helidon.tests.integration.junit5.ConfigUtils;
import io.helidon.tests.integration.junit5.ContainerConfig;
import io.helidon.tests.integration.junit5.Suite;
import io.helidon.tests.integration.junit5.SuiteResolver;
import io.helidon.tests.integration.junit5.spi.SuiteProvider;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class MySqlSuite implements SuiteProvider, SuiteResolver {

    private static final System.Logger LOGGER = System.getLogger(MySqlSuite.class.getName());

    private static final String CONFIG_FILE = "test.yaml";
    private static final int STARTUP_TIMEOUT = 60;
    private static final int CONNECTION_CHECK_TIMEOUT = 1;

    private Config config;
    private GenericContainer<?> container;
    private DbClient dbClient;

    public MySqlSuite() {
        config = null;
        container = null;
        dbClient = null;
    }

    @BeforeSuite
    public void beforeSuite() {
        config = Config.just(ConfigSources.classpath(CONFIG_FILE));
        // Build MySQL container configuration from config or use default values as fallback
        URI uri = ConfigUtils.uriFromDbUrl(
                config.get("db.connection.url")
                        .asString()
                        .orElse("jdbc:mysql://127.0.0.1:3306/pokemon?useSSL=false&allowPublicKeyRetrieval=true"));
        ContainerConfig containerConfig = ContainerConfig.builder()
                .image(config.get("db.container").asString().orElse("mysql:latest"))
                .addEnvironment(Map.of(
                        "MYSQL_ROOT_PASSWORD", config.get("db.rootpw").asString().orElse("R00t_P4ssw0rd"),
                        "MYSQL_USER", config.get("db.connection.username").asString().orElse("user"),
                        "MYSQL_PASSWORD", config.get("db.connection.password").asString().orElse("p4ssw0rd"),
                        "MYSQL_DATABASE", ConfigUtils.dbNameFromUri(uri)))
                .exposedPorts(new int[] {uri.getPort() >=0 ? uri.getPort() : 3306})
                .build();
        // Start MySQL container
        container = new GenericContainer<>(DockerImageName.parse(containerConfig.image()));
        containerConfig.environment().forEach(container::withEnv);
        container.addExposedPorts(containerConfig.exposedPorts());
        LOGGER.log(System.Logger.Level.TRACE,
                   () -> String.format("Starting MySQL database from image %s", containerConfig.image()));
        container.start();
        // Update config with current MySQL container configuration
        int dbPort = container.getMappedPort(containerConfig.exposedPorts()[0]);
        try {
            URI newUri = new URI("mysql",
                                 null,
                                 uri.getHost(),
                                 dbPort,
                                 uri.getPath(),
                                 uri.getQuery(),
                                 uri.getFragment());
            config = Config.create(
                    ConfigSources.create(
                            Map.of(
                                    "db.container", containerConfig.image(),
                                    "db.rootpw", containerConfig.environment().get("MYSQL_ROOT_PASSWORD"),
                                    "db.connection.username", containerConfig.environment().get("MYSQL_USER"),
                                    "db.connection.password", containerConfig.environment().get("MYSQL_PASSWORD"),
                                    "db.connection.url", "jdbc:" + newUri.toString())),
                    ConfigSources.create(config));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        // Wait for MySQL database to start accepting JDBC connections
        InitUtils.waitForStart(
                () -> DriverManager.getConnection(config.get("db.connection.url").asString().get(),
                                                  config.get("db.connection.username").asString().get(),
                                                  config.get("db.connection.password").asString().get()),
                STARTUP_TIMEOUT,
                CONNECTION_CHECK_TIMEOUT);
        // Create DbClient from updated configuration
        dbClient = DbClient.builder(config.get("db"))
                .addService(DbClientMetrics.counter()
                                    .statementNames("select-pokemons", "insert-pokemon"))
                .addService(DbClientMetrics.timer()
                                    .statementTypes(DbStatementType.INSERT))
                .build();
        // Initialize the database
        InitUtils.initSchema(dbClient);
        InitUtils.initData(dbClient);
    }

    @AfterSuite
    public void afterSuite(DbClient dbClient) {
        // Clean up the database
        InitUtils.dropSchema(dbClient);
        LOGGER.log(System.Logger.Level.TRACE, "Stopping MySQL database");
        // Stop MySQL container
        container.stop();
    }

    @Override
    public boolean supportsParameter(Type type) {
        return Config.class.isAssignableFrom((Class<?>) type)
                || DbClient.class.isAssignableFrom((Class<?>) type);
    }

    @Override
    public Object resolveParameter(Type type) {
        if (Config.class.isAssignableFrom((Class<?>) type)) {
            return config;
        } else if (DbClient.class.isAssignableFrom((Class<?>) type)) {
            return dbClient;
        }
        throw new IllegalArgumentException(String.format("Cannot resolve parameter Type %s", type.getTypeName()));
    }

    @Suite(MySqlSuite.class)
    public static class ExceptionalStmtIT extends io.helidon.tests.integration.dbclient.common.tests.ExceptionalStmtIT {

        public ExceptionalStmtIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class FlowControlIT extends io.helidon.tests.integration.dbclient.common.tests.FlowControlIT {
        public FlowControlIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class GetStatementIT extends io.helidon.tests.integration.dbclient.common.tests.GetStatementIT {

        public GetStatementIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class HealthCheckIT extends io.helidon.tests.integration.dbclient.common.tests.HealthCheckIT {

        public HealthCheckIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class InterceptorIT extends io.helidon.tests.integration.dbclient.common.tests.InterceptorIT {

        public InterceptorIT(Config config) {
            super(config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class MapperIT extends io.helidon.tests.integration.dbclient.common.tests.MapperIT {

        public MapperIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class QueryStatementIT extends io.helidon.tests.integration.dbclient.common.tests.QueryStatementIT {

        public QueryStatementIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class ServerHealthCheckIT extends io.helidon.tests.integration.dbclient.common.tests.ServerHealthCheckIT {

        public ServerHealthCheckIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class ServerMetricsCheckIT extends io.helidon.tests.integration.dbclient.common.tests.ServerMetricsCheckIT {

        public ServerMetricsCheckIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class SimpleDeleteIT extends io.helidon.tests.integration.dbclient.common.tests.SimpleDeleteIT {

        public SimpleDeleteIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class SimpleDmlIT extends io.helidon.tests.integration.dbclient.common.tests.SimpleDmlIT {

        public SimpleDmlIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class SimpleGetIT extends io.helidon.tests.integration.dbclient.common.tests.SimpleGetIT {

        public SimpleGetIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class SimpleInsertIT extends io.helidon.tests.integration.dbclient.common.tests.SimpleInsertIT {

        public SimpleInsertIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class SimpleQueriesIT extends io.helidon.tests.integration.dbclient.common.tests.SimpleQueriesIT {

        public SimpleQueriesIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class SimpleUpdateIT extends io.helidon.tests.integration.dbclient.common.tests.SimpleUpdateIT {

        public SimpleUpdateIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class StatementDmlIT extends io.helidon.tests.integration.dbclient.common.tests.StatementDmlIT {

        public StatementDmlIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class TransactionDeleteIT extends io.helidon.tests.integration.dbclient.common.tests.TransactionDeleteIT {

        public TransactionDeleteIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class TransactionExceptionalStmtIT extends io.helidon.tests.integration.dbclient.common.tests.TransactionExceptionalStmtIT {

        public TransactionExceptionalStmtIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(MySqlSuite.class)
    public static class TransactionGetIT extends io.helidon.tests.integration.dbclient.common.tests.TransactionGetIT {

        public TransactionGetIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class TransactionInsertIT extends io.helidon.tests.integration.dbclient.common.tests.TransactionInsertIT {

        public TransactionInsertIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class TransactionQueriesIT extends io.helidon.tests.integration.dbclient.common.tests.TransactionQueriesIT {

        public TransactionQueriesIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(MySqlSuite.class)
    public static class TransactionUpdateIT extends io.helidon.tests.integration.dbclient.common.tests.TransactionUpdateIT {

        public TransactionUpdateIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

}
