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
package io.helidon.webserver.grpc;

import java.util.LinkedHashSet;
import java.util.List;

import io.helidon.grpc.core.WeightedBag;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

class GrpcInterceptorUtil {

    private GrpcInterceptorUtil() {
    }

    static <ReqT, RespT> ServerCallHandler<ReqT, RespT> interceptHandler(ServerCallHandler<ReqT, RespT> handler,
                                                                         WeightedBag<ServerInterceptor> interceptors) {
        return interceptHandler(handler, interceptors, null);
    }

    static <ReqT, RespT> ServerCallHandler<ReqT, RespT> interceptHandler(ServerCallHandler<ReqT, RespT> handler,
                                                                         WeightedBag<ServerInterceptor> interceptors,
                                                                         GrpcServiceDescriptor serviceDescriptor) {
        // always add a context setting interceptor
        interceptors.add(ContextSettingServerInterceptor.create());

        // remove duplicates and set proper ordering
        List<ServerInterceptor> interceptorsList = interceptors.stream().toList();
        LinkedHashSet<ServerInterceptor> interceptorsSet = new LinkedHashSet<>(interceptorsList.size());
        // iterate the interceptors in reverse order to set up handler chain
        for (int i = interceptorsList.size() - 1; i >= 0; i--) {
            ServerInterceptor interceptor = interceptorsList.get(i);
            interceptorsSet.add(interceptor);
        }

        // create interceptor chain
        for (ServerInterceptor interceptor : interceptorsSet) {
            handler = new InterceptingCallHandler<>(serviceDescriptor, interceptor, handler);
        }

        return handler;
    }

    /**
     * A {@link ServerCallHandler} that wraps a {@link ServerCallHandler} with
     * a {@link ServerInterceptor}.
     * <p>
     * If the wrapped {@link ServerInterceptor} implements {@link GrpcServiceDescriptor.Aware}
     * then the {@link GrpcServiceDescriptor.Aware#setServiceDescriptor(GrpcServiceDescriptor)}
     * method will be called before interception.
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    static final class InterceptingCallHandler<ReqT, RespT> implements ServerCallHandler<ReqT, RespT> {
        private final GrpcServiceDescriptor serviceDefinition;
        private final ServerInterceptor interceptor;
        private final ServerCallHandler<ReqT, RespT> callHandler;

        private InterceptingCallHandler(GrpcServiceDescriptor serviceDefinition,
                                        ServerInterceptor interceptor,
                                        ServerCallHandler<ReqT, RespT> callHandler) {
            this.serviceDefinition = serviceDefinition;
            this.interceptor = interceptor;
            this.callHandler = callHandler;
        }

        @Override
        public ServerCall.Listener<ReqT> startCall(
                ServerCall<ReqT, RespT> call,
                Metadata headers) {
            if (serviceDefinition != null && interceptor instanceof GrpcServiceDescriptor.Aware) {
                ((GrpcServiceDescriptor.Aware) interceptor).setServiceDescriptor(serviceDefinition);
            }
            return interceptor.interceptCall(call, headers, callHandler);
        }
    }
}
