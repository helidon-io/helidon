/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.examples.quickstart.se;

import io.helidon.common.LogConfig;
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
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        // load logging configuration
        LogConfig.configureRuntime();

        WebServer server = WebServer.builder()
                .update(Main::configure) // configure routing and server
                .build() // create a server instance
                .start() // start the server
                .await(); // wait for the server to be started

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/greet");
        server.whenShutdown().thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));

    }

    /**
     * Configure the server builder.
     */
    static void configure(WebServer.Builder builder) {
        Config config = Config.create();
        builder.routing(createRouting(config))
                .config(config.get("server"))
                .addMediaSupport(JsonpSupport.create());
    }

    /**
     * Creates new {@link Routing}.
     *
     * @param config configuration of this server
     * @return routing configured with a health check, metrics, and a service
     */
    private static Routing createRouting(Config config) {

        MetricsSupport metrics = MetricsSupport.create();
        GreetService greetService = new GreetService(config);
        HealthSupport health = HealthSupport.builder()
                .addLiveness(HealthChecks.healthChecks())   // Adds a convenient set of checks
                .build();

        return Routing.builder()
                .register(health)                   // Health at "/health"
                .register(metrics)                  // Metrics at "/metrics"
                .register("/greet", greetService)
                .build();
    }
}
