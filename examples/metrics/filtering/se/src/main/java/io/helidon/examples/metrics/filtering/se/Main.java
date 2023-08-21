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

import java.util.Map;

import io.helidon.common.config.GlobalConfig;
import io.helidon.common.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.logging.common.LogConfig;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.ScopeConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.metrics.MetricsFeature;
import io.helidon.webserver.observe.metrics.MetricsObserveProvider;

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
        Config config = GlobalConfig.config();

        // Programmatically (not through config), tell the metrics feature to ignore the "gets" timer.
        // To do so, create the scope config, then add it to the metrics config that ultimately
        // the metrics feature class will use.
        ScopeConfig scopeConfig = ScopeConfig.builder()
                .name(Meter.Scope.APPLICATION)
                .exclude(GreetService.TIMER_FOR_GETS)
                .build();

        MetricsConfig initialMetricsConfig = config.get(MetricsConfig.METRICS_CONFIG_KEY)
                .as(MetricsConfig.class)
                .orElseGet(MetricsConfig::create);
        MetricsConfig.Builder metricsConfigBuilder = MetricsConfig.builder(initialMetricsConfig)
                .addScopes(Map.of(Meter.Scope.APPLICATION, scopeConfig));

        server.config(config.get("server"))
              .routing(r -> routing(r, config, metricsConfigBuilder));
    }

    /**
     * Set up routing.
     *
     * @param routing routing builder
     * @param config  configuration of this server
     */
    static void routing(HttpRouting.Builder routing, Config config, MetricsConfig.Builder metricsConfigBuilder) {
        MeterRegistry meterRegistry = MetricsFactory.getInstance(config).globalRegistry();

        MetricsFeature metrics = MetricsFeature.builder()
                .metricsConfig(metricsConfigBuilder)
                .build();
        GreetService greetService = new GreetService(config, meterRegistry);

        routing.addFeature(ObserveFeature.create(MetricsObserveProvider.create(metrics)))
                .register("/greet", greetService);
    }
}
