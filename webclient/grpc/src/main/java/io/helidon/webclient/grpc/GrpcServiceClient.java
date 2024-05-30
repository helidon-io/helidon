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

import java.util.Iterator;

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
     * gRPC bidirectional call using {@link StreamObserver}.
     *
     * @param methodName method name
     * @param response the response observer
     * @param <ReqT> type of request
     * @param <ResT> type of response
     * @return the request observer
     */
    <ReqT, ResT> StreamObserver<ReqT> bidi(String methodName, StreamObserver<ResT> response);
}
