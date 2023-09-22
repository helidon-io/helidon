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
package io.helidon.dbclient.tests.h2;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.metrics.DbClientMetrics;
import io.helidon.dbclient.tests.common.utils.InitUtils;
import io.helidon.dbclient.tests.common.utils.TestConfig;
import io.helidon.tests.integration.junit5.AfterSuite;
import io.helidon.tests.integration.junit5.BeforeSuite;
import io.helidon.tests.integration.junit5.Suite;
import io.helidon.tests.integration.junit5.SuiteResolver;
import io.helidon.tests.integration.junit5.spi.SuiteProvider;

import org.h2.tools.Server;

/**
 * Helidon Database Client Integration Tests with H2 Database {@link SuiteProvider}.
 */
public class H2Suite implements SuiteProvider, SuiteResolver {

    private static final Logger LOGGER = System.getLogger(H2Suite.class.getName());

    private static final String CONFIG_FILE = "test.yaml";
    private static final int STARTUP_TIMEOUT = 60;
    private static final int CONNECTION_CHECK_TIMEOUT = 1;

    private Server server;
    private Config config;
    private DbClient dbClient;

    public H2Suite() {
        server = null;
        config = null;
        dbClient = null;
    }

    @BeforeSuite
    public void beforeSuite() {
        config = Config.just(ConfigSources.classpath(CONFIG_FILE));
        if (config == null) {
            throw new IllegalStateException(String.format("Config file %s is not available", CONFIG_FILE));
        }
        start(config.get("db"));
        InitUtils.waitForStart(
                () -> DriverManager.getConnection(config.get("db.connection.url").as(String.class).get()),
                STARTUP_TIMEOUT,
                CONNECTION_CHECK_TIMEOUT);
        dbClient = DbClient.builder(config.get("db"))
                // add an interceptor to named statement(s)
                .addService(DbClientMetrics.counter()
                                   .statementNames("select-pokemons", "insert-pokemon"))
                // add an interceptor to statement type(s)
                .addService(DbClientMetrics.timer()
                                    .statementTypes(DbStatementType.INSERT))
                .build();
        InitUtils.initSchema(dbClient);
        InitUtils.initData(dbClient);
    }

    @AfterSuite
    public void afterSuite() {
        InitUtils.dropSchema(dbClient);
        stop();
    }

    @Override
    public boolean supportsParameter(Type type) {
        return DbClient.class.isAssignableFrom((Class<?>) type) || Config.class.isAssignableFrom((Class<?>) type);
    }

    @Override
    public Object resolveParameter(Type type) {
        if (DbClient.class.isAssignableFrom((Class<?>)type)) {
            return dbClient;
        }
        if (Config.class.isAssignableFrom((Class<?>) type)) {
            return config;
        }
        throw new IllegalArgumentException(String.format("Cannot resolve parameter Type %s", type.getTypeName()));
    }

