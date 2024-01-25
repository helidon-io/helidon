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
package io.helidon.docs.se;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.health.checks.HealthChecks;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;

@SuppressWarnings("ALL")
class HealthSnippets {

    static long serverStartTime = 0;

    class Snippet1 {

        // tag::snippet_1[]
        static HealthCheckResponse slowStartLivenessResponse() {
            long now = System.currentTimeMillis();
            return HealthCheckResponse.builder()
                    .detail("time", now)
                    .status(now - serverStartTime >= 8000)
                    .build();
        }
        // end::snippet_1[]
    }

    // stub
    class Main {

        static HealthCheckResponse slowStartLivenessResponse() {
            return HealthCheckResponse.builder()
                    .build();
        }

        static void routing(HttpRouting.Builder routing) {
        }
    }

    void snippet_2(Config config) {
        // tag::snippet_2[]
        ObserveFeature observe = ObserveFeature.builder()
                .config(config.get("server.features.observe")) // <1>
                .addObserver(HealthObserver.builder() // <2>
                                     .useSystemServices(true) // <3>
                                     .addCheck(Main::slowStartLivenessResponse, // <4>
                                               HealthCheckType.LIVENESS, // <5>
                                               "live-after-8-seconds") // <6>
                                     .build())
                .build();
        // end::snippet_2[]
    }

    void snippet_3(Config config) {
        // tag::snippet_3[]
        ObserveFeature observe = ObserveFeature.builder()
                .config(config.get("server.features.observe"))
                .addObserver(HealthObserver.builder() // <1>
                                     .useSystemServices(true) // Include Helidon-provided health checks.
                                     .addCheck(() -> HealthCheckResponse.builder() // <2>
                                                       .status(System.currentTimeMillis() - serverStartTime >= 8000) // <3>
                                                       .detail("time", System.currentTimeMillis()) // <4>
                                                       .build(), // <5>
                                               HealthCheckType.READINESS, // <6>
                                               "live-after-8-seconds") // <7>
                                     .build())
                .build();
        // end::snippet_3[]
    }

    // tag::snippet_4[]
    /**
     * A custom readiness health check that reports UP 8 seconds after server start-up.
     */
    class SlowStartHealthCheck implements HealthCheck { // <1>

        @Override
        public HealthCheckType type() {
            return HealthCheckType.READINESS; // <2>
        }

        @Override
        public HealthCheckResponse call() {
            long now = System.currentTimeMillis();
            return HealthCheckResponse.builder()
                    .detail("time", now) // <3>
                    .status(now - serverStartTime >= 8000) // <4>
                    .build();
        }
    }
    // end::snippet_4[]

    void snippet_5(Config config) {
        // tag::snippet_5[]
        ObserveFeature observe = ObserveFeature.builder()
                .config(config.get("server.features.observe"))
                .addObserver(HealthObserver.builder() // <1>
                                     .addCheck(new SlowStartHealthCheck()) // <2>
                                     .build())
                .build();
        // end::snippet_5[]
    }

    void snippet_6(ObserveFeature observe) {
        // tag::snippet_6[]
        WebServer server = WebServer.builder()
                .featuresDiscoverServices(false)
                .addFeature(observe) // <1>
                .routing(Main::routing)
                .build()
                .start();
        // end::snippet_6[]
    }

    void snippet_7(Config config, HealthCheck hc) {
        // tag::snippet_7[]
        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .addFeature(ObserveFeature.create(HealthObserver.builder()
                                                          .useSystemServices(false) // <1>
                                                          .addCheck(HealthChecks.deadlockCheck()) // <2>
                                                          .addCheck(hc) // <3>
                                                          .details(true)
                                                          .build()))
                .routing(Main::routing)
                .build()
                .start();
        // end::snippet_7[]
    }

    void snippet_8() {
        // tag::snippet_8[]
        ObserveFeature observeFeature = ObserveFeature.builder()
                .addObserver(HealthObserver.builder()
                                     .useSystemServices(false)
                                     .endpoint("/health/live") // <1>
                                     .addChecks(HealthChecks.healthChecks()) // <2>
                                     .build())
                .addObserver(HealthObserver.builder()
                                     .useSystemServices(false)
                                     .endpoint("/health/ready") // <3>
                                     .addCheck(() -> HealthCheckResponse.builder() // <4>
                                                       .status(true)
                                                       .build(),
                                               HealthCheckType.READINESS,
                                               "database")
                                     .build())
                .sockets(List.of("observe")) // <5>
                .build();
        WebServer server = WebServer.builder()
                .putSocket("@default", socket -> socket
                        .port(8080) // <6>
                        .routing(r -> r.any((req, res) -> res.send("It works!")))) // <7>
                .addFeature(observeFeature)
                .putSocket("observe", socket -> socket
                        .port(8081)) // <8>
                .build()
                .start();
        // end::snippet_8[]
    }
}
