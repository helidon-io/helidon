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

package io.helidon.examples.webserver.threadpool;

import io.helidon.common.LogConfig;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HealthChecks;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.MetricsSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * The application main class.
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
     */
    public static void main(final String[] args) {
        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();
        startServer(config);
    }

    /**
     * Start the server.
     *
     * @return the created {@link WebServer} instance
     */
    static Single<WebServer> startServer(Config config) {
        // load logging configuration
        LogConfig.configureRuntime();

        // Build server using three ports:
        // default public port, admin port, private port
        WebServer server = WebServer.builder(createPublicRouting(config))
                .config(config.get("server"))
                .addMediaSupport(JsonpSupport.create())
                .build();

        Single<WebServer> webServerSingle = server.start();

        // Try to start the server. If successful, print some info and arrange to
        // print a message at shutdown. If unsuccessful, print the exception.
        webServerSingle
                .thenAccept(ws -> {
                    System.out.println(
                            "WEB server is up! http://localhost:" + ws.port());
                    ws.whenShutdown().thenRun(()
                            -> System.out.println("WEB server is DOWN. Good bye!"));
                })
                .exceptionallyAccept(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                });

        // Server threads are not daemon. No need to block. Just react.

        return webServerSingle;
    }

    /**
     * Creates public {@link Routing}.
     *
     * @return routing for use on "public" port
     */
    private static Routing createPublicRouting(Config config) {
        MetricsSupport metrics = MetricsSupport.create();
        HealthSupport health = HealthSupport.builder()
                .addLiveness(HealthChecks.healthChecks())   // Adds a convenient set of checks
                .build();
        GreetService greetService = new GreetService(config);
        return Routing.builder()
                .register(health)
                .register(metrics)
                .register("/greet", greetService)
                .build();
    }

}
