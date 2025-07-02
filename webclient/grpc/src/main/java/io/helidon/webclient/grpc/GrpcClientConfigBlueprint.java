/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.webclient.grpc;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.grpc.spi.GrpcClientService;
import io.helidon.webclient.grpc.spi.GrpcClientServiceProvider;

/**
 * Configuration of a grpc client.
 */
@Prototype.Blueprint
@Prototype.Configured
interface GrpcClientConfigBlueprint extends HttpClientConfig, Prototype.Factory<GrpcClient> {

    /**
     * gRPC specific configuration.
     *
     * @return protocol specific configuration
     */
    @Option.Default("create()")
    @Option.Configured
    GrpcClientProtocolConfig protocolConfig();

    /**
     * A {@link io.helidon.webclient.grpc.ClientUriSupplier} that can dynamically
     * provide zero or more {@link io.helidon.webclient.api.ClientUri}s to connect.
     *
     * @return a supplier for zero or more client URIs
     */
    Optional<ClientUriSupplier> clientUriSupplier();

    /**
     * Whether to collect metrics for gRPC client calls.
     *
     * @return metrics flag
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean enableMetrics();

    /**
     * gRPC client services. A gRPC service needs to be explicitly added to
     * be enabled given that {@code discoveredServices} is {@code false}.
     *
     * @return services to use with this gRPC client
     */
    @Option.Singular
    @Option.Configured
    @Option.Provider(value = GrpcClientServiceProvider.class, discoverServices = false)
    List<GrpcClientService> grpcServices();
}

