/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.client.GrpcServiceClient;

import io.grpc.Channel;

/**
 * Helper methods for gRPC clients.
 */
class GrpcClientHelper {

    private static final Map<Class<?>, ClientServiceDescriptor> DESCRIPTORS = new ConcurrentHashMap<>();

    /**
     * Create a gRPC client.
     * <p>
     * The class passed to this method should be properly annotated with
     * {@link io.helidon.microprofile.grpc.core.RpcService} and
     * {@link io.helidon.microprofile.grpc.core.RpcMethod} annotations
     * so that the proxy can properly route calls to the server.
     *
     * @param channel  the {@link Channel} to connect to the server
     * @param type     the service type
     * @param <T>      the service type
     * @return a dynamic proxy that makes calls to the gRPC service.
     */
    static <T> T proxy(Channel channel, Class<T> type) {
        ClientServiceDescriptor descriptor = DESCRIPTORS.computeIfAbsent(type, GrpcClientHelper::createDescriptor);
        GrpcServiceClient client = GrpcServiceClient.builder(channel, descriptor).build();
        return client.proxy(type);
    }

    private static ClientServiceDescriptor createDescriptor(Class<?> type) {
        ClientServiceModeller modeller = new ClientServiceModeller(type);
        return modeller.createServiceBuilder().build();
    }

    /**
     * Private constructor for utility class.
     */
    private GrpcClientHelper() {
    }
}
