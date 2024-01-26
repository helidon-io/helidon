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

import java.util.Collection;

import io.grpc.stub.StreamObserver;

import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCalls;

class GrpcServiceClientImpl implements GrpcServiceClient {
    private final GrpcServiceDescriptor descriptor;
    private final GrpcClientImpl grpcClient;

    GrpcServiceClientImpl(GrpcServiceDescriptor descriptor, GrpcClientImpl grpcClient) {
        this.descriptor = descriptor;
        this.grpcClient = grpcClient;
    }

    @Override
    public String serviceName() {
        return descriptor.serviceName();
    }

    @Override
    public <ReqT, RespT> RespT unary(String methodName, ReqT request) {
        ClientCall<? super ReqT, ? extends RespT> call = ensureMethod(methodName, MethodDescriptor.MethodType.UNARY);
        return ClientCalls.blockingUnaryCall(call, request);
    }

    @Override
    public <ReqT, RespT> StreamObserver<ReqT> unary(String methodName, StreamObserver<RespT> responseObserver) {
        return null;
    }

    @Override
    public <ReqT, RespT> Collection<RespT> serverStream(String methodName, ReqT request) {
        return null;
    }

    @Override
    public <ReqT, RespT> void serverStream(String methodName, ReqT request, StreamObserver<RespT> responseObserver) {
    }

    @Override
    public <ReqT, RespT> RespT clientStream(String methodName, Collection<ReqT> request) {
        return null;
    }

    @Override
    public <ReqT, RespT> StreamObserver<ReqT> clientStream(String methodName, StreamObserver<RespT> responseObserver) {
        return null;
    }

    @Override
    public <ReqT, RespT> Collection<RespT> bidi(String methodName, Collection<ReqT> responseObserver) {
        return null;
    }

    @Override
    public <ReqT, RespT> StreamObserver<ReqT> bidi(String methodName, StreamObserver<RespT> responseObserver) {
        return null;
    }

    private <ReqT, RespT> ClientCall<ReqT, RespT> ensureMethod(String methodName, MethodDescriptor.MethodType methodType) {
        GrpcClientMethodDescriptor method = descriptor.method(methodName);
        if (!method.type().equals(methodType)) {
            throw new IllegalArgumentException("Method " + methodName + " is of type " + method.type() + ", yet " + methodType + " was requested.");
        }
        return createClientCall(method);
    }

    private <ReqT, RespT> ClientCall<ReqT, RespT> createClientCall(GrpcClientMethodDescriptor method) {
        return new GrpcClientCall<>(grpcClient, method);
    }
}
