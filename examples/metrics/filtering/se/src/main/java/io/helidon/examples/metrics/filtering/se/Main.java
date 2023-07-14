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

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.metrics.api.MetricsSettings;
import io.helidon.metrics.api.Registry;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.metrics.api.RegistryFilterSettings;
import io.helidon.metrics.api.RegistrySettings;
import io.helidon.nima.observe.metrics.MetricsFeature;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig;
import io.helidon.nima.webserver.http.HttpRouting;

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

        // Ignore the "gets" timer.
        RegistryFilterSettings.Builder registryFilterSettingsBuilder =
                RegistryFilterSettings.builder()
                                      .exclude(GreetService.TIMER_FOR_GETS);

        RegistrySettings.Builder registrySettingsBuilder =
                RegistrySettings.builder()
                                .filterSettings(registryFilterSettingsBuilder);

        MetricsSettings.Builder metricsSettingsBuilder =
                MetricsSettings.builder()
                               .registrySettings(Registry.APPLICATION_SCOPE, registrySettingsBuilder.build());

        server.config(config.get("server"))
              .routing(r -> routing(r, config, metricsSettingsBuilder));
    }

    /**
     * Set up routing.
     *
     * @param routing routing builder
     * @param config  configuration of this server
     */
    static void routing(HttpRouting.Builder routing, Config config, MetricsSettings.Builder metricsSettingsBuilder) {
        MetricsFeature metrics = MetricsFeature.builder()
                .metricsSettings(metricsSettingsBuilder)
                .build();
        MetricRegistry appRegistry = RegistryFactory.getInstance(metricsSettingsBuilder.build())
                .getRegistry(MetricRegistry.APPLICATION_SCOPE);

        GreetService greetService = new GreetService(config, appRegistry);

        routing.addFeature(metrics)
                .register("/greet", greetService);
    }
}
