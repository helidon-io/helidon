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

package io.helidon.examples.metrics.kpi;

import java.time.Duration;

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.metrics.api.KeyPerformanceIndicatorMetricsConfig;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.metrics.MetricsObserver;

/**
 * The application main class.
 */
public final class Main {

    static final String USE_CONFIG_PROPERTY_NAME = "useConfig";

    static final boolean USE_CONFIG = Boolean.getBoolean(USE_CONFIG_PROPERTY_NAME);

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
        WebServerConfig.Builder builder = WebServer.builder();
        setup(builder);
        WebServer server = builder.build().start();
        System.out.println("WEB server is up! http://localhost:" + server.port() + "/greet");
    }

    /**
     * Set up the server.
     *
     * @param server server builder
     */
    static void setup(WebServerConfig.Builder server) {
        // load logging configuration
        LogConfig.configureRuntime();

        // By default, this will pick up application.yaml from the classpath
        Config config = Config.create();
        Config.global(config);

        /*
         * For purposes of illustration, the key performance indicator settings for the
         * MetricsSupport instance are set up according to a system property, so you can see,
         * in one example, how to code each approach. Normally, you would choose one
         * approach to use in an application.
         */
        MetricsObserver metricsObserver = USE_CONFIG
                ? metricsSupportWithConfig(config.get("metrics"))
                : metricsSupportWithoutConfig();


        server.addFeature(ObserveFeature.just(metricsObserver))
                .routing(Main::routing)
                .config(config.get("server"));

    }

    /**
     * Setup routing.
     *
     * @param routing routing builder
     */
    private static void routing(HttpRouting.Builder routing) {

        routing.register("/greet", new GreetService());
    }

    /**
     * Creates a {@link MetricsObserver} instance using a "metrics" configuration node.
     *
     * @param metricsConfig {@link Config} node with key "metrics" if present; an empty node otherwise
     * @return {@code MetricsSupport} object with metrics (including KPI) set up using the config node
     */
    private static MetricsObserver metricsSupportWithConfig(Config metricsConfig) {
        return MetricsObserver.create(metricsConfig);
    }

    /**
     * Creates a {@link MetricsObserver} instance explicitly turning on extended KPI metrics.
     *
     * @return {@code MetricsSupport} object with extended KPI metrics enabled
     */
    private static MetricsObserver metricsSupportWithoutConfig() {
        KeyPerformanceIndicatorMetricsConfig.Builder configBuilder =
                KeyPerformanceIndicatorMetricsConfig.builder()
                        .extended(true)
                        .longRunningRequestThreshold(Duration.ofSeconds(2));
        return MetricsObserver.builder()
                .metricsConfig(MetricsConfig.builder()
                        .keyPerformanceIndicatorMetricsConfig(configBuilder))
                .build();
    }
}
