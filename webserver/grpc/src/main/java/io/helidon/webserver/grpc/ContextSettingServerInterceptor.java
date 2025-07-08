/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.Weight;
import io.helidon.grpc.core.ContextKeys;
import io.helidon.grpc.core.InterceptorWeights;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

import static io.helidon.grpc.core.GrpcHelper.extractMethodName;

/**
 * A {@link io.grpc.ServerInterceptor} that sets values into the gRPC call context.
 */
@Weight(InterceptorWeights.CONTEXT)
class ContextSettingServerInterceptor implements ServerInterceptor {

    private static final ContextSettingServerInterceptor INSTANCE = new ContextSettingServerInterceptor();

    private ContextSettingServerInterceptor() {
    }

    static ContextSettingServerInterceptor instance() {
        return INSTANCE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <ReqT, ResT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, ResT> call,
                                                                Metadata headers,
                                                                ServerCallHandler<ReqT, ResT> next) {
        Context context = Context.current();

        // set Helidon context into gRPC context
        Optional<io.helidon.common.context.Context> helidonContext =
                io.helidon.common.context.Contexts.context();
        context = context.withValue(ContextKeys.HELIDON_CONTEXT,
                helidonContext.orElseGet(io.helidon.common.context.Context::create));

        if (helidonContext.isPresent()) {
            GrpcServiceDescriptor serviceDescriptor = helidonContext.get()
                    .get(GrpcServiceDescriptor.class, GrpcServiceDescriptor.class)
                    .orElse(null);

            if (serviceDescriptor != null) {
                // method info
                String fullMethodName = call.getMethodDescriptor().getFullMethodName();
                String methodName = extractMethodName(fullMethodName);
                GrpcMethodDescriptor<ReqT, ResT> methodDescriptor =
                        (GrpcMethodDescriptor<ReqT, ResT>) serviceDescriptor.method(methodName);

                // apply context keys from the service followed by the method for overrides
                Map<Context.Key<?>, Object> contextMap = new HashMap<>();
                contextMap.putAll(serviceDescriptor.context());
                contextMap.putAll(methodDescriptor.context());
                contextMap.put(GrpcServiceDescriptor.SERVICE_DESCRIPTOR_KEY, serviceDescriptor);

                // set up context from gRPC API
                for (Map.Entry<Context.Key<?>, Object> entry : contextMap.entrySet()) {
                    Context.Key<Object> key = (Context.Key<Object>) entry.getKey();
                    context = context.withValue(key, entry.getValue());
                }
            }
        }

        // intercept with new context
        return Contexts.interceptCall(context, call, headers, next);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
