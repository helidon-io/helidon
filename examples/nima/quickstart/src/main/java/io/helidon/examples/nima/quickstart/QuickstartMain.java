/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.examples.nima.quickstart;

import io.helidon.health.checks.DeadlockHealthCheck;
import io.helidon.health.checks.DiskSpaceHealthCheck;
import io.helidon.health.checks.HeapMemoryHealthCheck;
import io.helidon.logging.common.LogConfig;
import io.helidon.nima.observe.ObserveFeature;
import io.helidon.nima.observe.health.HealthFeature;
import io.helidon.nima.observe.health.HealthObserveProvider;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;

/**
 * The application main class.
 */
public class QuickstartMain {
    /**
     * Cannot be instantiated.
     */
    private QuickstartMain() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        // load logging configuration
        LogConfig.configureRuntime();

        WebServer server = WebServer.builder()
                .routing(QuickstartMain::routing)
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/greet");
    }

    /**
     * Updates HTTP Routing.
     */
    static void routing(HttpRouting.Builder routing) {
        ObserveFeature observe = ObserveFeature.builder()
                .useSystemServices(false)
                .addProvider(HealthObserveProvider.create(HealthFeature.builder()
                                                                  .useSystemServices(false)
                                                                  .addCheck(HeapMemoryHealthCheck.create())
                                                                  .addCheck(DiskSpaceHealthCheck.create())
                                                                  .addCheck(DeadlockHealthCheck.create())
                                                                  .build()))
                .build();

        GreetService greetService = new GreetService();

        routing.register("/greet", greetService)
                .addFeature(observe);
    }
}
