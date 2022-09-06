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
import io.helidon.config.ConfigSources;
import io.helidon.examples.data.pokemons.dao.PokemonDao;
import io.helidon.reactive.dbclient.DbClient;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.WebServer;
import io.helidon.tracing.TracerBuilder;
import jakarta.persistence.EntityManager;

/**
 * Simple Hello World rest application.
 */
public final class PokemonMain {

    /** MongoDB configuration. Default configuration file {@code appliaction.yaml} contains JDBC configuration. */
    private static final String MONGO_CFG = "mongo.yaml";

    /** Whether MongoDB support is selected. */
    private static boolean mongo;

    static boolean isMongo() {
        return mongo;
    }

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
        if (args != null && args.length > 0 && args[0] != null && "mongo".equals(args[0].toLowerCase())) {
            System.out.println("MongoDB database selected");
            mongo = true;
        } else {
            System.out.println("JDBC database selected");
            mongo = false;
        }
        startServer();
    }

    /**
     * Start the server.
     *
     * @return the created {@link io.helidon.reactive.webserver.WebServer} instance
     */
    static WebServer startServer() {

        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = isMongo() ? Config.create(ConfigSources.classpath(MONGO_CFG)) : Config.create();

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
        Config dbConfig = config.get("db");

        // Client services are added through a service loader - see mongoDB example for explicit services
        DbClient dbClient = DbClient.builder(dbConfig)
                .build();

        // Initialize database schema
        InitializeDb.init(dbClient);

        // TODO: Initialization
        EntityManager entityManager = null;

        return Routing.builder()
                .register("/db", new PokemonService(entityManager))
                .build();
    }
}
