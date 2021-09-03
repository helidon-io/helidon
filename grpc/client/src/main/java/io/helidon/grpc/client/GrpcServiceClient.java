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

package io.helidon.grpc.client;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.grpc.core.InterceptorPriorities;
import io.helidon.grpc.core.MethodHandler;
import io.helidon.grpc.core.PriorityBag;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.Status;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;

/**
 * A gRPC Client for a specific gRPC service.
 */
public class GrpcServiceClient {

    private final HashMap<String, GrpcMethodStub> methodStubs;

    private final ClientServiceDescriptor clientServiceDescriptor;

    /**
     * Creates a {@link GrpcServiceClient.Builder}.
     *
     * @param channel the {@link Channel} to use to connect to the server
     * @param descriptor the {@link ClientServiceDescriptor} describing the gRPC service
     *
     * @return a new instance of {@link GrpcServiceClient.Builder}
     */
    public static GrpcServiceClient.Builder builder(Channel channel, ClientServiceDescriptor descriptor) {
        return new GrpcServiceClient.Builder(channel, descriptor);
    }

    /**
     * Creates a {@link GrpcServiceClient}.
     *
     * @param channel the {@link Channel} to use to connect to the server
     * @param descriptor the {@link ClientServiceDescriptor} describing the gRPC service
     *
     * @return a new instance of {@link GrpcServiceClient.Builder}
     */
    public static GrpcServiceClient create(Channel channel, ClientServiceDescriptor descriptor) {
        return builder(channel, descriptor).build();
    }

    private GrpcServiceClient(Channel channel,
                              CallOptions callOptions,
                              ClientServiceDescriptor clientServiceDescriptor) {
        this.clientServiceDescriptor = clientServiceDescriptor;
        this.methodStubs = new HashMap<>();

        // Merge Interceptors specified in Channel, ClientServiceDescriptor and ClientMethodDescriptor.
        // Add the merged interceptor list to the AbstractStub which will be be used for the invocation
        // of the method.
        for (ClientMethodDescriptor methodDescriptor : clientServiceDescriptor.methods()) {
            GrpcMethodStub methodStub = new GrpcMethodStub(channel, callOptions, methodDescriptor);

            PriorityBag<ClientInterceptor> priorityInterceptors = PriorityBag.withDefaultPriority(InterceptorPriorities.USER);
            priorityInterceptors.addAll(clientServiceDescriptor.interceptors());
            priorityInterceptors.addAll(methodDescriptor.interceptors());
            List<ClientInterceptor> interceptors = priorityInterceptors.stream().collect(Collectors.toList());

            if (interceptors.size() > 0) {
                LinkedHashSet<ClientInterceptor> uniqueInterceptors = new LinkedHashSet<>(interceptors.size());

                // iterate the interceptors in reverse order so that the interceptor chain is in the correct order
                for (int i = interceptors.size() - 1; i >= 0; i--) {
                    ClientInterceptor interceptor = interceptors.get(i);
                    if (!uniqueInterceptors.contains(interceptor)) {
                        uniqueInterceptors.add(interceptor);
                    }
                }

                for (ClientInterceptor interceptor : uniqueInterceptors) {
                    methodStub = (GrpcMethodStub) methodStub.withInterceptors(interceptor);
                }
            }

            if (methodDescriptor.callCredentials() != null) {
                // Method level CallCredentials take precedence over service level CallCredentials.
                methodStub = (GrpcMethodStub) methodStub.withCallCredentials(methodDescriptor.callCredentials());
            } else if (clientServiceDescriptor.callCredentials() != null) {
                methodStub = (GrpcMethodStub) methodStub.withCallCredentials(clientServiceDescriptor.callCredentials());
            }

            methodStubs.put(methodDescriptor.name(), methodStub);
        }
    }

    /**
     * Obtain the service name.
     *
     * @return The name of the service
     */
    public String serviceName() {
        return clientServiceDescriptor.name();
    }

