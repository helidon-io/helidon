/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;

import static io.helidon.config.ConfigSources.classpath;

/**
 * The application main class.
 */
public class DbClientITMain {

    /**
     * Cannot be instantiated.
     */
    private DbClientITMain() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        LogConfig.configureRuntime();
        Config config = Config.create(
                classpath("db.yaml"),
                classpath("db-common.yaml"));
        Config.global(config);

        DbClient db = DbClient.create(config.get("db"));
        if (config.get("db.create-schema").asBoolean().orElse(false)) {
            DBHelper.createSchema(db);
        }
        if (config.get("db.insert-dataset").asBoolean().orElse(false)) {
            DBHelper.insertDataSet(db);
        }

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .update(r -> setup(db, config, r))
                .build()
                .start();
        server.context().register(server);

        System.out.println("WEB server is up! http://localhost:" + server.port());
    }

    /**
     * Set up the server.
     *
     * @param dbClient db client
     * @param config   config
     * @param builder  builder
     */
    public static void setup(DbClient dbClient, Config config, WebServerConfig.Builder builder) {
        builder.addFeature(observeFeature(dbClient, config, "healthNoDetails", "noDetails", false));
        builder.addFeature(observeFeature(dbClient, config, "healthDetails", "details", true));
        builder.routing(r -> r.register("/test", new TestService(dbClient, config)));
    }

    private static ObserveFeature observeFeature(DbClient dbClient,
                                                 Config config,
                                                 String name,
                                                 String endpoint,
                                                 boolean details) {
        return ObserveFeature.builder()
                .observersDiscoverServices(false)
                .endpoint(endpoint)
                .name(name)
                .addObserver(HealthObserver.builder()
                        .addCheck(DbClientHealthCheck.builder(dbClient)
                                .config(config.get("db.health-check"))
                                .name(name)
                                .build())
                        .details(details)
                        .build())
                .build();
    }
}
