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

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.webclient.grpc.spi.GrpcClientService;
import io.helidon.webclient.grpc.spi.GrpcClientServiceProvider;

/**
 * gRPC client tracing SPI provider implementation.
 *
 * @deprecated This class should only be used via {@link java.util.ServiceLoader}.
 *         Use {@link io.helidon.webclient.grpc.tracing.GrpcClientTracing} instead.
 */
@Deprecated
@Weight(Weighted.DEFAULT_WEIGHT + 100)
public class GrpcClientTracingProvider implements GrpcClientServiceProvider {

    /**
     * Required public constructor.
     *
     * @deprecated This class should only be used via {@link java.util.ServiceLoader}.
     */
    @Deprecated
    public GrpcClientTracingProvider() {
    }

    @Override
    public String configKey() {
        return "tracing";
    }

    @Override
    public GrpcClientService create(Config config, String name) {
        return GrpcClientTracing.create(config);
    }
}
