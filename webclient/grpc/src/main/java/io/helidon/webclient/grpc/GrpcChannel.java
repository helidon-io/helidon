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

import io.helidon.webclient.api.ClientUri;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;

/**
 * Helidon's implementation of a gRPC {@link Channel}.
 */
public class GrpcChannel extends Channel {

    private final GrpcClientImpl grpcClient;

    /**
     * Creates a new channel from a {@link GrpcClient}.
     *
     * @param grpcClient the gRPC client
     */
    GrpcChannel(GrpcClient grpcClient) {
        this.grpcClient = (GrpcClientImpl) grpcClient;
    }

    /**
     * Underlying gRPC Client for this channel.
     *
     * @return the gRPC client
     */
    public GrpcClient grpcClient() {
        return grpcClient;
    }

    @Override
    public <ReqT, ResT> ClientCall<ReqT, ResT> newCall(
            MethodDescriptor<ReqT, ResT> methodDescriptor, CallOptions callOptions) {
        MethodDescriptor.MethodType methodType = methodDescriptor.getType();
        return methodType == MethodDescriptor.MethodType.UNARY
                ? new GrpcUnaryClientCall<>(this, methodDescriptor, callOptions)
                : new GrpcClientCall<>(this, methodDescriptor, callOptions);
    }

    @Override
    public String authority() {
        return baseUri().authority();
    }

    /**
     * Gets base URI for this gRPC client.
     *
     * @return the base URI
     * @throws java.lang.IllegalArgumentException if no base URI is defined
     */
    public ClientUri baseUri() {
        return grpcClient.prototype()
                .baseUri()
                .orElseThrow(() -> new IllegalArgumentException("No base URI provided for GrpcClient"));
    }
}
