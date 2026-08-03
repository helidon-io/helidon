/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import java.util.List;
import java.util.Set;

import io.helidon.common.Api;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;

/**
 * Container class for types related to HTTP entry points.
 * <p>
 * NOTE: this API is part of incubating features of Helidon. This API may change including backward incompatible changes
 *               and full removal. We welcome feedback for incubating features.
 */
@Api.Incubating
public class HttpEntryPoint {
    private HttpEntryPoint() {
    }

    /**
     * Interceptor of an HTTP entry point.
     * This interceptor has direct access to {@link io.helidon.webserver.http.ServerRequest} and
     * {@link io.helidon.webserver.http.ServerResponse}, as well as metadata of the invoked endpoint.
     */
    public interface Interceptor {
        /**
         * Method to implement interceptor logic.
         *
         * @param interceptionContext context with invocation metadata
         * @param chain             invocation chain, interceptor must call
         *                          {@link io.helidon.webserver.http.HttpEntryPoint.Interceptor.Chain#proceed
         *                          (io.helidon.webserver.http.ServerRequest, io.helidon.webserver.http.ServerResponse)}
         * @param request           server request
         * @param response          server response
         * @throws java.lang.Exception in case the invocation fails, or the interceptor needs to stop processing of the
         *                             request with an exception
         */
        void proceed(InterceptionContext interceptionContext,
                     Chain chain,
                     ServerRequest request,
                     ServerResponse response) throws Exception;

        /**
         * Invocation chain for HTTP Entry point.
         */
        interface Chain extends Interception.Interceptor.Chain<Void> {
            @Override
            default Void proceed(Object[] args) throws Exception {
                proceed((ServerRequest) args[0], (ServerResponse) args[1]);
                return null;
            }

            /**
             * Invoke the next interceptor in the chain.
             *
             * @param request  server request
             * @param response server response
             * @throws java.lang.Exception in case the invocation fails, this should not be handled by most
             *                             interceptors
             */
            void proceed(ServerRequest request, ServerResponse response) throws Exception;
        }
    }

    /**
     * Authorization-only interceptor for an operation discovered inside an active HTTP entry point.
     * <p>
     * This contract is intended for protocol integrations that must authorize a framework-owned operation after request
     * parsing has identified it. Authentication and the remaining HTTP entry point interceptors are handled by the enclosing
     * HTTP entry point and must not be repeated by this interceptor.
     */
    @Api.Internal
    @Service.Contract
    public interface AuthorizationInterceptor {
        /**
         * Authorize the operation.
         *
         * @param interceptionContext context with invocation metadata
         * @param chain authorization interceptor chain
         * @param request server request
         * @param response server response
         * @throws Exception in case authorization fails with an exception
         */
        void authorize(InterceptionContext interceptionContext,
                       Interceptor.Chain chain,
                       ServerRequest request,
                       ServerResponse response) throws Exception;
    }

    /**
     * A contract used from generated code to invoke HTTP entry point interceptors.
     */
    public interface EntryPoints {
        /**
         * Handler that triggers interceptors.
         *
         * @param descriptor descriptor of the invoked endpoint (not generated code)
         * @param typeQualifiers qualifiers of the invoked endpoint
         * @param typeAnnotations type annotations of the invoked endpoint
         * @param methodInfo method information of the endpoint method
         * @param actualHandler handler that invokes the endpoint method
         * @return handler to register with WebServer
         */
        Handler handler(ServiceDescriptor<?> descriptor,
                        Set<Qualifier> typeQualifiers,
                        List<Annotation> typeAnnotations,
                        TypedElementInfo methodInfo,
                        Handler actualHandler);

        /**
         * Handler that triggers authorization-only interceptors for an operation discovered inside an active HTTP entry
         * point.
         * <p>
         * The default fails because invoking the complete entry point chain would repeat authentication and other interceptors.
         *
         * @param descriptor descriptor of the invoked endpoint (not generated code)
         * @param typeQualifiers qualifiers of the invoked endpoint
         * @param typeAnnotations type annotations of the invoked endpoint
         * @param methodInfo method information of the framework-owned operation
         * @param actualHandler handler that invokes the operation
         * @return handler to invoke inside the active HTTP entry point
         */
        default Handler authorizationHandler(ServiceDescriptor<?> descriptor,
                                             Set<Qualifier> typeQualifiers,
                                             List<Annotation> typeAnnotations,
                                             TypedElementInfo methodInfo,
                                             Handler actualHandler) {
            throw new UnsupportedOperationException("Authorization-only HTTP entry point interception is not supported");
        }
    }
}