    /**
     * Invoke the specified method using the method's
     * {@link io.helidon.grpc.core.MethodHandler}.
     *
     * @param name  the name of the method to invoke
     * @param args  the method arguments
     * @return the method response
     */
    Object invoke(String name, Object[] args) {
        GrpcMethodStub stub = methodStubs.get(name);
        if (stub == null) {
            throw Status.INTERNAL.withDescription("gRPC method '" + name + "' does not exist").asRuntimeException();
        }
        ClientMethodDescriptor descriptor = stub.descriptor();
        MethodHandler methodHandler = descriptor.methodHandler();

        switch (descriptor.descriptor().getType()) {
        case UNARY:
            return methodHandler.unary(args, this::unary);
        case CLIENT_STREAMING:
            return methodHandler.clientStreaming(args, this::clientStreaming);
        case SERVER_STREAMING:
            return methodHandler.serverStreaming(args, this::serverStreaming);
        case BIDI_STREAMING:
            return methodHandler.bidirectional(args, this::bidiStreaming);
        case UNKNOWN:
        default:
            throw Status.INTERNAL.withDescription("Unknown or unsupported method type for method " + name).asRuntimeException();
        }
    }

    /**
     * Create a dynamic proxy for the specified interface that proxies
     * calls to the wrapped gRPC service.
     *
     * @param type  the interface to create a proxy for
     * @param extraTypes extra types for the proxy to implement
     * @param <T>   the type of the returned proxy
     * @return  a dynamic proxy that calls methods on this gRPC service
     */
    @SuppressWarnings("unchecked")
    public <T> T proxy(Class<T> type, Class<?>... extraTypes) {
        Map<String, String> names = new HashMap<>();
        for (ClientMethodDescriptor methodDescriptor : clientServiceDescriptor.methods()) {
            MethodHandler methodHandler = methodDescriptor.methodHandler();
            if (methodHandler != null) {
                names.put(methodHandler.javaMethodName(), methodDescriptor.name());
            }
        }

        Class<?>[] proxyTypes;
        if (extraTypes == null || extraTypes.length == 0) {
            proxyTypes = new Class<?>[]{type};
        } else {
            proxyTypes = new Class<?>[extraTypes.length + 1];
            proxyTypes[0] = type;
            System.arraycopy(extraTypes, 0, proxyTypes, 1, extraTypes.length);
        }
        return (T) Proxy.newProxyInstance(type.getClassLoader(), proxyTypes, ClientProxy.create(this, names));
    }

    /**
     * Invoke the specified unary method with the specified request object.
     *
     * @param methodName the method name to be invoked
     * @param request    the request parameter
     * @param <ReqT>     the request type
     * @param <RespT>     the response type
     *
     * @return The result of this invocation
     */
    public <ReqT, RespT> RespT blockingUnary(String methodName, ReqT request) {
        GrpcMethodStub<ReqT, RespT> stub = ensureMethod(methodName, MethodType.UNARY);
        return ClientCalls.blockingUnaryCall(
                stub.getChannel(), stub.descriptor().descriptor(), stub.getCallOptions(), request);
    }

    /**
     * Asynchronously invoke the specified unary method.
     *
     * @param methodName the method name to be invoked
     * @param request    the request parameter
     * @param <ReqT>     the request type
     * @param <RespT>     the response type
     *
     * @return A {@link CompletionStage} that will complete with the result of the unary method call
     */
    public <ReqT, RespT> CompletionStage<RespT> unary(String methodName, ReqT request) {
        SingleValueStreamObserver<RespT> observer = new SingleValueStreamObserver<>();

        GrpcMethodStub<ReqT, RespT> stub = ensureMethod(methodName, MethodType.UNARY);
        ClientCalls.asyncUnaryCall(
                stub.getChannel().newCall(stub.descriptor().descriptor(), stub.getCallOptions()),
                request,
                observer);

        return observer.completionStage();
    }

    /**
     * Invoke the specified unary method.
     *
     * @param methodName the method name to be invoked
     * @param request    the request parameter
     * @param observer   a {@link StreamObserver} to receive the result
     * @param <ReqT>     the request type
     * @param <RespT>     the response type
     */
    public <ReqT, RespT> void unary(String methodName, ReqT request, StreamObserver<RespT> observer) {
        GrpcMethodStub<ReqT, RespT> stub = ensureMethod(methodName, MethodType.UNARY);
        ClientCalls.asyncUnaryCall(
                stub.getChannel().newCall(stub.descriptor().descriptor(), stub.getCallOptions()),
                request,
                observer);
    }

