/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.jaeger;

import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.grpc.server.GrpcTracingConfig;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import io.opentracing.Tracer;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

class TestMultipleJaegerClients {

    @BeforeEach
    @AfterEach
    void clearVendorRegistry() {
        RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.VENDOR).removeMatching((metricId, Metric) -> true);
    }

    @Test
    void checkMultipleJaegerClientsReuseMetric() {

        Config config = Config.create();
        Tracer tracer = TracerBuilder.create(config.get("tracing")).build();

        WebServer webServer = WebServer.builder(createRouting(config))
                .tracer(tracer)
                .config(config.get("server"))
                .build();

        GrpcTracingConfig grpcTracingConfig = GrpcTracingConfig.create();
        GrpcServerConfiguration grpcServerconfig = GrpcServerConfiguration.builder(config.get("grpc"))
                .tracer(tracer)
                .tracingConfig(grpcTracingConfig)
                .build();

        GrpcServer grpcServer = GrpcServer.create(grpcServerconfig, GrpcRouting.builder().build());

        webServer.start()
                .thenAccept(ws -> ws.whenShutdown().thenRun(() -> System.err.println("Web server has stopped.")))
                .exceptionallyAccept(t -> fail("WebServer failed to start: " + t.getMessage()));

        grpcServer.start()
                .thenAccept(gs -> gs.whenShutdown().thenRun(() -> System.err.println("gRPC server has stopped.")))
                .exceptionally(t -> {
                    fail("gRPC server failed to start: " + t.getMessage());
                    return null;
                });

        try {
            TimeUnit.SECONDS.sleep(4);
        } catch (InterruptedException e) {
            fail("Error waiting for servers to warm up: ", e);
        }
    }

    private Routing createRouting(Config config) {
        return Routing.builder()
                .get((req, resp) -> req.next())
                .build();
    }
}
