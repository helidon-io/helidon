/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.grpc.examples.opentracing;

import io.helidon.common.LogConfig;
import io.helidon.config.Config;
import io.helidon.grpc.examples.common.GreetService;
import io.helidon.grpc.examples.common.StringService;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.grpc.server.GrpcTracingConfig;
import io.helidon.grpc.server.ServerRequestAttribute;
import io.helidon.tracing.TracerBuilder;

import io.opentracing.Tracer;

/**
 * An example gRPC server with Zipkin tracing enabled.
 */
public class ZipkinExampleMain {

    private ZipkinExampleMain() {
    }

    /**
     * Program entry point.
     *
     * @param args the program command line arguments
     * @throws Exception if there is a program error
     */
    public static void main(String[] args) throws Exception {
        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // load logging configuration
        LogConfig.configureRuntime();

        Tracer tracer = TracerBuilder.create(config.get("tracing")).build();

        GrpcTracingConfig tracingConfig = GrpcTracingConfig.builder()
                .withStreaming()
                .withVerbosity()
                .withTracedAttributes(ServerRequestAttribute.CALL_ATTRIBUTES,
                                      ServerRequestAttribute.HEADERS,
                                      ServerRequestAttribute.METHOD_NAME)
                .build();

        // Get gRPC server config from the "grpc" section of application.yaml
        GrpcServerConfiguration serverConfig =
                GrpcServerConfiguration.builder(config.get("grpc")).tracer(tracer).tracingConfig(tracingConfig).build();

        GrpcServer grpcServer = GrpcServer.create(serverConfig, createRouting(config));

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
    }

    private static GrpcRouting createRouting(Config config) {
        return GrpcRouting.builder()
                .register(new GreetService(config))
                .register(new StringService())
                .build();
    }
}
