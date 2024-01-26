/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc;

import com.google.protobuf.Descriptors;
import io.grpc.stub.ServerCalls;

/**
 * Grpc service.
 */
public interface GrpcService {
    /**
     * Proto descriptor of this service.
     *
     * @return proto file descriptor
     */
    Descriptors.FileDescriptor proto();

    /**
     * Service name, defaults to this class simple name.
     *
     * @return service name
     */
    default String serviceName() {
        return getClass().getSimpleName();
    }

    /**
     * Update routing.
     *
     * @param routing routing
     */
    void update(Routing routing);

    /**
     * Service specific routing (proto descriptor is provided by {@link GrpcService#proto()}.
     */
    interface Routing {
        /**
         * Unary route.
         *
         * @param methodName method name
         * @param method     method to handle the route
         * @param <ReqT>     request type
         * @param <ResT>     response type
         * @return updated routing
         */
        <ReqT, ResT> Routing unary(String methodName, ServerCalls.UnaryMethod<ReqT, ResT> method);
        <ReqT, ResT> Routing unary(String methodName, GrpcServerCalls.Unary<ReqT, ResT> method);

        /**
         * Bidirectional route.
         *
         * @param methodName method name
         * @param method     method to handle the route
         * @param <ReqT>     request type
         * @param <ResT>     response type
         * @return updated routing
         */
        <ReqT, ResT> Routing bidi(String methodName, ServerCalls.BidiStreamingMethod<ReqT, ResT> method);
        <ReqT, ResT> Routing bidi(String methodName, GrpcServerCalls.Bidi<ReqT, ResT> method);

        /**
         * Server streaming route.
         *
         * @param methodName method name
         * @param method     method to handle the route
         * @param <ReqT>     request type
         * @param <ResT>     response type
         * @return updated routing
         */
        <ReqT, ResT> Routing serverStream(String methodName, ServerCalls.ServerStreamingMethod<ReqT, ResT> method);
        <ReqT, ResT> Routing serverStream(String methodName, GrpcServerCalls.ServerStream<ReqT, ResT> method);

        /**
         * Client streaming route.
         *
         * @param methodName method name
         * @param method     method to handle the route
         * @param <ReqT>     request type
         * @param <ResT>     response type
         * @return updated routing
         */
        <ReqT, ResT> Routing clientStream(String methodName, ServerCalls.ClientStreamingMethod<ReqT, ResT> method);
        <ReqT, ResT> Routing clientStream(String methodName, GrpcServerCalls.ClientStream<ReqT, ResT> method);
    }
}