    /**
     * Invoke the specified server streaming method.
     *
     * @param methodName the method name to be invoked
     * @param request    the request parameter
     * @param <ReqT>     the request type
     * @param <RespT>    the response type
     *
     * @return an {@link Iterator} to obtain the streamed results
     */
    public <ReqT, RespT> Iterator<RespT> blockingServerStreaming(String methodName, ReqT request) {
        GrpcMethodStub<ReqT, RespT> stub = ensureMethod(methodName, MethodType.SERVER_STREAMING);
        return ClientCalls.blockingServerStreamingCall(
                stub.getChannel().newCall(stub.descriptor().descriptor(), stub.getCallOptions()),
                request);
    }

    /**
     * Invoke the specified server streaming method.
     *
     * @param methodName the method name to be invoked
     * @param request    the request parameter
     * @param observer   a {@link StreamObserver} to receive the results
     * @param <ReqT>     the request type
     * @param <RespT>    the response type
     */
    public <ReqT, RespT> void serverStreaming(String methodName, ReqT request, StreamObserver<RespT> observer) {
        GrpcMethodStub<ReqT, RespT> stub = ensureMethod(methodName, MethodType.SERVER_STREAMING);
        ClientCalls.asyncServerStreamingCall(
                stub.getChannel().newCall(stub.descriptor().descriptor(), stub.getCallOptions()),
                request,
                observer);
    }

    /**
     * Invoke the specified client streaming method.
     *
     * @param methodName the method name to be invoked
     * @param items      an {@link Iterable} of items to be streamed to the server
     * @param <ReqT>     the request type
     * @param <RespT>    the response type
     * @return A {@link StreamObserver} to retrieve the method call result
     */
    public <ReqT, RespT> CompletionStage<RespT> clientStreaming(String methodName, Iterable<ReqT> items) {
        return clientStreaming(methodName, StreamSupport.stream(items.spliterator(), false));
    }

    /**
     * Invoke the specified client streaming method.
     *
     * @param methodName the method name to be invoked
     * @param items      a {@link java.util.stream.Stream} of items to be streamed to the server
     * @param <ReqT>     the request type
     * @param <RespT>    the response type
     * @return A {@link StreamObserver} to retrieve the method call result
     */
    public <ReqT, RespT> CompletionStage<RespT> clientStreaming(String methodName, Stream<ReqT> items) {
        SingleValueStreamObserver<RespT> obsv = new SingleValueStreamObserver<>();
        GrpcMethodStub<ReqT, RespT> stub = ensureMethod(methodName, MethodType.CLIENT_STREAMING);
        StreamObserver<ReqT> reqStream = ClientCalls.asyncClientStreamingCall(
                stub.getChannel().newCall(stub.descriptor().descriptor(), stub.getCallOptions()),
                obsv);

        items.forEach(item -> {
            reqStream.onNext(item);
        });
        reqStream.onCompleted();

        return obsv.completionStage();
    }

    /**
     * Invoke the specified client streaming method.
     *
     * @param methodName the method name to be invoked
     * @param observer   a {@link StreamObserver} to receive the result
     * @param <ReqT>     the request type
     * @param <RespT>    the response type
     * @return a {@link StreamObserver} to use to stream requests to the server
     */
    public <ReqT, RespT> StreamObserver<ReqT> clientStreaming(String methodName, StreamObserver<RespT> observer) {
        GrpcMethodStub<ReqT, RespT> stub = ensureMethod(methodName, MethodType.CLIENT_STREAMING);
        return ClientCalls.asyncClientStreamingCall(
                stub.getChannel().newCall(stub.descriptor().descriptor(), stub.getCallOptions()),
                observer);
    }

    /**
     * Invoke the specified bidirectional streaming method.
     *
     * @param methodName the method name to be invoked.
     * @param observer   a {@link StreamObserver} to receive the result
     * @param <ReqT>     the request type
     * @param <RespT>     the response type
     * @return A {@link StreamObserver} to use to stream requests to the server
     */
    public <ReqT, RespT> StreamObserver<ReqT> bidiStreaming(String methodName, StreamObserver<RespT> observer) {
        GrpcMethodStub<ReqT, RespT> stub = ensureMethod(methodName, MethodType.BIDI_STREAMING);
        return ClientCalls.asyncBidiStreamingCall(
                stub.getChannel().newCall(stub.descriptor().descriptor(), stub.getCallOptions()),
                observer);
    }

