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

package io.helidon.grpc.client;

import java.util.logging.LogManager;

import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;

import services.EchoService;

/**
 * A test gRPC server.
 */
public class TestServer {
    /**
     * Program entry point.
     *
     * @param args the program command line arguments
     * @throws Exception if there is a program error
     */
    public static void main(String[] args) throws Exception {
        LogManager.getLogManager().readConfiguration(TestServer.class.getResourceAsStream("/logging.properties"));

        // Add the EchoService and enable GrpcMetrics
        GrpcRouting routing = GrpcRouting.builder()
                                         .register(new EchoService())
                                         .build();

        // Run the server on port 0 so that it picks a free ephemeral port
        GrpcServerConfiguration serverConfig = GrpcServerConfiguration.builder().build();

        GrpcServer.create(serverConfig, routing)
                        .start()
                .thenAccept(s -> {
                        System.out.println("gRPC server is UP and listening on localhost:" + s.port());
                        s.whenShutdown().thenRun(() -> System.out.println("gRPC server is DOWN. Good bye!"));
                        })
                .exceptionally(t -> {
                        System.err.println("Startup failed: " + t.getMessage());
                        t.printStackTrace(System.err);
                        return null;
                        });
    }
}
