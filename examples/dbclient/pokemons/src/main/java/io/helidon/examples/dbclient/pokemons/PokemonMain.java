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

package io.helidon.examples.dbclient.pokemons;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.dbclient.DbClient;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserveFeature;

/**
 * Simple Hello World rest application.
 */
public final class PokemonMain {

    /**
     * MongoDB configuration. Default configuration file {@code application.yaml} contains JDBC configuration.
     */
    private static final String MONGO_CFG = "mongo.yaml";

    /**
     * Whether MongoDB support is selected.
     */
    private static boolean mongo;

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
        if (args != null && args.length > 0 && args[0] != null && "mongo".equalsIgnoreCase(args[0])) {
            System.out.println("MongoDB database selected");
            mongo = true;
        } else {
            System.out.println("JDBC database selected");
            mongo = false;
        }
        startServer();
    }

    private static void startServer() {

        // load logging configuration
        LogConfig.configureRuntime();

        // By default, this will pick up application.yaml from the classpath
        Config config = mongo ? Config.create(ConfigSources.classpath(MONGO_CFG)) : Config.create();


        // load logging configuration
        LogConfig.configureRuntime();

        WebServer server = WebServer.builder()
                .routing(routing -> PokemonMain.routing(routing, config))
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/");
    }

    /**
     * Updates HTTP Routing.
     */
    static void routing(HttpRouting.Builder routing, Config config) {
        Config dbConfig = config.get("db");

        // Client services are added through a service loader - see mongoDB example for explicit services
        DbClient dbClient = DbClient.builder(dbConfig)
                .build();

        // Initialize database schema
        InitializeDb.init(dbClient, !mongo);

        routing.register("/db", new PokemonService(dbClient))
                .addFeature(ObserveFeature.create());
    }
}
