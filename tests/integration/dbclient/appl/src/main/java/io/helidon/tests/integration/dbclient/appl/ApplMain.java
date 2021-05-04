/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.appl;

import java.util.Map;
import java.util.logging.Logger;

import io.helidon.common.LogConfig;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.dbclient.metrics.DbClientMetrics;
import io.helidon.health.HealthSupport;
import io.helidon.media.jsonb.JsonbSupport;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.MetricsSupport;
import io.helidon.tests.integration.dbclient.appl.health.HealthCheckService;
import io.helidon.tests.integration.dbclient.appl.interceptor.InterceptorService;
import io.helidon.tests.integration.dbclient.appl.mapping.MapperService;
import io.helidon.tests.integration.dbclient.appl.result.FlowControlService;
import io.helidon.tests.integration.dbclient.appl.simple.SimpleDeleteService;
import io.helidon.tests.integration.dbclient.appl.simple.SimpleGetService;
import io.helidon.tests.integration.dbclient.appl.simple.SimpleInsertService;
import io.helidon.tests.integration.dbclient.appl.simple.SimpleQueryService;
import io.helidon.tests.integration.dbclient.appl.simple.SimpleUpdateService;
import io.helidon.tests.integration.dbclient.appl.statement.DmlStatementService;
import io.helidon.tests.integration.dbclient.appl.statement.GetStatementService;
import io.helidon.tests.integration.dbclient.appl.statement.QueryStatementService;
import io.helidon.tests.integration.dbclient.appl.tools.ExitService;
import io.helidon.tests.integration.dbclient.appl.transaction.TransactionDeleteService;
import io.helidon.tests.integration.dbclient.appl.transaction.TransactionGetService;
import io.helidon.tests.integration.dbclient.appl.transaction.TransactionInsertService;
import io.helidon.tests.integration.dbclient.appl.transaction.TransactionQueriesService;
import io.helidon.tests.integration.dbclient.appl.transaction.TransactionUpdateService;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * Main class.
 * Testing application entry point.
 */
public class ApplMain {

    private static final Logger LOGGER = Logger.getLogger(ApplMain.class.getName());

    private static final String CONFIG_PROPERTY_NAME="app.config";

    private static final String DEFAULT_CONFIG_FILE="test.yaml";

    private static WebServer startServer(final String configFile) {

        final Config config = Config.create(ConfigSources.classpath(configFile));
        final Config dbConfig = config.get("db");

        // Client services are added through a service loader - see mongoDB example for explicit services
        final DbClient dbClient = DbClient.builder(dbConfig)
                .addService(DbClientMetrics.counter()
                                    .statementNames("select-pokemon-named-arg", "select-pokemon-order-arg", "insert-pokemon"))
                .addService(DbClientMetrics.timer()
                                    .statementTypes(DbStatementType.GET))
                .build();
        final HealthSupport health = HealthSupport.builder()
                .addLiveness(DbClientHealthCheck
                        .builder(dbClient)
                        //.dml()
                        .statementName("ping")
                        .build())
                .build();

        final Map<String, String> statements = dbConfig
                .get("statements")
                .detach()
                .asMap()
                .get();

        final ExitService exitResource = new ExitService();

        final DbClientService interceptorTestService = new InterceptorService.TestClientService();

        final Routing routing = Routing.builder()
                .register(MetricsSupport.create())  // Metrics at "/metrics"
                .register(health)                   // Health at "/health"
                .register("/Init", new InitService(dbClient, dbConfig))
                .register("/Exit", exitResource)
                .register("/Verify", new VerifyService(dbClient, config))
                .register("/SimpleGet", new SimpleGetService(dbClient, statements))
                .register("/SimpleQuery", new SimpleQueryService(dbClient, statements))
                .register("/SimpleUpdate", new SimpleUpdateService(dbClient, statements))
                .register("/SimpleInsert", new SimpleInsertService(dbClient, statements))
                .register("/SimpleDelete", new SimpleDeleteService(dbClient, statements))
                .register("/TransactionGet", new TransactionGetService(dbClient, statements))
                .register("/TransactionQueries", new TransactionQueriesService(dbClient, statements))
                .register("/TransactionUpdate", new TransactionUpdateService(dbClient, statements))
                .register("/TransactionInsert", new TransactionInsertService(dbClient, statements))
                .register("/TransactionDelete", new TransactionDeleteService(dbClient, statements))
                .register("/DmlStatement", new DmlStatementService(dbClient, statements))
                .register("/GetStatement", new GetStatementService(dbClient, statements))
                .register("/QueryStatement", new QueryStatementService(dbClient, statements))
                .register("/FlowControl", new FlowControlService(dbClient, statements))
                .register("/Mapper", new MapperService(dbClient, statements))
                .register("/Interceptor", new InterceptorService(
                        DbClient.builder(dbConfig)
                                .addService(interceptorTestService).build(),
                        statements,
                        interceptorTestService))
                .register("/HealthCheck", new HealthCheckService(dbClient, dbConfig, statements))
                .build();

        // Prepare routing for the server
        final WebServer.Builder serverBuilder = WebServer.builder()
                .routing(routing)
                // Get webserver config from the "server" section of application.yaml
                .config(config.get("server"));

        final WebServer server = serverBuilder
                .addMediaSupport(JsonpSupport.create())
                .addMediaSupport(JsonbSupport.create())
                .build();

        exitResource.setServer(server);

        // Start the server and print some info.
        server.start().thenAccept(ws -> {
            System.out.println(
                    "WEB server is up! http://localhost:" + ws.port() + "/");
        });

        // Server threads are not daemon. NO need to block. Just react.
        server.whenShutdown().thenRun(
                () -> System.out.println("WEB server is DOWN. Good bye!"));

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
        LOGGER.info(() -> String.format("Configuration file: %s", configFile));

        LogConfig.configureRuntime();
        startServer(configFile);

    }

}
