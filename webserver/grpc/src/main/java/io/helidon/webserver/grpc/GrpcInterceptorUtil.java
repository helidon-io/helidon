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

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
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
        // remove duplicates and set proper ordering
        for (ServerInterceptor interceptor : interceptors.stream()
                .distinct()
                .toList()
                .reversed()) {
            handler = new InterceptingCallHandler<>(serviceDescriptor, interceptor, handler);
        }
        return handler;
    }

    /**
     * A server call handler that sets a Helidon context and, if available, the
     * current service descriptor in it before calling the next interceptor.
     *
     * @param <ReqT>  the request type
     * @param <RespT> the response type
     */
    static final class InterceptingCallHandler<ReqT, RespT> implements ServerCallHandler<ReqT, RespT> {
        private final GrpcServiceDescriptor serviceDescriptor;
        private final ServerInterceptor interceptor;
        private final ServerCallHandler<ReqT, RespT> callHandler;

        private InterceptingCallHandler(GrpcServiceDescriptor serviceDescriptor,
                                        ServerInterceptor interceptor,
                                        ServerCallHandler<ReqT, RespT> callHandler) {
            this.serviceDescriptor = serviceDescriptor;
            this.interceptor = interceptor;
            this.callHandler = callHandler;
        }

        @Override
        public ServerCall.Listener<ReqT> startCall(
                ServerCall<ReqT, RespT> call,
                Metadata headers) {
            Context context = Contexts.context().orElse(Context.create());
            if (serviceDescriptor != null) {
                context.register(GrpcServiceDescriptor.class, serviceDescriptor);
            }
            return Contexts.runInContext(context, () -> interceptor.interceptCall(call, headers, callHandler));
        }
    }
}
