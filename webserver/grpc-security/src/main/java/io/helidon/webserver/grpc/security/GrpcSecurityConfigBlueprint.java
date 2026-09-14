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

package io.helidon.webserver.grpc.security;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webserver.grpc.spi.GrpcServerServiceProvider;

/**
 * Helidon gRPC security configuration.
 */
@Prototype.Blueprint
@Prototype.Configured(value = GrpcSecurity.TYPE, root = false)
@Prototype.Provides(GrpcServerServiceProvider.class)
interface GrpcSecurityConfigBlueprint {
    /**
     * Whether gRPC security is enabled.
     *
     * @return whether gRPC security is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Default gRPC security handler.
     *
     * @return default gRPC security handler
     */
    @Option.Configured
    @Option.DefaultCode("GrpcSecurityHandler.create()")
    GrpcSecurityHandler defaults();

    /**
     * Service-specific gRPC security configuration.
     *
     * @return service security configuration
     */
    @Option.Configured
    @Option.Singular
    List<GrpcSecurityServiceConfig> services();
}
