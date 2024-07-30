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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.grpc.core.WeightedBag;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;

class GrpcServiceClientImpl implements GrpcServiceClient {
    private final GrpcServiceDescriptor serviceDescriptor;
    private final Channel serviceChannel;
    private final GrpcClientImpl grpcClient;
    private final Map<String, Channel> methodCache = new ConcurrentHashMap<>();

    GrpcServiceClientImpl(GrpcServiceDescriptor descriptor, GrpcClientImpl grpcClient) {
        this.serviceDescriptor = descriptor;
        this.grpcClient = grpcClient;

        if (descriptor.interceptors().isEmpty()) {
            serviceChannel = grpcClient.channel();
        } else {
            // sort interceptors using a weighted bag
            WeightedBag<ClientInterceptor> interceptors = WeightedBag.create();
            for (ClientInterceptor interceptor : descriptor.interceptors()) {
                interceptors.add(interceptor);
            }

            // wrap channel to call interceptors -- reversed for composition
            List<ClientInterceptor> orderedInterceptors = interceptors.stream().toList().reversed();
            serviceChannel = ClientInterceptors.intercept(grpcClient.channel(), orderedInterceptors);
        }
    }

    @Override
    public String serviceName() {
        return serviceDescriptor.serviceName();
    }

    @Override
    public <ReqT, ResT> ResT unary(String methodName, ReqT request) {
        ClientCall<ReqT, ResT> call = ensureMethod(methodName, MethodDescriptor.MethodType.UNARY);
        return ClientCalls.blockingUnaryCall(call, request);
    }

    @Override
    public <ReqT, ResT> void unary(String methodName, ReqT request, StreamObserver<ResT> response) {
        ClientCall<ReqT, ResT> call = ensureMethod(methodName, MethodDescriptor.MethodType.UNARY);
        ClientCalls.asyncUnaryCall(call, request, response);
    }

    @Override
    public <ReqT, ResT> Iterator<ResT> serverStream(String methodName, ReqT request) {
        ClientCall<ReqT, ResT> call = ensureMethod(methodName, MethodDescriptor.MethodType.SERVER_STREAMING);
        return ClientCalls.blockingServerStreamingCall(call, request);
    }

    @Override
    public <ReqT, ResT> void serverStream(String methodName, ReqT request, StreamObserver<ResT> response) {
        ClientCall<ReqT, ResT> call = ensureMethod(methodName, MethodDescriptor.MethodType.SERVER_STREAMING);
        ClientCalls.asyncServerStreamingCall(call, request, response);
    }

    @Override
    public <ReqT, ResT> ResT clientStream(String methodName, Iterator<ReqT> request) {
        ClientCall<ReqT, ResT> call = ensureMethod(methodName, MethodDescriptor.MethodType.CLIENT_STREAMING);
        CompletableFuture<ResT> future = new CompletableFuture<>();
        StreamObserver<ReqT> observer = ClientCalls.asyncClientStreamingCall(call, new StreamObserver<>() {
            private ResT value;

            @Override
            public void onNext(ResT value) {
                this.value = value;
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                future.complete(value);
            }
        });

        // send client stream
        while (request.hasNext()) {
            observer.onNext(request.next());
        }
        observer.onCompleted();

        // block waiting for response
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <ReqT, ResT> StreamObserver<ReqT> clientStream(String methodName, StreamObserver<ResT> response) {
        ClientCall<ReqT, ResT> call = ensureMethod(methodName, MethodDescriptor.MethodType.CLIENT_STREAMING);
        return ClientCalls.asyncClientStreamingCall(call, response);
    }

    @Override
    public <ReqT, ResT> Iterator<ResT> bidi(String methodName, Iterator<ReqT> request) {
        ClientCall<ReqT, ResT> call = ensureMethod(methodName, MethodDescriptor.MethodType.BIDI_STREAMING);
        CompletableFuture<Iterator<ResT>> future = new CompletableFuture<>();
        StreamObserver<ReqT> observer = ClientCalls.asyncBidiStreamingCall(call, new StreamObserver<>() {
            private final List<ResT> values = new ArrayList<>();

            @Override
            public void onNext(ResT value) {
                values.add(value);
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                future.complete(values.iterator());
            }
        });

        // send client stream
        while (request.hasNext()) {
            observer.onNext(request.next());
        }
        observer.onCompleted();

        // block waiting for response
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <ReqT, ResT> StreamObserver<ReqT> bidi(String methodName, StreamObserver<ResT> response) {
        ClientCall<ReqT, ResT> call = ensureMethod(methodName, MethodDescriptor.MethodType.BIDI_STREAMING);
        return ClientCalls.asyncBidiStreamingCall(call, response);
    }

    private <ReqT, ResT> ClientCall<ReqT, ResT> ensureMethod(String methodName, MethodDescriptor.MethodType methodType) {
        GrpcClientMethodDescriptor methodDescriptor = serviceDescriptor.method(methodName);
        if (!methodDescriptor.type().equals(methodType)) {
            throw new IllegalArgumentException("Method " + methodName + " is of type " + methodDescriptor.type()
                    + ", yet " + methodType + " was requested.");
        }

        // use channel that contains all service and method interceptors
        if (methodDescriptor.interceptors().isEmpty()) {
            return serviceChannel.newCall(methodDescriptor.descriptor(), CallOptions.DEFAULT);
        } else {
            Channel methodChannel = methodCache.computeIfAbsent(methodName, k -> {
                WeightedBag<ClientInterceptor> interceptors = WeightedBag.create();
                for (ClientInterceptor interceptor : serviceDescriptor.interceptors()) {
                    interceptors.add(interceptor);
                }
                interceptors.merge(methodDescriptor.interceptors());
                List<ClientInterceptor> orderedInterceptors = interceptors.stream().toList().reversed();
                return ClientInterceptors.intercept(grpcClient.channel(), orderedInterceptors);
            });
            return methodChannel.newCall(methodDescriptor.descriptor(), CallOptions.DEFAULT);
        }
    }
}
