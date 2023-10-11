/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.employee;

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.staticcontent.StaticContentService;

/**
 * Simple Employee rest application.
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
        System.out.printf("""
                WEB server is up!
                Web client at: http://localhost:%1$d/public/index.html
                """, server.port());
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

        // Get webserver config from the "server" section of application.yaml and JSON support registration
        server.config(config.get("server"))
              .routing(r -> routing(r, config));
    }

    /**
     * Setup routing.
     *
     * @param routing routing builder
     * @param config  configuration of this server
     */
    static void routing(HttpRouting.Builder routing, Config config) {
        routing.register("/public", StaticContentService.builder("public")
                                                        .welcomeFileName("index.html"))
               .register("/employees", new EmployeeService(config));
    }

}
