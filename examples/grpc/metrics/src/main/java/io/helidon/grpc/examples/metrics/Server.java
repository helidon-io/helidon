/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.grpc.examples.metrics;

import java.util.logging.LogManager;

import io.helidon.config.Config;
import io.helidon.grpc.examples.common.GreetService;
import io.helidon.grpc.examples.common.StringService;
import io.helidon.grpc.metrics.GrpcMetrics;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.metrics.MetricsSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * A basic example of a Helidon gRPC server.
 */
public class Server {

    private Server() {
    }

    /**
     * The main program entry point.
     *
     * @param args  the program arguments
     *
     * @throws Exception  if an error occurs
     */
    public static void main(String[] args) throws Exception {
        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // load logging configuration
        LogManager.getLogManager().readConfiguration(
                Server.class.getResourceAsStream("/logging.properties"));

        // Get gRPC server config from the "grpc" section of application.yaml
        GrpcServerConfiguration serverConfig =
                GrpcServerConfiguration.builder(config.get("grpc")).build();

        GrpcRouting grpcRouting = GrpcRouting.builder()
                        .intercept(GrpcMetrics.counted()) // global metrics - all service methods counted
                        .register(new GreetService(config))  // GreetService uses global metrics so all methods are counted
                        .register(new StringService(), rules -> {
                            // service level metrics - StringService overrides global so that its methods are timed
                            rules.intercept(GrpcMetrics.timed())
                                 // method level metrics - overrides service and global
                                 .intercept("Upper", GrpcMetrics.histogram());
                        })
                        .build();

        GrpcServer grpcServer = GrpcServer.create(serverConfig, grpcRouting);

        // Try to start the server. If successful, print some info and arrange to
        // print a message at shutdown. If unsuccessful, print the exception.
        grpcServer.start()
                .thenAccept(s -> {
                    System.out.println("gRPC server is UP! http://localhost:" + s.port());
                    s.whenShutdown().thenRun(() -> System.out.println("gRPC server is DOWN. Good bye!"));
                })
                .exceptionally(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                    return null;
                });

        // start web server with the metrics endpoints
        Routing routing = Routing.builder()
                .register(MetricsSupport.create())
                .build();

        WebServer.create(routing, config.get("webserver"))
                .start()
                .thenAccept(s -> {
                    System.out.println("HTTP server is UP! http://localhost:" + s.port());
                    s.whenShutdown().thenRun(() -> System.out.println("HTTP server is DOWN. Good bye!"));
                })
                .exceptionally(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                    return null;
                });
    }
}
