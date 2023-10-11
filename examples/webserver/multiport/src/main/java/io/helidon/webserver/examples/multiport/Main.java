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

package io.helidon.webserver.examples.multiport;

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;

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
        // load logging configuration
        LogConfig.configureRuntime();

        // By default, this will pick up application.yaml from the classpath
        Config config = Config.create();
        Config.global(config);

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .update(Main::setup)
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port());
    }

    /**
     * Set up the server.
     */
    static void setup(WebServerConfig.Builder server) {
        // Build server using three ports:
        // default public port, admin port, private port
        server.routing(Main::publicRouting)
                .routing("private", Main::privateSocket);
    }

    /**
     * Set up private socket.
     */
    static void privateSocket(HttpRouting.Builder routing) {
        routing.get("/private/hello", (req, res) -> res.send("Private Hello!!"));
    }

    /**
     * Set up public routing.
     */
    static void publicRouting(HttpRouting.Builder routing) {
        routing.get("/hello", (req, res) -> res.send("Public Hello!!"));
    }
}
