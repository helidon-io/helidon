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
import io.helidon.config.Config;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.WebServer;
import io.helidon.tracing.TracerBuilder;

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
     * @return the created {@link io.helidon.reactive.webserver.WebServer} instance
     */
    static WebServer startServer() {

        // Load logging configuration
        LogConfig.configureRuntime();

        // Load service configuration from .yaml file
        Config config = Config.create();

        // Prepare routing for the server
        Routing routing = createRouting(config);

        WebServer server = WebServer.builder(routing)
                .config(config.get("server"))
                .tracer(TracerBuilder.create(config.get("tracing")).build())
                .build();

        // Start the server and print some info.
        server.start()
                .thenAccept(ws -> System.out.println("WEB server is up! http://localhost:" + ws.port() + "/"));

        // Server threads are not daemon. NO need to block. Just react.
        server.whenShutdown()
                .thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));

        return server;
    }

    /**
     * Creates new {@link io.helidon.reactive.webserver.Routing}.
     *
     * @param config configuration of this server
     * @return routing configured with JSON support, a health check, and a service
     */
    private static Routing createRouting(Config config) {
        return Routing.builder()
                .register("/example", new PokemonService())
                .build();
    }
}
