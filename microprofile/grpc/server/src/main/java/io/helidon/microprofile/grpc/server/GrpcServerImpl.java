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
package io.helidon.microprofile.grpc.server;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.context.Context;
import io.helidon.grpc.core.WeightedBag;
import io.helidon.webserver.grpc.ServiceDescriptor;

import io.grpc.ServerInterceptor;
import org.eclipse.microprofile.health.HealthCheck;

class GrpcServerImpl implements GrpcServer {

    static GrpcServerImpl create() {
        return new GrpcServerImpl();
    }

    static GrpcServerImpl create(GrpcServerConfiguration config) {
        return new GrpcServerImpl();        // TODO
    }

    @Override
    public GrpcServerConfiguration configuration() {
        return null;
    }

    @Override
    public Context context() {
        return null;
    }

    @Override
    public CompletionStage<GrpcServer> start() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<GrpcServer> whenShutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<GrpcServer> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public HealthCheck[] healthChecks() {
        return new HealthCheck[0];
    }

    @Override
    public Map<String, ServiceDescriptor> services() {
        return null;
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public int port() {
        return 0;
    }

    public void deploy(ServiceDescriptor service, WeightedBag<ServerInterceptor> interceptors) {
    }
}
