/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.dbclient.jdbc;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;

/**
 * Simple Hello World rest application.
 */
public final class JdbcExampleMain {

    /**
     * Cannot be instantiated.
     */
    private JdbcExampleMain() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        // load logging configuration
        LogConfig.configureRuntime();

        // Prepare routing for the server
        WebServer server = setupServer(WebServer.builder());

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/");
    }

    static WebServer setupServer(WebServerConfig.Builder builder) {
        // By default, this will pick up application.yaml from the classpath
        Config config = Config.global();

        Config dbConfig = config.get("db");
        DbClient dbClient = DbClient.create(dbConfig);

        ObserveFeature observe = ObserveFeature.builder()
                .config(config.get("server.features.observe"))
                .addObserver(HealthObserver.builder()
                        .addCheck(DbClientHealthCheck.create(dbClient, dbConfig.get("health-check")))
                        .build())
                .build();

        return builder
                .config(config.get("server"))
                .addFeature(observe)
                .routing(routing -> routing.register("/db", new PokemonService(dbClient)))
                .build()
                .start();
    }
}
