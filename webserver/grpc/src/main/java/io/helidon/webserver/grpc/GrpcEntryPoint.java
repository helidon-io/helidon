/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Set;

import io.helidon.common.Api;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceDescriptor;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerInterceptor;

/**
 * Container class for types related to gRPC entry points.
 * <p>
 * NOTE: this API is part of incubating features of Helidon. This API may change including backward incompatible changes
 *               and full removal. We welcome feedback for incubating features.
 */
@Api.Incubating
public class GrpcEntryPoint {
    private GrpcEntryPoint() {
    }

    /**
     * Interceptor of a gRPC entry point.
     * This interceptor has direct access to {@link io.grpc.ServerCall}, {@link io.grpc.Metadata}, and metadata of the
     * invoked endpoint.
     */
    public interface Interceptor {
        /**
         * Method to implement interceptor logic.
         *
         * @param interceptionContext context with invocation metadata
         * @param chain invocation chain
         * @param call gRPC server call
         * @param headers gRPC request headers
         * @param <ReqT> request type
         * @param <RespT> response type
         * @return server call listener
         * @throws java.lang.Exception in case the invocation fails, or the interceptor needs to stop processing of the request
         */
        <ReqT, RespT> ServerCall.Listener<ReqT> intercept(InterceptionContext interceptionContext,
                                                          Chain<ReqT, RespT> chain,
                                                          ServerCall<ReqT, RespT> call,
                                                          Metadata headers) throws Exception;

        /**
         * Invocation chain for a gRPC entry point.
         *
         * @param <ReqT> request type
         * @param <RespT> response type
         */
        interface Chain<ReqT, RespT> extends Interception.Interceptor.Chain<ServerCall.Listener<ReqT>> {
            @Override
            @SuppressWarnings("unchecked")
            default ServerCall.Listener<ReqT> proceed(Object[] args) throws Exception {
                return proceed((ServerCall<ReqT, RespT>) args[0], (Metadata) args[1]);
            }

            /**
             * Invoke the next interceptor in the chain.
             *
             * @param call gRPC server call
             * @param headers gRPC request headers
             * @return server call listener
             * @throws java.lang.Exception in case the invocation fails, this should not be handled by most interceptors
             */
            ServerCall.Listener<ReqT> proceed(ServerCall<ReqT, RespT> call, Metadata headers) throws Exception;
        }
    }

    /**
     * A contract used from generated code to invoke gRPC entry point interceptors.
     */
    public interface EntryPoints {
        /**
         * Whether gRPC entry point interceptors should be registered.
         * <p>
         * The default preserves compatibility for custom implementations.
         *
         * @return whether interceptors are available
         */
        default boolean hasInterceptors() {
            return true;
        }

        /**
         * Interceptor that triggers entry point interceptors.
         *
         * @param descriptor descriptor of the invoked endpoint
         * @param typeQualifiers qualifiers of the invoked endpoint
         * @param typeAnnotations type annotations of the invoked endpoint
         * @param methodInfo method information of the endpoint method
         * @return interceptor to register with the gRPC method descriptor
         */
        ServerInterceptor interceptor(ServiceDescriptor<?> descriptor,
                                      Set<Qualifier> typeQualifiers,
                                      List<Annotation> typeAnnotations,
                                      TypedElementInfo methodInfo);
    }
}
