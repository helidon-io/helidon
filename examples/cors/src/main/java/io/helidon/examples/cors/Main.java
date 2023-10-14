/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.cors;

import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.cors.CorsSupport;
import io.helidon.webserver.http.HttpRouting;

/**
 * Simple Hello World rest application.
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
        // load logging configuration
        LogConfig.configureRuntime();

        // initialize global config from default configuration
        Config config = Config.create();
        Config.global(config);

        // Get webserver config from the "server" section of application.yaml
        WebServerConfig.Builder builder = WebServer.builder();
        WebServer server = builder
                .config(config.get("server"))
                .routing(Main::routing)
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/greet");
    }

    /**
     * Setup routing.
     *
     * @param routing routing builder
     */
    static void routing(HttpRouting.Builder routing) {

        // Note: Add the CORS routing *before* registering the GreetService routing.
        routing.register("/greet", corsSupportForGreeting(), new GreetService());
    }

    private static CorsSupport corsSupportForGreeting() {
        Config config = Config.global();

        // The default CorsSupport object (obtained using CorsSupport.create()) allows sharing for any HTTP method and with any
        // origin. Using CorsSupport.create(Config) with a missing config node yields a default CorsSupport, which might not be
        // what you want. This example warns if either expected config node is missing and then continues with the default.

        Config restrictiveConfig = config.get("restrictive-cors");
        if (!restrictiveConfig.exists()) {
            Logger.getLogger(Main.class.getName())
                    .warning("Missing restrictive config; continuing with default CORS support");
        }

        CorsSupport.Builder corsBuilder = CorsSupport.builder();

        // Use possible overrides first.
        config.get("cors")
                .ifExists(c -> {
                    Logger.getLogger(Main.class.getName()).info("Using the override configuration");
                    corsBuilder.mappedConfig(c);
                });
        corsBuilder
                .config(restrictiveConfig) // restricted sharing for PUT, DELETE
                .addCrossOrigin(CrossOriginConfig.create()) // open sharing for other methods
                .build();

        return corsBuilder.build();
    }
}