    private void start(Config dbConfig) {
        String url = dbConfig.get("connection.url").as(String.class).get();
        LOGGER.log(Level.TRACE,
                   () -> String.format("Starting H2 database %s", url));
        URI dbUri = URI.create(
                dbConfig.get("connection.url").asString().get().substring("jdbc:h2:".length()));
        int dbPort = dbUri.getPort();
        String dbName = dbNameFromUri(dbUri);
        String password = dbConfig.get("rootpw").asString().get();
        String baseDir = Paths.get("").toAbsolutePath().resolve("target").resolve(dbName).toString();
        try {
            // Starting without specific TCP port to allow server to choose one
            server = Server.createTcpServer(
                    "-web",
                    "-webAllowOthers",
                    "-tcp",
                    "-tcpAllowOthers",
                    "-tcpAllowOthers",
                    "-tcpPassword",
                    password,
                    "-baseDir",
                    baseDir,
                    "-ifNotExists");
            server.start();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        String newUrl = url.replace(Integer.toString(dbPort), Integer.toString(server.getPort()));
        LOGGER.log(Level.TRACE,
                   () -> String.format("Updated H2 database URL: %s", newUrl));
        // Update config if URL has changed
        if (!url.equals(newUrl)) {
            config = Config.create(ConfigSources.create(Map.of("db.connection.url", newUrl)),
                                   ConfigSources.create(config));
        }
    }

    private void stop() {
        LOGGER.log(Level.TRACE, "Shutting down H2 database");
        server.stop();
    }

    private static String dbNameFromUri(URI dbUri) {
        String dbNameWithParams = TestConfig.dbNameFromUri(dbUri);
        return dbNameWithParams.substring(0, dbNameWithParams.indexOf(';'));
    }

    @Suite(H2Suite.class)
    public static class ExceptionalStmtIT extends io.helidon.dbclient.tests.common.tests.ExceptionalStmtIT {

        public ExceptionalStmtIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(H2Suite.class)
    public static class FlowControlIT extends io.helidon.dbclient.tests.common.tests.FlowControlIT {
        public FlowControlIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(H2Suite.class)
    public static class GetStatementIT extends io.helidon.dbclient.tests.common.tests.GetStatementIT {

        public GetStatementIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(H2Suite.class)
    public static class HealthCheckIT extends io.helidon.dbclient.tests.common.tests.HealthCheckIT {

        public HealthCheckIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(H2Suite.class)
    public static class InterceptorIT extends io.helidon.dbclient.tests.common.tests.InterceptorIT {

        public InterceptorIT(Config config) {
            super(config);
        }

    }

    @Suite(H2Suite.class)
    public static class MapperIT extends io.helidon.dbclient.tests.common.tests.MapperIT {

        public MapperIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(H2Suite.class)
    public static class QueryStatementIT extends io.helidon.dbclient.tests.common.tests.QueryStatementIT {

        public QueryStatementIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(H2Suite.class)
    public static class ServerHealthCheckIT extends io.helidon.dbclient.tests.common.tests.ServerHealthCheckIT {

        public ServerHealthCheckIT(Config config, DbClient dbClient) {
            super(config, dbClient);
        }

    }

    @Suite(H2Suite.class)
    public static class ServerMetricsCheckIT extends io.helidon.dbclient.tests.common.tests.ServerMetricsCheckIT {

        public ServerMetricsCheckIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(H2Suite.class)
    public static class SimpleDeleteIT extends io.helidon.dbclient.tests.common.tests.SimpleDeleteIT {

        public SimpleDeleteIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(H2Suite.class)
    public static class SimpleDmlIT extends io.helidon.dbclient.tests.common.tests.SimpleDmlIT {

        public SimpleDmlIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(H2Suite.class)
    public static class SimpleGetIT extends io.helidon.dbclient.tests.common.tests.SimpleGetIT {

        public SimpleGetIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(H2Suite.class)
    public static class SimpleInsertIT extends io.helidon.dbclient.tests.common.tests.SimpleInsertIT {

        public SimpleInsertIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(H2Suite.class)
    public static class SimpleQueriesIT extends io.helidon.dbclient.tests.common.tests.SimpleQueriesIT {

        public SimpleQueriesIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(H2Suite.class)
    public static class SimpleUpdateIT extends io.helidon.dbclient.tests.common.tests.SimpleUpdateIT {

        public SimpleUpdateIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(H2Suite.class)
    public static class StatementDmlIT extends io.helidon.dbclient.tests.common.tests.StatementDmlIT {

        public StatementDmlIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(H2Suite.class)
    public static class TransactionDeleteIT extends io.helidon.dbclient.tests.common.tests.TransactionDeleteIT {

        public TransactionDeleteIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(H2Suite.class)
    public static class TransactionExceptionalStmtIT extends io.helidon.dbclient.tests.common.tests.TransactionExceptionalStmtIT {

        public TransactionExceptionalStmtIT(DbClient dbClient) {
            super(dbClient);
        }

    }

    @Suite(H2Suite.class)
    public static class TransactionGetIT extends io.helidon.dbclient.tests.common.tests.TransactionGetIT {

        public TransactionGetIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(H2Suite.class)
    public static class TransactionInsertIT extends io.helidon.dbclient.tests.common.tests.TransactionInsertIT {

        public TransactionInsertIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(H2Suite.class)
    public static class TransactionQueriesIT extends io.helidon.dbclient.tests.common.tests.TransactionQueriesIT {

        public TransactionQueriesIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

    @Suite(H2Suite.class)
    public static class TransactionUpdateIT extends io.helidon.dbclient.tests.common.tests.TransactionUpdateIT {

        public TransactionUpdateIT(DbClient dbClient, Config config) {
            super(dbClient, config);
        }

    }

}
