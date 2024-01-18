/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.eclipsestore.greetings.se;

import io.helidon.config.ClasspathConfigSource;
import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;

/**
 * Eclipsestore demo with a simple rest application.
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
        System.out.println(
          "WEB server is up! http://localhost:" + server.port() + "/greet");
    }

    static void setup(final WebServerConfig.Builder server) {
        LogConfig.configureRuntime();
        Config config =
                Config.builder()
                   .addSource(ClasspathConfigSource.create("/application.yaml"))
                   .build();

        // Build server with JSONP support
        server.config(config.get("server"))
              .routing(r -> routing(r, config));
    }

    /**
     * Setup routing.
     *
     * @param routing routing builder
     * @param config  configuration of this server
     */
    static void routing(final HttpRouting.Builder routing,
                        final Config config) {
        GreetingService greetService = new GreetingService(config);
        routing.register("/greet", greetService);
    }
}
