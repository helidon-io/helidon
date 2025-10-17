/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.webserver.grpc.tracing;

import io.helidon.common.Weighted;
import io.helidon.grpc.core.WeightedBag;
import io.helidon.tracing.Tracer;
import io.helidon.webserver.grpc.GrpcTracingConfig;
import io.helidon.webserver.grpc.GrpcTracingInterceptor;
import io.helidon.webserver.grpc.spi.GrpcServerService;

import io.grpc.ServerInterceptor;

class GrpcTracingService implements GrpcServerService {

    private final GrpcTracingConfig config;

    private GrpcTracingService(GrpcTracingConfig config) {
        this.config = config;
    }

    static GrpcTracingService create(GrpcTracingConfig config) {
        return new GrpcTracingService(config);
    }

    @Override
    public String type() {
        return "tracing";
    }

    @Override
    public WeightedBag<ServerInterceptor> interceptors() {
        WeightedBag<ServerInterceptor> interceptors = WeightedBag.create();
        interceptors.add(GrpcTracingInterceptor.create(Tracer.global(), config),
                         Weighted.DEFAULT_WEIGHT + 100);
        return interceptors;
    }
}
