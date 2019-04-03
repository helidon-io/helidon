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

package io.helidon.grpc.server;

import java.util.HashMap;
import java.util.Map;

import io.helidon.grpc.core.InterceptorPriority;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;

import static io.helidon.grpc.core.GrpcHelper.extractMethodName;

/**
 * A {@link io.grpc.ServerInterceptor} that sets values into the
 * gRPC call context.
 *
 * @author Jonathan Knight
 */
class ContextSettingServerInterceptor
        implements PriorityServerInterceptor, ServiceDescriptor.Aware {

    /**
     * The {@link ServiceDescriptor} for the service being intercepted.
     */
    private ServiceDescriptor serviceDescriptor;

    @Override
    @SuppressWarnings("unchecked")
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {

        Context context = Context.current();
        String fullMethodName = call.getMethodDescriptor().getFullMethodName();
        String methodName = extractMethodName(fullMethodName);
        MethodDescriptor methodDescriptor = serviceDescriptor.method(methodName);
        Map<Context.Key<?>, Object> contextMap = new HashMap<>();

        contextMap.putAll(serviceDescriptor.context());
        contextMap.putAll(methodDescriptor.context());
        contextMap.put(ServiceDescriptor.SERVICE_DESCRIPTOR_KEY, serviceDescriptor);

        if (!contextMap.isEmpty()) {
            for (Map.Entry<Context.Key<?>, Object> entry : contextMap.entrySet()) {
                Context.Key<Object> key = (Context.Key<Object>) entry.getKey();
                context = context.withValue(key, entry.getValue());
            }
        }

        return Contexts.interceptCall(context, call, headers, next);
    }

    @Override
    public InterceptorPriority getInterceptorPriority() {
        return InterceptorPriority.Context;
    }

    @Override
    public void setServiceDescriptor(ServiceDescriptor descriptor) {
        this.serviceDescriptor = descriptor;
    }
}
