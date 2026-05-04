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

package io.helidon.security.integration.grpc.spi;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.grpc.server.spi.GrpcRoutingConfigurer;
import io.helidon.security.integration.grpc.GrpcSecurity;

import io.grpc.ServerInterceptor;

/**
 * Configures gRPC security from application configuration.
 */
public final class GrpcSecurityRoutingConfigurer implements GrpcRoutingConfigurer {
    @Override
    public Iterable<ServerInterceptor> interceptors(Config config) {
        return interceptors(config, List.of());
    }

    @Override
    public Iterable<ServerInterceptor> interceptors(Config config, Iterable<ServerInterceptor> existingInterceptors) {
        Config security = config.get("security");
        if (!security.get("grpc-server").exists()) {
            return List.of();
        }

        for (ServerInterceptor interceptor : existingInterceptors) {
            if (interceptor instanceof GrpcSecurity && ((GrpcSecurity) interceptor).usesSameConfig(security)) {
                return List.of();
            }
        }

        return List.of(GrpcSecurity.create(security));
    }
}
