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

package io.helidon.examples.integrations.neo4j;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.examples.integrations.neo4j.domain.MovieRepository;
import io.helidon.health.checks.DeadlockHealthCheck;
import io.helidon.health.checks.DiskSpaceHealthCheck;
import io.helidon.health.checks.HeapMemoryHealthCheck;
import io.helidon.integrations.neo4j.Neo4j;
import io.helidon.integrations.neo4j.health.Neo4jHealthCheck;
import io.helidon.integrations.neo4j.metrics.Neo4jMetricsSupport;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;
import io.helidon.webserver.spi.ServerFeature;

import org.neo4j.driver.Driver;

import static io.helidon.webserver.http.HttpRouting.Builder;

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
        Neo4j neo4j = Neo4j.create(Config.create().get("neo4j"));
        Driver neo4jDriver = neo4j.driver();

        WebServer server = WebServer.builder()
                .featuresDiscoverServices(false)
                .features(features(neo4jDriver))
                .routing(it -> routing(it, neo4jDriver))
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/api/movies");
    }

    static List<ServerFeature> features(Driver neo4jDriver) {
        Neo4jHealthCheck healthCheck = Neo4jHealthCheck.create(neo4jDriver);
        return List.of(ObserveFeature.just(HealthObserver.builder()
                                                             .useSystemServices(false)
                                                             .addCheck(HeapMemoryHealthCheck.create())
                                                             .addCheck(DiskSpaceHealthCheck.create())
                                                             .addCheck(DeadlockHealthCheck.create())
                                                             .addCheck(healthCheck)
                                                             .build()));
    }

    /**
     * Updates HTTP Routing.
     */
    static void routing(Builder routing, Driver neo4jDriver) {


        Neo4jMetricsSupport.builder()
                           .driver(neo4jDriver)
                           .build()
                           .initialize();


        MovieService movieService = new MovieService(new MovieRepository(neo4jDriver));



        routing.register(movieService);
    }
}

