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
package io.helidon.webclient.grpc.tracing;

import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.grpc.core.WeightedBag;
import io.helidon.webclient.grpc.spi.GrpcClientService;

import io.grpc.ClientInterceptor;

/**
 * gRPC client tracing service.
 */
public class GrpcClientTracing implements GrpcClientService {

    private final Config config;

    /**
     * Create an instance from config.
     *
     * @param config the config
     */
    public GrpcClientTracing(Config config) {
        this.config = config;
    }

    /**
     * Create a new instance of the gRPC client tracing service.
     *
     * @param config the config
     * @return client tracing service
     */
    public static GrpcClientTracing create(Config config) {
        return new GrpcClientTracing(config);
    }

    @Override
    public String type() {
        return "tracing";
    }

    @Override
    public WeightedBag<ClientInterceptor> interceptors() {
        WeightedBag<ClientInterceptor> interceptors = WeightedBag.create();
        interceptors.add(new GrpcClientTracingInterceptor(), Weighted.DEFAULT_WEIGHT + 1000.0);
        return interceptors;
    }
}
