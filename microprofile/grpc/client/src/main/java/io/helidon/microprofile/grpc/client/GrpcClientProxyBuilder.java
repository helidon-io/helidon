/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.Builder;
import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.client.GrpcServiceClient;

import io.grpc.Channel;
import io.grpc.inprocess.InProcessChannelBuilder;

/**
 * A builder for gRPC clients dynamic proxies.
 *
 * @param <T> the type of the interface to be proxied
 */
public class GrpcClientProxyBuilder<T>
        implements Builder<T> {

    private static final Map<Class<?>, ClientServiceDescriptor> DESCRIPTORS = new ConcurrentHashMap<>();

    private final GrpcServiceClient client;

    private final Class<T> type;

    private GrpcClientProxyBuilder(GrpcServiceClient client, Class<T> type) {
        this.client = client;
        this.type = type;
    }

    /**
     * Create a GrpcClientProxyBuilder that can build gRPC dynamic proxies
     * for a given gRPC service interface using in-process channel.
     * <p>
     * This method will attempt to create in-process channel for the default
     * gRPC server. If you have changed the gRPC server name, use
     * {@link #create(String, Class)} instead.
     * <p>
     * The class passed to this method should be properly annotated with
     * {@link io.helidon.microprofile.grpc.core.Grpc} and
     * {@link io.helidon.microprofile.grpc.core.GrpcMethod} annotations
     * so that the proxy can properly route calls to the server.
     *
     * @param type  the service type
     * @param <T>   the service type
     * @return a {@link GrpcClientProxyBuilder} that can build dynamic proxies
     *         for the gRPC service
     */
    public static <T> GrpcClientProxyBuilder<T> create(Class<T> type) {
        return create("grpc.server", type);
    }

    /**
     * Create a GrpcClientProxyBuilder that can build gRPC dynamic proxies
     * for a given gRPC service interface using in-process channel.
     * <p>
     * The class passed to this method should be properly annotated with
     * {@link io.helidon.microprofile.grpc.core.Grpc} and
     * {@link io.helidon.microprofile.grpc.core.GrpcMethod} annotations
     * so that the proxy can properly route calls to the server.
     *
     * @param serverName  the name of the gRPC server proxy should connect to
     * @param type        the service type
     * @param <T>         the service type
     * @return a {@link GrpcClientProxyBuilder} that can build dynamic proxies
     *         for the gRPC service
     */
    public static <T> GrpcClientProxyBuilder<T> create(String serverName, Class<T> type) {
        return create(InProcessChannelBuilder.forName(serverName).usePlaintext().build(), type);
    }

    /**
     * Create a GrpcClientProxyBuilder that can build gRPC dynamic proxies
     * for a given gRPC service interface.
     * <p>
     * The class passed to this method should be properly annotated with
     * {@link io.helidon.microprofile.grpc.core.Grpc} and
     * {@link io.helidon.microprofile.grpc.core.GrpcMethod} annotations
     * so that the proxy can properly route calls to the server.
     *
     * @param channel  the {@link Channel} to connect to the server
     * @param type     the service type
     * @param <T>      the service type
     * @return a {@link GrpcClientProxyBuilder} that can build dynamic proxies
     *         for the gRPC service
     */
    public static <T> GrpcClientProxyBuilder<T> create(Channel channel, Class<T> type) {
        ClientServiceDescriptor descriptor = DESCRIPTORS.computeIfAbsent(type, GrpcClientProxyBuilder::createDescriptor);
        return new GrpcClientProxyBuilder<>(GrpcServiceClient.builder(channel, descriptor).build(), type);
    }

    /**
     * Build a gRPC client dynamic proxy of the required type.
     *
     * @return a gRPC client dynamic proxy
     */
    @Override
    public T build() {
        return client.proxy(type);
    }

    private static ClientServiceDescriptor createDescriptor(Class<?> type) {
        GrpcClientBuilder builder = GrpcClientBuilder.create(type);
        ClientServiceDescriptor.Builder descriptorBuilder = builder.build();
        return descriptorBuilder.build();
    }
}
