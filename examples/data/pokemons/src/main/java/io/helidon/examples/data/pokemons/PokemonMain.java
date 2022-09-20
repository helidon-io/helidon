/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
package io.helidon.examples.data.pokemons;

import io.helidon.common.LogConfig;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;


/**
 * Simple Hello World rest application.
 */
public final class PokemonMain {

    /**
     * Cannot be instantiated.
     */
    private PokemonMain() {
    }

    /**
     * Application main entry point.
     *
     * @param args Command line arguments. Run with MongoDB support when 1st argument is mongo, run with JDBC support otherwise.
     */
    public static void main(final String[] args) {
        startServer();
    }

    /**
     * Start the server.
     *
     * @return the created {@link io.helidon.nima.webserver.WebServer} instance
     */
    static WebServer startServer() {

        // Load logging configuration
        LogConfig.configureRuntime();

        WebServer server = WebServer.builder()
                .routing(PokemonMain::routing)
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/greet");

        return server;
    }

    /**
     * Updates HTTP Routing.
     *
     * @param routing routing builder
     */
    private static void routing(HttpRouting.Builder routing) {
        routing.register("/example", new PokemonService())
                .build();
    }

}
