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
 */

package io.helidon.examples.rsocket.server;

import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HealthChecks;
import io.helidon.metrics.MetricsSupport;
import io.helidon.rsocket.health.RSocketHealthCheck;
import io.helidon.rsocket.server.RSocketEndpoint;
import io.helidon.rsocket.server.RSocketRouting;
import io.helidon.rsocket.server.RSocketSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;


/**
 * Application demonstrates combination of websocket and REST.
 */
public class MainWS {

    private MainWS() {
    }

    /**
     * Creates new {@link Routing}.
     *
     * @return the new instance
     */
    static Routing createRouting() {

        MyRSocketService myRSocketService = new MyRSocketService();

        MetricsSupport metrics = MetricsSupport.create();

        RSocketRouting rSocketRouting = RSocketRouting.builder()
                .register(myRSocketService)
                .build();

        HealthSupport health = HealthSupport.builder()
                .addLiveness(HealthChecks.healthChecks())   // Adds a convenient set of checks
                .addLiveness(RSocketHealthCheck.create())   // Adds RSocket checks
                .build();

        Routing build = Routing.builder()
                .register(health)
                .register(metrics)
                .register("/rsocket",
                        RSocketSupport.builder()
                                .register(RSocketEndpoint.create(rSocketRouting, "/board")
                                        .getEndPoint()
                                ).build())
                .build();

        return build;
    }

    static RSocketRouting rSocketRouting(){

        MyRSocketService myRSocketService = new MyRSocketService();

        RSocketRouting rSocketRouting = RSocketRouting.builder()
                .register(myRSocketService)
                .build();

        return rSocketRouting;
    }


    static WebServer startWebServer() {

        WebServer server = WebServer.builder(createRouting())
                .port(8080)
                .build()
                .start()
                .await();
        System.out.println("WEB server is up! http://localhost:" + server.port());

        return server;
    }

    /**
     * A java main class.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        WebServer server = startWebServer();

        // Server threads are not demon. NO need to block. Just react.
        server.whenShutdown()
                .thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));

    }
}
