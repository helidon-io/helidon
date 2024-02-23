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

package io.helidon.webclient.grpc;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.Protocol;

import io.grpc.Channel;

/**
 * gRPC client.
 */
@RuntimeType.PrototypedBy(GrpcClientConfig.class)
public interface GrpcClient extends RuntimeType.Api<GrpcClientConfig> {
    /**
     * Protocol ID constant for gRPC.
     */
    String PROTOCOL_ID = "grpc";

    /**
     * Protocol to use to obtain an instance of gRPC specific client from
     * {@link io.helidon.webclient.api.WebClient#client(io.helidon.webclient.spi.Protocol)}.
     */
    Protocol<GrpcClient, GrpcClientProtocolConfig> PROTOCOL = GrpcProtocolProvider::new;

    /**
     * A new fluent API builder to customize client setup.
     *
     * @return a new builder
     */
    static GrpcClientConfig.Builder builder() {
        return GrpcClientConfig.builder();
    }

    /**
     * Create a new instance with custom configuration.
     *
     * @param clientConfig HTTP/2 client configuration
     * @return a new HTTP/2 client
     */
    static GrpcClient create(GrpcClientConfig clientConfig) {
        return new GrpcClientImpl(WebClient.create(it -> it.from(clientConfig)), clientConfig);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param consumer HTTP/2 client configuration
     * @return a new HTTP/2 client
     */
    static GrpcClient create(Consumer<GrpcClientConfig.Builder> consumer) {
        return create(GrpcClientConfig.builder()
                              .update(consumer)
                              .buildPrototype());
    }

    /**
     * Create a new instance with default configuration.
     *
     * @return a new HTTP/2 client
     */
    static GrpcClient create() {
        return create(GrpcClientConfig.create());
    }

    /**
     * Create a client for a specific service. The client will be backed by the same HTTP/2 client.
     *
     * @param descriptor descriptor to use
     * @return client for the provided descriptor
     */
    GrpcServiceClient serviceClient(GrpcServiceDescriptor descriptor);

    /**
     * Create a gRPC channel for this client that can be used to create stubs.
     *
     * @return a new gRPC channel
     */
    Channel channel();
}
