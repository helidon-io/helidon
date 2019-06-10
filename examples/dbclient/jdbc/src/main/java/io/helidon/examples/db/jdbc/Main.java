/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.examples.db.jdbc;

import java.io.IOException;
import java.util.logging.LogManager;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.health.HealthSupport;
import io.helidon.media.jsonb.server.JsonBindingSupport;
import io.helidon.media.jsonp.server.JsonSupport;
import io.helidon.metrics.MetricsSupport;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

/**
 * Simple Hello World rest application.
 */
public final class Main {

    /**
     * Cannot be instantiated.
     */
    private Main() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     * @throws java.io.IOException if there are problems reading logging properties
     */
    public static void main(final String[] args) throws IOException {
        startServer();
    }

    /**
     * Start the server.
     *
     * @return the created {@link io.helidon.webserver.WebServer} instance
     * @throws java.io.IOException if there are problems reading logging properties
     */
    static WebServer startServer() throws IOException {

        // load logging configuration
        LogManager.getLogManager().readConfiguration(
                Main.class.getResourceAsStream("/logging.properties"));

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // Get webserver config from the "server" section of application.yaml
        ServerConfiguration serverConfig =
                ServerConfiguration.builder(config.get("server"))
                        .tracer(TracerBuilder.create(config.get("tracing")).buildAndRegister())
                        .build();

        WebServer server = WebServer.create(serverConfig, createRouting(config));

        // Start the server and print some info.
        server.start().thenAccept(ws -> {
            System.out.println(
                    "WEB server is up! http://localhost:" + ws.port() + "/");
        });

        // Server threads are not daemon. NO need to block. Just react.
        server.whenShutdown().thenRun(()
                                              -> System.out.println("WEB server is DOWN. Good bye!"));

        return server;
    }

    /**
     * Creates new {@link io.helidon.webserver.Routing}.
     *
     * @param config configuration of this server
     * @return routing configured with JSON support, a health check, and a service
     */
    private static Routing createRouting(Config config) {
        Config dbConfig = config.get("db");

        // Interceptors are added through a service loader - see mongoDB example for explicit interceptors
        DbClient dbClient = DbClient.create(dbConfig);

        HealthSupport health = HealthSupport.builder()
                .add(DbClientHealthCheck.create(dbClient))
                .build();

        return Routing.builder()
                .register(JsonSupport.create())
                .register(JsonBindingSupport.create())
                .register(DbResultSupport.create())
                .register(health)                   // Health at "/health"
                .register(MetricsSupport.create())  // Metrics at "/metrics"
                .register("/db", new PokemonService(dbClient))
                .build();
    }
}
