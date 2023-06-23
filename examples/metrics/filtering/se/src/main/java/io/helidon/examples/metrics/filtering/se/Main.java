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

package io.helidon.examples.metrics.filtering.se;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.metrics.api.MetricsSettings;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.metrics.api.RegistryFilterSettings;
import io.helidon.metrics.api.RegistrySettings;
import io.helidon.reactive.media.jsonp.JsonpSupport;
import io.helidon.reactive.metrics.MetricsSupport;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.WebServer;

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
        startServer();
    }

    /**
     * Start the server.
     * @return the created {@link io.helidon.reactive.webserver.WebServer} instance
     */
    static Single<WebServer> startServer() {

        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // Ignore the "gets" timer.
        RegistryFilterSettings.Builder registryFilterSettingsBuilder = RegistryFilterSettings.builder()
                .exclude(GreetService.TIMER_FOR_GETS);

        RegistrySettings.Builder registrySettingsBuilder = RegistrySettings.builder()
                .filterSettings(registryFilterSettingsBuilder);

        MetricsSettings.Builder metricsSettingsBuilder = MetricsSettings.builder()
                .registrySettings(Registry.APPLICATION_SCOPE, registrySettingsBuilder.build());

        WebServer server = WebServer.builder()
                .routing(createRouting(config, metricsSettingsBuilder))
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
     * Creates new {@link io.helidon.reactive.webserver.Routing}.
     *
     * @return routing configured with JSON support, a health check, and a service
     * @param config configuration of this server
     */
    private static Routing createRouting(Config config, MetricsSettings.Builder metricsSettingsBuilder) {
        MetricsSupport metricsSupport = MetricsSupport.builder()
                .metricsSettings(metricsSettingsBuilder)
                .build();
        MetricRegistry appRegistry = RegistryFactory.getInstance(metricsSettingsBuilder.build())
                .getRegistry(Registry.APPLICATION_SCOPE);

        GreetService greetService = new GreetService(config, appRegistry);

        return Routing.builder()
                .register(metricsSupport)           // Metrics at "/metrics"
                .register("/greet", greetService)
                .build();
    }
}
