/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.grpc.server.spi;

import io.helidon.config.Config;

import io.grpc.ServerInterceptor;

/**
 * Java service provider interface for gRPC routing configuration.
 */
@FunctionalInterface
public interface GrpcRoutingConfigurer {
    /**
     * Creates default global interceptors to add to gRPC routing.
     *
     * @param config root configuration
     * @return interceptors to add
     */
    Iterable<ServerInterceptor> interceptors(Config config);

    /**
     * Creates default global interceptors to add to gRPC routing.
     *
     * @param config root configuration
     * @param existingInterceptors interceptors already configured on the routing
     * @return interceptors to add
     */
    default Iterable<ServerInterceptor> interceptors(Config config,
                                                     Iterable<ServerInterceptor> existingInterceptors) {
        return interceptors(config);
    }
}
