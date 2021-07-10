/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.lra.coordinator;

import io.helidon.common.LogConfig;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HealthChecks;
import io.helidon.metrics.MetricsSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * In memory Lra coordinator.
 */
public class Main {

    private Main() {
    }

    /**
     * Main method to start Helidon LRA coordinator.
     *
     * @param args are not used
     */
    public static void main(String[] args) {

        LogConfig.configureRuntime();

        Config config = Config.create();

        CoordinatorService coordinatorService = CoordinatorService.builder().build();

        WebServer server = WebServer.builder(createRouting(config, coordinatorService))
                .config(config.get("server"))
                .build();

        Single<WebServer> webserver = server.start();

        webserver.thenAccept(ws -> {
            System.out.println("Helidon LRA Coordinator is up! http://localhost:" + ws.port() + "/lra-coordinator");
            ws.whenShutdown()
                    .thenRun(() -> {
                        System.out.println("Helidon LRA Coordinator is DOWN. Good bye!");
                    });
        }).exceptionallyAccept(t -> {
            System.err.println("Startup failed: " + t.getMessage());
            t.printStackTrace(System.err);
        });
    }

    private static Routing createRouting(Config config,
                                         CoordinatorService coordinatorService) {

        MetricsSupport metrics = MetricsSupport.create();
        HealthSupport health = HealthSupport.builder()
                .addLiveness(HealthChecks.healthChecks())
                .build();

        return Routing.builder()
                .register(health)                   // Health at "/health"
                .register(metrics)                  // Metrics at "/metrics"
                .register(config.get("mp.lra.coordinator.context.path")
                        .asString()
                        .orElse("/lra-coordinator"), coordinatorService)
                .build();
    }
}
