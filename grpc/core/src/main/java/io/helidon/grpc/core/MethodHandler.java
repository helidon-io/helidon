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
package io.helidon.grpc.core;

import java.util.concurrent.CompletionStage;

import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;

/**
 * A gRPC method call handler.
 *
 * @param <ReqT>  the request type
 * @param <RespT> the response type
 */
public interface MethodHandler<ReqT, RespT>
        extends ServerCalls.UnaryMethod<ReqT, RespT>,
                ServerCalls.ClientStreamingMethod<ReqT, RespT>,
                ServerCalls.ServerStreamingMethod<ReqT, RespT>,
                ServerCalls.BidiStreamingMethod<ReqT, RespT> {
    /**
     * Obtain the {@link MethodDescriptor.MethodType gRPC method tyoe} that
     * this {@link MethodHandler} handles.
     *
     * @return the {@link MethodDescriptor.MethodType gRPC method type} that
     *         this {@link MethodHandler} handles
     */
    MethodDescriptor.MethodType type();

    /**
     * Obtain the request type.
     * @return  the request type
     */
    Class<?> getRequestType();

    /**
     * Obtain the response type.
     * @return  the response type
     */
    Class<?> getResponseType();

    /**
     * Obtain the name of the underlying Java method that this handler maps to.
     *
     * @return the name of the underlying Java method that this handler maps to
     */
    String javaMethodName();

    /**
     * Determine whether this is a client side only handler.
     *
     * @return  {@code true} if this handler can only be used on the client
     */
    default boolean clientOnly() {
        return false;
    }

    @Override
    default void invoke(ReqT request, StreamObserver<RespT> observer) {
        observer.onError(Status.UNIMPLEMENTED.asException());
    }

    @Override
    default StreamObserver<ReqT> invoke(StreamObserver<RespT> observer) {
        observer.onError(Status.UNIMPLEMENTED.asException());
        return null;
    }

    /**
     * Handle a bi-directional client call.
     *
     * @param args the call arguments.
     * @param client the {@link BidirectionalClient} instance to forward the call to.
     * @return the call result
     */
    default Object bidirectional(Object[] args, BidirectionalClient client) {
        throw Status.UNIMPLEMENTED.asRuntimeException();
    }

    /**
     * Handle a bi-directional client call.
     *
     * @param args the call arguments.
     * @param client the {@link ClientStreaming} instance to forward the call to.
     * @return the call result
     */
    default Object clientStreaming(Object[] args, ClientStreaming client) {
        throw Status.UNIMPLEMENTED.asRuntimeException();
    }

    /**
     * Handle a bi-directional client call.
     *
     * @param args the call arguments.
     * @param client the {@link ServerStreamingClient} instance to forward the call to.
     * @return the call result
     */
    default Object serverStreaming(Object[] args, ServerStreamingClient client) {
        throw Status.UNIMPLEMENTED.asRuntimeException();
    }

    /**
     * Handle a bi-directional client call.
     *
     * @param args the call arguments.
     * @param client the {@link UnaryClient} instance to forward the call to.
     * @return the call result
     */
    default Object unary(Object[] args, UnaryClient client) {
        throw Status.UNIMPLEMENTED.asRuntimeException();
    }

    /**
     * A bidirectional client call handler.
     */
    interface BidirectionalClient {
        /**
         * Perform a bidirectional client call.
         *
         * @param methodName  the name of the gRPC method
         * @param observer    the {@link StreamObserver} that will receive the responses
         * @param <ReqT>      the request type
         * @param <RespT>     the response type
         * @return a {@link StreamObserver} to use to send requests
         */
        <ReqT, RespT> StreamObserver<ReqT> bidiStreaming(String methodName, StreamObserver<RespT> observer);
    }

    /**
     * A client streaming client call handler.
     */
    interface ClientStreaming {
        /**
         * Perform a client streaming  client call.
         *
         * @param methodName  the name of the gRPC method
         * @param observer    the {@link StreamObserver} that will receive the responses
         * @param <ReqT>      the request type
         * @param <RespT>     the response type
         * @return a {@link StreamObserver} to use to send requests
         */
        <ReqT, RespT> StreamObserver<ReqT> clientStreaming(String methodName, StreamObserver<RespT> observer);
    }

    /**
     * A server streaming client call handler.
     */
    interface ServerStreamingClient {
        /**
         * Perform a server streaming client call.
         *
         * @param methodName  the name of the gRPC method
         * @param request     the request message
         * @param observer    the {@link StreamObserver} that will receive the responses
         * @param <ReqT>      the request type
         * @param <RespT>     the response type
         */
        <ReqT, RespT> void serverStreaming(String methodName, ReqT request, StreamObserver<RespT> observer);
    }

    /**
     * A unary client call handler.
     */
    interface UnaryClient {
        /**
         * Perform a unary client call.
         *
         * @param methodName  the name of the gRPC method
         * @param request     the request message
         * @param <ReqT>      the request type
         * @param <RespT>     the response type
         * @return a {@link java.util.concurrent.CompletableFuture} that completes when the call completes
         */
        <ReqT, RespT> CompletionStage<RespT> unary(String methodName, ReqT request);
    }
}
