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

package io.helidon.examples.metrics.kpi;

import io.helidon.common.LogConfig;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.MetricsSupport;
import io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings;
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
        startServer();
    }

    /**
     * Start the server.
     * @return the created {@link WebServer} instance
     */
    static Single<WebServer> startServer() {

        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        WebServer server = WebServer.builder()
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
     * Creates new {@link Routing}.
     *
     * @return routing configured with JSON support, a health check, and a service
     * @param config configuration of this server
     */
    private static Routing createRouting(Config config) {

        /*
         * For purposes of illustration, the key performance indicator settings for the
         * MetricsSupport instance are set up according to a system property so you can see,
         * in one example, how to code each approach. Normally, you would choose one
         * approach to use in an application.
         */
        MetricsSupport metricsSupport = Boolean.getBoolean("useConfig")
                ? metricsSupportWithConfig(config.get("metrics"))
                : metricsSupportWithoutConfig();

        GreetService greetService = new GreetService(config);

        return Routing.builder()
                .register(metricsSupport)                  // Metrics at "/metrics"
                .register("/greet", greetService)
                .build();
    }

    /**
     * Creates a {@link MetricsSupport} instance using a "metrics" configuration node.
     *
     * @param metricsConfig {@link Config} node with key "metrics" if present; an empty node otherwise
     * @return {@code MetricsSupport} object with metrics (including KPI) set up using the config node
     */
    private static MetricsSupport metricsSupportWithConfig(Config metricsConfig) {
        return MetricsSupport.create(metricsConfig);
    }

    /**
     * Creates a {@link MetricsSupport} instance explicitly turning on extended KPI metrics.
     *
     * @return {@code MetricsSupport} object with extended KPI metrics enabled
     */
    private static MetricsSupport metricsSupportWithoutConfig() {

        KeyPerformanceIndicatorMetricsSettings.Builder settingsBuilder =
                KeyPerformanceIndicatorMetricsSettings.builder()
                        .extended(true)
                        .longRunningRequestThresholdMs(2000);
        return MetricsSupport.builder()
                .keyPerformanceIndicatorsMetricsSettings(settingsBuilder)
                .build();
    }
}
