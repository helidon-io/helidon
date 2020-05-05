/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.examples.cors;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HealthChecks;
import io.helidon.media.jsonp.server.JsonSupport;
import io.helidon.metrics.MetricsSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.cors.CorsSupport;
import io.helidon.webserver.cors.CrossOriginConfig;

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
     * @param args command line arguments.
     * @throws IOException if there are problems reading logging properties
     */
    public static void main(final String[] args) throws IOException {
        startServer();
    }

    /**
     * Start the server.
     * @return the created {@link WebServer} instance
     * @throws IOException if there are problems reading logging properties
     */
    static WebServer startServer() throws IOException {

        // load logging configuration
        try (InputStream is = Main.class.getResourceAsStream("/logging.properties")) {
            LogManager.getLogManager()
                    .readConfiguration(is);
        }

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // Get webserver config from the "server" section of application.yaml
        ServerConfiguration serverConfig =
                ServerConfiguration.create(config.get("server"));

        WebServer server = WebServer.create(serverConfig, createRouting(config));

        // Try to start the server. If successful, print some info and arrange to
        // print a message at shutdown. If unsuccessful, print the exception.
        server.start()
            .thenAccept(ws -> {
                System.out.println(
                        "WEB server is up! http://localhost:" + ws.port() + "/greet");
                ws.whenShutdown().thenRun(()
                    -> System.out.println("WEB server is DOWN. Good bye!"));
                })
            .exceptionally(t -> {
                System.err.println("Startup failed: " + t.getMessage());
                t.printStackTrace(System.err);
                return null;
            });

        // Server threads are not daemon. No need to block. Just react.

        return server;
    }

    /**
     * Creates new {@link Routing}.
     *
     * @return routing configured with JSON support, a health check, and a service
     * @param config configuration of this server
     */
    private static Routing createRouting(Config config) {

        MetricsSupport metrics = MetricsSupport.create();
        GreetService greetService = new GreetService(config);
        HealthSupport health = HealthSupport.builder()
                .addLiveness(HealthChecks.healthChecks())   // Adds a convenient set of checks
                .build();

        // Note: Add the CORS routing *before* registering the GreetService routing.
        return Routing.builder()
                .register(JsonSupport.create())
                .register(health)                   // Health at "/health"
                .register(metrics)                 // Metrics at "/metrics"
                .register("/greet", corsSupportForGreeting(config), greetService)
                .build();
    }

    private static CorsSupport corsSupportForGreeting(Config config) {

        // The default CorsSupport object (obtained using CorsSupport.create()) allows sharing for any HTTP method and with any
        // origin. Using CorsSupport.create(Config) with a missing config node yields a default CorsSupport, which might not be
        // what you want. This example warns if either expected config node is missing and then continues with the default.

        Config restrictiveConfig = config.get("restrictive-cors");
        if (!restrictiveConfig.exists()) {
            Logger.getLogger(Main.class.getName())
                    .warning("Missing restrictive config; continuing with default CORS support");
        }

        CorsSupport.Builder corsBuilder = CorsSupport.builder();

        // Use possible overrides first.
        config.get("cors")
                .ifExists(c -> {
                    Logger.getLogger(Main.class.getName()).info("Using the override configuration");
                    corsBuilder.mappedConfig(c);
                });
        corsBuilder
                .config(restrictiveConfig) // restricted sharing for PUT, DELETE
                .addCrossOrigin(CrossOriginConfig.create()) // open sharing for other methods
                .build();

        return corsBuilder.build();
    }
}
