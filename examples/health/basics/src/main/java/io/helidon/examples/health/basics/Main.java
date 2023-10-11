/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.health.basics;

import java.time.Duration;

import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;

/**
 * Main class of health check integration example.
 */
public final class Main {

    private static long serverStartTime;

    private Main() {
    }

    /**
     * Start the example. Prints endpoints to standard output.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        serverStartTime = System.currentTimeMillis();

        // load logging
        LogConfig.configureRuntime();

        ObserveFeature observe = ObserveFeature.builder()
                .observersDiscoverServices(true)
                .addObserver(HealthObserver.builder()
                                     .useSystemServices(true)
                                     .addCheck(() -> HealthCheckResponse.builder()
                                             .status(HealthCheckResponse.Status.UP)
                                             .detail("time", System.currentTimeMillis())
                                             .build(), HealthCheckType.READINESS)
                                     .addCheck(() -> HealthCheckResponse.builder()
                                             .status(isStarted())
                                             .detail("time", System.currentTimeMillis())
                                             .build(), HealthCheckType.STARTUP)
                                     .build())
                .build();

        WebServer server = WebServer.builder()
                .featuresDiscoverServices(false)
                .addFeature(observe)
                .routing(Main::routing)
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port());
    }

    /**
     * Set up HTTP routing.
     * This method is used from tests as well.
     *
     * @param router HTTP routing builder
     */
    static void routing(HttpRouting.Builder router) {
        router.get("/hello", (req, res) -> res.send("Hello World!"));
    }

    private static boolean isStarted() {
        return Duration.ofMillis(System.currentTimeMillis() - serverStartTime).getSeconds() >= 8;
    }
}