    @SuppressWarnings("unchecked")
    private <ReqT, RespT> GrpcMethodStub<ReqT, RespT> ensureMethod(String methodName, MethodType methodType) {
        GrpcMethodStub<ReqT, RespT> stub = methodStubs.get(methodName);
        if (stub == null) {
            throw new IllegalArgumentException("No method named " + methodName + " registered with this service");
        }
        ClientMethodDescriptor cmd = stub.descriptor();
        if (cmd.descriptor().getType() != methodType) {
            throw new IllegalArgumentException("Method (" + methodName + ") already registered with a different method type.");
        }

        return stub;
    }

    /**
     * GrpcMethodStub can be used to configure method specific Interceptors, Metrics, Tracing, Deadlines, etc.
     */
    private static class GrpcMethodStub<ReqT, RespT>
        extends AbstractStub<GrpcMethodStub<ReqT, RespT>> {

        private ClientMethodDescriptor cmd;

        GrpcMethodStub(Channel channel, CallOptions callOptions, ClientMethodDescriptor cmd) {
            super(channel, callOptions);
            this.cmd = cmd;
        }

        @Override
        protected GrpcMethodStub<ReqT, RespT> build(Channel channel, CallOptions callOptions) {
            return new GrpcMethodStub<>(channel, callOptions, cmd);
        }

        public ClientMethodDescriptor descriptor() {
            return cmd;
        }
    }

    /**
     * Builder to build an instance of {@link io.helidon.grpc.client.GrpcServiceClient}.
     */
    public static class Builder {

        private Channel channel;

        private CallOptions callOptions = CallOptions.DEFAULT;

        private ClientServiceDescriptor clientServiceDescriptor;

        private Builder(Channel channel, ClientServiceDescriptor descriptor) {
            this.channel = channel;
            this.clientServiceDescriptor = descriptor;
        }

        /**
         * Set the {@link io.grpc.CallOptions} to use.
         *
         * @param callOptions the {@link CallOptions} to use
         * @return This {@link Builder} for fluent method chaining
         */
        public Builder callOptions(CallOptions callOptions) {
            this.callOptions = callOptions;
            return this;
        }

        /**
         * Build an instance of {@link GrpcServiceClient}.
         *
         * @return an new instance of a {@link GrpcServiceClient}
         */
        public GrpcServiceClient build() {
            return new GrpcServiceClient(channel, callOptions, clientServiceDescriptor);
        }
    }

    /**
     * A simple {@link io.grpc.stub.StreamObserver} adapter class that completes
     * a {@link CompletableFuture} when the observer is completed.
     * <p>
     * This observer uses the value passed to its {@link #onNext(Object)} method to complete
     * the {@link CompletableFuture}.
     * <p>
     * This observer should only be used in cases where a single result is expected. If more
     * that one call is made to {@link #onNext(Object)} then future will be completed with
     * an exception.
     *
     * @param <T> The type of objects received in this stream.
     */
    public static class SingleValueStreamObserver<T>
            implements StreamObserver<T> {

        private int count;

        private T result;

        private CompletableFuture<T> resultFuture = new CompletableFuture<>();

        /**
         * Create a SingleValueStreamObserver.
         */
        public SingleValueStreamObserver() {
        }

        /**
         * Obtain the {@link CompletableFuture} that will be completed
         * when the {@link io.grpc.stub.StreamObserver} completes.
         *
         * @return The CompletableFuture
         */
        public CompletionStage<T> completionStage() {
            return resultFuture;
        }

        @Override
        public void onNext(T value) {
            if (count++ == 0) {
                result = value;
            } else {
                resultFuture.completeExceptionally(new IllegalStateException("More than one result received."));
            }
        }

        @Override
        public void onError(Throwable t) {
            resultFuture.completeExceptionally(t);
        }

        @Override
        public void onCompleted() {
            resultFuture.complete(result);
        }
    }
}
