/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceDescriptor;

/**
 * Container class for types related to HTTP entry points.
 */
public class HttpEntryPoint {
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
    }
}
