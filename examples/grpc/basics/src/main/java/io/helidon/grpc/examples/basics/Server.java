/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.grpc.examples.basics;

import io.helidon.config.Config;
import io.helidon.grpc.examples.common.GreetService;
import io.helidon.grpc.examples.common.GreetServiceJava;
import io.helidon.grpc.examples.common.StringService;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.logging.common.LogConfig;
import io.helidon.nima.observe.ObserveFeature;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;

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
     */
    public static void main(String[] args) {
        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // load logging configuration
        LogConfig.configureRuntime();

        // Get gRPC server config from the "grpc" section of application.yaml
        GrpcServerConfiguration serverConfig =
                GrpcServerConfiguration.builder(config.get("grpc")).build();

        GrpcServer grpcServer = GrpcServer.create(serverConfig, createGrpcRouting(config));

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

        WebServer server = WebServer.builder()
                .routing(Server::routing)
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port());
    }

    private static void routing(HttpRouting.Builder routing) {
        routing.addFeature(ObserveFeature.create());
    }

    private static GrpcRouting createGrpcRouting(Config config) {
        GreetService greetService = new GreetService(config);
        GreetServiceJava greetServiceJava = new GreetServiceJava(config);

        return GrpcRouting.builder()
                .register(greetService)
                .register(greetServiceJava)
                .register(new StringService())
                .build();
    }
}
