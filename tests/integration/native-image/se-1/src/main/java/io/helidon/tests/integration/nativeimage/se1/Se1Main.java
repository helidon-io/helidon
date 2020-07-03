/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.nativeimage.se1;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Set;
import java.util.logging.LogManager;

import io.helidon.config.Config;
import io.helidon.config.FileSystemWatcher;
import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HealthChecks;
import io.helidon.media.jsonb.JsonbSupport;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.MetricsSupport;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webserver.Routing;
import io.helidon.webserver.StaticContentSupport;
import io.helidon.webserver.WebServer;

import org.eclipse.microprofile.health.HealthCheckResponse;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

/**
 * Main class of this integration test.
 */
public final class Se1Main {

    /**
     * Cannot be instantiated.
     */
    private Se1Main() {
    }

    /**
     * Application main entry point.
     * @param args command line arguments.
     * @throws java.io.IOException if there are problems reading logging properties
     */
    public static void main(final String[] args) throws IOException {
        startServer();
    }

    /**
     * Start the server.
     * @return the created {@link io.helidon.webserver.WebServer} instance
     * @throws IOException if there are problems reading logging properties
     */
    static WebServer startServer() throws IOException {

        // load logging configuration
        LogManager.getLogManager().readConfiguration(
                Se1Main.class.getResourceAsStream("/logging.properties"));

        // By default this will pick up application.yaml from the classpath
        Config config = buildConfig();

        // Get webserver config from the "server" section of application.yaml
        WebServer server = WebServer.builder()
                .routing(createRouting(config))
                .config(config.get("server"))
                .tracer(TracerBuilder.create(config.get("tracing")).build())
                .addMediaSupport(JsonpSupport.create())
                .addMediaSupport(JsonbSupport.create())
                .printFeatureDetails(true)
                .build();

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

    private static Config buildConfig() {
        return Config.builder()
                .sources(
                        classpath("se-test.yaml").optional(),
                        file("conf/se.yaml")
                                .changeWatcher(FileSystemWatcher.create())
                                .optional(),
                        classpath("application.yaml"))
                .build();
    }

    /**
     * Creates new {@link io.helidon.webserver.Routing}.
     *
     * @return routing configured with JSON support, a health check, and a service
     * @param config configuration of this server
     */
    private static Routing createRouting(Config config) {

        MetricsSupport metrics = MetricsSupport.create();
        GreetService greetService = new GreetService(config);
        MockZipkinService zipkinService = new MockZipkinService(Set.of("helidon-webclient"));
        WebClientService webClientService = new WebClientService(config, zipkinService);
        HealthSupport health = HealthSupport.builder()
                .addLiveness(HealthChecks.healthChecks())   // Adds a convenient set of checks
                .addLiveness(() -> HealthCheckResponse.named("custom") // a custom health check
                        .up()
                        .withData("timestamp", System.currentTimeMillis())
                        .build())
                .build();

        return Routing.builder()
                .register("/static/path", StaticContentSupport.create(Paths.get("web")))
                .register("/static/classpath", StaticContentSupport.create("web"))
                .register("/static/jar", StaticContentSupport.create("web-jar"))
                .register(WebSecurity.create(config.get("security")))
                .register(health)                   // Health at "/health"
                .register(metrics)                  // Metrics at "/metrics"
                .register("/greet", greetService)
                .register("/wc", webClientService)
                .register("/zipkin", zipkinService)
                .build();
    }

}
