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
package io.helidon.tests.integration.dbclient.app;

import java.lang.System.Logger.Level;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.dbclient.metrics.DbClientMetrics;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.tests.integration.dbclient.app.tests.FlowControlService;
import io.helidon.tests.integration.dbclient.app.tests.HealthCheckService;
import io.helidon.tests.integration.dbclient.app.tests.InterceptorService;
import io.helidon.tests.integration.dbclient.app.tests.MapperService;
import io.helidon.tests.integration.dbclient.app.tests.SimpleDeleteService;
import io.helidon.tests.integration.dbclient.app.tests.SimpleGetService;
import io.helidon.tests.integration.dbclient.app.tests.SimpleInsertService;
import io.helidon.tests.integration.dbclient.app.tests.SimpleQueryService;
import io.helidon.tests.integration.dbclient.app.tests.SimpleUpdateService;
import io.helidon.tests.integration.dbclient.app.tests.StatementDmlService;
import io.helidon.tests.integration.dbclient.app.tests.StatementGetService;
import io.helidon.tests.integration.dbclient.app.tests.StatementQueryService;
import io.helidon.tests.integration.dbclient.app.tests.TransactionDeleteService;
import io.helidon.tests.integration.dbclient.app.tests.TransactionGetService;
import io.helidon.tests.integration.dbclient.app.tests.TransactionInsertService;
import io.helidon.tests.integration.dbclient.app.tests.TransactionQueryService;
import io.helidon.tests.integration.dbclient.app.tests.TransactionUpdateService;
import io.helidon.tests.integration.dbclient.app.tools.ExitService;
import io.helidon.tests.integration.harness.RemoteTestException;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;

/**
 * Main class.
 * Testing application entry point.
 */
public class Main {

    private static final System.Logger LOGGER = System.getLogger(Main.class.getName());
    private static final String CONFIG_PROPERTY_NAME = "app.config";
    private static final String DEFAULT_CONFIG_FILE = "test.yaml";

    /**
     * Start the server.
     *
     * @param configFile config file
     * @return server
     */
    public static WebServer startServer(String configFile) {

        Config config = Config.create(ConfigSources.classpath("common.yaml"),
                                      ConfigSources.classpath(configFile));
        Config.global(config);

        Config dbConfig = config.get("db");
        MetricsFactory.getInstance(config.get("metrics"));

        // Client services are added through a service loader - see mongoDB example for explicit services
        DbClient dbClient = DbClient.builder(dbConfig)
                .addService(DbClientMetrics.counter()
                        .statementNames(
                                "select-pokemon-named-arg",
                                "select-pokemon-order-arg",
                                "insert-pokemon"))
                .addService(DbClientMetrics.timer().statementTypes(DbStatementType.GET))
                .build();

        HealthObserver health = HealthObserver.builder()
                .addCheck(DbClientHealthCheck.builder(dbClient)
                        .statementName("ping-query")
                        .build())
                .build();

        Map<String, String> statements = dbConfig.get("statements")
                .detach()
                .asMap()
                .get();

        ExitService exitResource = new ExitService();

        DbClientService interceptorTestService = new InterceptorService.TestClientService();

        // Prepare routing for the server
        WebServer server = WebServer.builder()
                .featuresDiscoverServices(false)
                .addFeature(ObserveFeature.builder().addObserver(health).build())
                .routing(routing -> routing
                        .register("/Init", new InitService(dbClient, dbConfig))
                        .register("/Exit", exitResource)
                        .register("/Verify", new VerifyService(dbClient, config))
                        .register("/SimpleGet", new SimpleGetService(dbClient, statements))
                        .register("/SimpleQuery", new SimpleQueryService(dbClient, statements))
                        .register("/SimpleUpdate", new SimpleUpdateService(dbClient, statements))
                        .register("/SimpleInsert", new SimpleInsertService(dbClient, statements))
                        .register("/SimpleDelete", new SimpleDeleteService(dbClient, statements))
                        .register("/TransactionGet", new TransactionGetService(dbClient, statements))
                        .register("/TransactionQueries", new TransactionQueryService(dbClient, statements))
                        .register("/TransactionUpdate", new TransactionUpdateService(dbClient, statements))
                        .register("/TransactionInsert", new TransactionInsertService(dbClient, statements))
                        .register("/TransactionDelete", new TransactionDeleteService(dbClient, statements))
                        .register("/StatementDml", new StatementDmlService(dbClient, statements))
                        .register("/StatementGet", new StatementGetService(dbClient, statements))
                        .register("/StatementQuery", new StatementQueryService(dbClient, statements))
                        .register("/FlowControl", new FlowControlService(dbClient, statements))
                        .register("/Mapper", new MapperService(dbClient, statements))
                        .register("/Interceptor", new InterceptorService(
                                DbClient.builder(dbConfig)
                                        .addService(interceptorTestService).build(),
                                statements,
                                interceptorTestService))
                        .register("/HealthCheck", new HealthCheckService(dbClient, dbConfig, statements))
                        .error(DbClientException.class, ErrorHandlerImpl.INSTANCE)
                        .error(RemoteTestException.class, ErrorHandlerImpl.INSTANCE))
                .config(config.get("server"))
                .build()
                .start();

        exitResource.setServer(server);

        // Start the server and print some info.
        System.out.println("WEB server is up! http://localhost:" + server.port() + "/");

        return server;
    }

    /**
     * Main method.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        String configFile;
        if (args != null && args.length > 0) {
            configFile = args[0];
        } else {
            configFile = System.getProperty(CONFIG_PROPERTY_NAME, DEFAULT_CONFIG_FILE);
        }
        LOGGER.log(Level.INFO, () -> String.format("Configuration file: %s", configFile));
        startServer(configFile);
    }
}
