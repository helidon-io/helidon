/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.neo4j.se;

import io.helidon.config.Config;
import io.helidon.examples.integrations.neo4j.se.domain.MovieRepository;
import io.helidon.health.checks.DeadlockHealthCheck;
import io.helidon.health.checks.DiskSpaceHealthCheck;
import io.helidon.health.checks.HeapMemoryHealthCheck;
import io.helidon.integrations.neo4j.Neo4j;
import io.helidon.integrations.neo4j.health.Neo4jHealthCheck;
import io.helidon.logging.common.LogConfig;
import io.helidon.nima.http.media.jsonp.JsonpSupport;
import io.helidon.nima.observe.ObserveFeature;
import io.helidon.nima.observe.health.HealthFeature;
import io.helidon.nima.observe.health.HealthObserveProvider;
import io.helidon.nima.webserver.WebServer;

import org.neo4j.driver.Driver;

import static io.helidon.nima.webserver.http.HttpRouting.Builder;

/**
 * The application main class.
 */
public class Main {
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
    public static void main(String[] args) {
        // load logging configuration
        LogConfig.configureRuntime();

        startServer();
    }

    static void startServer() {

        Config config = Config.create();
        WebServer server = WebServer.builder()
                .addMediaSupport(JsonpSupport.create(config))
                .routing(Main::routing)
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/api/movies");
    }

    /**
     * Updates HTTP Routing.
     */
    static void routing(Builder routing) {

        Neo4j neo4j = Neo4j.create(Config.create().get("neo4j"));

        Neo4jHealthCheck healthCheck = Neo4jHealthCheck.create(neo4j.driver());

        Driver neo4jDriver = neo4j.driver();

        MovieService movieService = new MovieService(new MovieRepository(neo4jDriver));

        ObserveFeature observe = ObserveFeature.builder()
                .useSystemServices(false)
                .addProvider(HealthObserveProvider.create(HealthFeature.builder()
                                                                  .useSystemServices(false)
                                                                  .addCheck(HeapMemoryHealthCheck.create())
                                                                  .addCheck(DiskSpaceHealthCheck.create())
                                                                  .addCheck(DeadlockHealthCheck.create())
                                                                  .addCheck(healthCheck)
                                                                  .build()))
                .build();

        routing.register(movieService)
                .addFeature(observe);
    }
}

