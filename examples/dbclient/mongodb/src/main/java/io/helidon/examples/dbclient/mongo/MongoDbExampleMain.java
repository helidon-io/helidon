/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.examples.dbclient.mongo;

import java.io.IOException;
import java.util.logging.LogManager;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.dbclient.metrics.DbCounter;
import io.helidon.dbclient.metrics.DbTimer;
import io.helidon.dbclient.tracing.DbClientTracing;
import io.helidon.health.HealthSupport;
import io.helidon.media.jsonb.common.JsonbSupport;
import io.helidon.media.jsonp.common.JsonpSupport;
import io.helidon.metrics.MetricsSupport;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * Simple Hello World rest application.
 */
public final class MongoDbExampleMain {

    /**
     * Cannot be instantiated.
     */
    private MongoDbExampleMain() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     * @throws java.io.IOException if there are problems reading logging properties
     */
    public static void main(final String[] args) throws IOException {
        startServer();
    }

    /**
     * Start the server.
     *
     * @return the created {@link io.helidon.webserver.WebServer} instance
     * @throws java.io.IOException if there are problems reading logging properties
     */
    static WebServer startServer() throws IOException {

        // load logging configuration
        LogManager.getLogManager().readConfiguration(
                MongoDbExampleMain.class.getResourceAsStream("/logging.properties"));

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        WebServer server = WebServer.builder(createRouting(config))
                .config(config.get("server"))
                .tracer(TracerBuilder.create("mongo-db").build())
                .addMediaSupport(JsonpSupport.create())
                .addMediaSupport(JsonbSupport.create())
                .build();

        // Start the server and print some info.
        server.start().thenAccept(ws -> {
            System.out.println(
                    "WEB server is up! http://localhost:" + ws.port() + "/");
        });

        // Server threads are not daemon. NO need to block. Just react.
        server.whenShutdown().thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));

        return server;
    }

    /**
     * Creates new {@link io.helidon.webserver.Routing}.
     *
     * @param config configuration of this server
     * @return routing configured with JSON support, a health check, and a service
     */
    private static Routing createRouting(Config config) {
        Config dbConfig = config.get("db");

        DbClient dbClient = DbClient.builder(dbConfig)
                // add an interceptor to named statement(s)
                .addInterceptor(DbCounter.create(), "select-all", "select-one")
                // add an interceptor to statement type(s)
                .addInterceptor(DbTimer.create(), DbStatementType.DELETE, DbStatementType.UPDATE, DbStatementType.INSERT)
                // add an interceptor to all statements
                .addInterceptor(DbClientTracing.create())
                .build();

        HealthSupport health = HealthSupport.builder()
                .addLiveness(DbClientHealthCheck.create(dbClient))
                .build();

        return Routing.builder()
                .register(health)                   // Health at "/health"
                .register(MetricsSupport.create())  // Metrics at "/metrics"
                .register("/db", new PokemonService(dbClient))
                .build();
    }

    private static IllegalStateException noConfigError(String key) {
        return new IllegalStateException("Attempting to create a Pokemon service with no configuration"
                                                 + ", config key: " + key);
    }

}
