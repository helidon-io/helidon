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

package io.helidon.examples.metrics.exemplar;

import io.helidon.common.LogConfig;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.api.MetricsSettings;
import io.helidon.metrics.api.RegistrySettings;
import io.helidon.metrics.serviceapi.MetricsSupport;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.eclipse.microprofile.metrics.MetricRegistry;

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
        startServer(true);
    }

    /**
     * Starts the server using a specified configuration.
     *
     * @param configFile the config file to use in starting the server
     * @return a {@code Single} for the {@code WebServer}
     */
    static Single<WebServer> startServer(String configFile) {
        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = Config.just(ConfigSources.classpath("/" + configFile));

        WebServer server = WebServer.builder()
                .tracer(TracerBuilder.create(config.get("tracing")))
                .routing(createRouting(config))
                .config(config.get("server"))
                .addMediaSupport(JsonpSupport.create())
                .build();

        Single<WebServer> webserver = server.start();

        // Try to start the server. If successful, print some info and arrange to
        // print a message at shutdown. If unsuccessful, print the exception.
        webserver.thenAccept(ws -> {
                    System.out.println("WEB server is up! http://localhost:" + ws.port() + "/greet");
                    ws.whenShutdown().thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));
                })
                .exceptionallyAccept(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                });

        return webserver;
    }

    /**
     * Start the server.
     *
     * @param isStrictExemplars whether to use strict exemplar behavior
     * @return the created {@link WebServer} instance
     */
    static Single<WebServer> startServer(boolean isStrictExemplars) {

        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        WebServer server = WebServer.builder()
                .tracer(TracerBuilder.create(config.get("tracing")))
                .routing(createRouting(config, isStrictExemplars))
                .config(config.get("server"))
                .addMediaSupport(JsonpSupport.create())
                .build();

        Single<WebServer> webserver = server.start();

        // Try to start the server. If successful, print some info and arrange to
        // print a message at shutdown. If unsuccessful, print the exception.
        webserver.thenAccept(ws -> {
                    System.out.println("WEB server is up! http://localhost:" + ws.port() + "/greet");
                    ws.whenShutdown().thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));
                })
                .exceptionallyAccept(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                });

        return webserver;
    }

    /**
     * Creates new {@link Routing}.
     *
     * @return routing configured with JSON support, a health check, and a service
     * @param config configuration of this server
     */
    private static Routing.Builder createRouting(Config config, boolean isStrictExemplars) {

        MetricsSupport metrics = MetricsSupport.create(MetricsSettings.builder()
                                                               .registrySettings(MetricRegistry.Type.APPLICATION,
                                                                                 RegistrySettings.builder()
                                                                                         .strictExemplars(isStrictExemplars)
                                                                                         .build())
                                                               .build());
        GreetService greetService = new GreetService(config);

        return Routing.builder()
                .register(metrics)                  // Metrics at "/metrics"
                .register("/greet", greetService);
    }

    private static Routing.Builder createRouting(Config config) {
        MetricsSupport metrics = MetricsSupport.create(MetricsSettings.builder()
                                                               .config(config.get("metrics"))
                                                               .build());
        GreetService greetService = new GreetService(config);

        return Routing.builder()
                .register(metrics)
                .register("/greet", greetService);
    }
}
