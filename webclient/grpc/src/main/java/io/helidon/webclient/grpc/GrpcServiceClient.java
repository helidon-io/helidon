/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Stream;

import io.helidon.common.Api;

import io.grpc.stub.StreamObserver;

/**
 * Client for a single service.
 *
 * @see io.helidon.webclient.grpc.GrpcClient#serviceClient(io.helidon.webclient.grpc.GrpcServiceDescriptor)
 */
public interface GrpcServiceClient {

    /**
     * Name of the service this client was created for.
     *
     * @return service name
     */
    String serviceName();

    /**
     * Blocking gRPC unary call.
     *
     * @param methodName method name
     * @param request the request
     * @param <ReqT> type of request
     * @param <ResT> type of response
     * @return the response
     */
    <ReqT, ResT> ResT unary(String methodName, ReqT request);

    /**
     * Asynchronous gRPC unary call.
     *
     * @param methodName method name
     * @param request the request
     * @param response the response observer
     * @param <ReqT> type of request
     * @param <ResT> type of response
     */
    <ReqT, ResT> void unary(String methodName, ReqT request, StreamObserver<ResT> response);

    /**
     * Blocking gRPC server stream call.
     *
     * @param methodName method name
     * @param request the request
     * @param <ReqT> type of request
     * @param <ResT> type of response
     * @return the response iterator
     */
    <ReqT, ResT> Iterator<ResT> serverStream(String methodName, ReqT request);

    /**
     * Asynchronous gRPC server stream call.
     *
     * @param methodName method name
     * @param request the request
     * @param response the response observer
     * @param <ReqT> type of request
     * @param <ResT> type of response
     */
    <ReqT, ResT> void serverStream(String methodName, ReqT request, StreamObserver<ResT> response);

    /**
     * Blocking gRPC client stream call.
     *
     * @param methodName method name
     * @param request the request iterator
     * @param <ReqT> type of request
     * @param <ResT> type of response
     * @return the response
     */
    <ReqT, ResT> ResT clientStream(String methodName, Iterator<ReqT> request);

    /**
     * Asynchronous gRPC client stream call.
     *
     * @param methodName method name
     * @param response the response observer
     * @param <ReqT> type of request
     * @param <ResT> type of response
     * @return the request observer
     */
    <ReqT, ResT> StreamObserver<ReqT> clientStream(String methodName, StreamObserver<ResT> response);

    /**
     * gRPC bidirectional call using {@link Iterator}.
     *
     * @param methodName method name
     * @param request request iterator
     * @param <ReqT> type of request
     * @param <ResT> type of response
     * @return response iterator
     */
    <ReqT, ResT> Iterator<ResT> bidi(String methodName, Iterator<ReqT> request);

    /**
     * Blocking gRPC server-streaming call exposed as a resource-owning {@link Stream}.
     * Closing the returned stream cancels an active RPC.
     * Implementations supporting resource-owning streaming calls must override this method.
     *
     * @param methodName method name
     * @param request request
     * @param <ReqT> type of request
     * @param <ResT> type of response
     * @return response stream
     * @throws UnsupportedOperationException if this implementation does not support resource-owning streams
     */
    @Api.Incubating
    default <ReqT, ResT> Stream<ResT> serverStreaming(String methodName, ReqT request) {
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(request, "request");
        throw new UnsupportedOperationException("Resource-owning gRPC streams are not supported by this client.");
    }

    /**
     * Blocking gRPC client-streaming call consuming a resource-owning {@link Stream}.
     * The request stream is closed on normal completion, cancellation, or failure and must not be reused after this
     * method is called.
     * The calling thread consumes the request stream. If producing request elements can block, closing the stream must
     * unblock production so this method can return promptly after an early peer termination.
     * Implementations supporting resource-owning streaming calls must override this method.
     *
     * @param methodName method name
     * @param requests request stream
     * @param <ReqT> type of request
     * @param <ResT> type of response
     * @return response
     * @throws UnsupportedOperationException if this implementation does not support resource-owning streams
     */
    @Api.Incubating
    default <ReqT, ResT> ResT clientStreaming(String methodName, Stream<ReqT> requests) {
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(requests, "requests");
        throw unsupportedResourceStreams(requests);
    }

    /**
     * gRPC bidirectional call using resource-owning {@link Stream Streams}.
     * The request stream is closed on normal completion, failure, or early closure of the returned stream and must not
     * be reused after this method is called. Closing the returned stream cancels an active RPC.
     * Implementations supporting resource-owning streaming calls must override this method.
     *
     * @param methodName method name
     * @param requests request stream
     * @param <ReqT> type of request
     * @param <ResT> type of response
     * @return response stream
     * @throws UnsupportedOperationException if this implementation does not support resource-owning streams
     */
    @Api.Incubating
    default <ReqT, ResT> Stream<ResT> bidirectional(String methodName, Stream<ReqT> requests) {
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(requests, "requests");
        throw unsupportedResourceStreams(requests);
    }

    /**
     * gRPC bidirectional call using {@link StreamObserver}.
     *
     * @param methodName method name
     * @param response the response observer
     * @param <ReqT> type of request
     * @param <ResT> type of response
     * @return the request observer
     */
    <ReqT, ResT> StreamObserver<ReqT> bidi(String methodName, StreamObserver<ResT> response);

    private static UnsupportedOperationException unsupportedResourceStreams(Stream<?> requests) {
        UnsupportedOperationException failure =
                new UnsupportedOperationException("Resource-owning gRPC streams are not supported by this client.");
        try {
            requests.close();
        } catch (Throwable t) {
            failure.addSuppressed(t);
        }
        return failure;
    }
}
