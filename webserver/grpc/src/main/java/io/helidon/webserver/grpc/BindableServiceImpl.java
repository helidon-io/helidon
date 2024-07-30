/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

import io.helidon.grpc.core.InterceptorWeights;
import io.helidon.grpc.core.WeightedBag;

import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;

/**
 * A {@link BindableService} implementation that creates {@link ServerServiceDefinition}
 * from a {@link GrpcServiceDescriptor}.
 */
class BindableServiceImpl implements BindableService {
    /**
     * The descriptor of this service.
     */
    private final GrpcServiceDescriptor descriptor;

    /**
     * The global interceptors to apply.
     */
    private final WeightedBag<ServerInterceptor> interceptors;

    private BindableServiceImpl(GrpcServiceDescriptor descriptor, WeightedBag<ServerInterceptor> interceptors) {
        this.descriptor = descriptor;
        this.interceptors = interceptors.copyMe();
    }

    /**
     * Create a {@link BindableServiceImpl} for a gRPC service.
     *
     * @param descriptor the service descriptor
     * @param interceptors the bag of interceptors to apply to the service
     * @return a {@link BindableServiceImpl} for the gRPC service
     */
    static BindableServiceImpl create(GrpcServiceDescriptor descriptor, WeightedBag<ServerInterceptor> interceptors) {
        return new BindableServiceImpl(descriptor, interceptors);
    }

    // ---- BindableService implementation ----------------------------------

    @SuppressWarnings("unchecked")
    @Override
    public ServerServiceDefinition bindService() {
        ServiceDescriptor.Builder serviceDescriptorBuilder = ServiceDescriptor.newBuilder(descriptor.fullName());
        if (descriptor.proto() != null) {
            serviceDescriptorBuilder.setSchemaDescriptor((ProtoFileDescriptorSupplier) descriptor::proto);
        }
        descriptor.methods().forEach(method -> serviceDescriptorBuilder.addMethod(method.descriptor()));

        ServerServiceDefinition.Builder builder = ServerServiceDefinition.builder(serviceDescriptorBuilder.build());
        descriptor.methods()
                .forEach(method -> builder.addMethod((MethodDescriptor) method.descriptor(),
                        wrapCallHandler(method)));

        return builder.build();
    }

    // ---- helpers ---------------------------------------------------------

    private <ReqT, RespT> ServerCallHandler<ReqT, RespT> wrapCallHandler(GrpcMethodDescriptor<ReqT, RespT> method) {
        ServerCallHandler<ReqT, RespT> handler = method.callHandler();

        WeightedBag<ServerInterceptor> priorityServerInterceptors = WeightedBag.create(InterceptorWeights.USER);
        priorityServerInterceptors.addAll(interceptors);
        priorityServerInterceptors.addAll(descriptor.interceptors());
        priorityServerInterceptors.addAll(method.interceptors());
        List<ServerInterceptor> interceptors = priorityServerInterceptors.stream().toList();

        if (!interceptors.isEmpty()) {
            LinkedHashSet<ServerInterceptor> uniqueInterceptors = new LinkedHashSet<>(interceptors.size());

            // iterate the interceptors in reverse order to set up handler chain
            for (int i = interceptors.size() - 1; i >= 0; i--) {
                ServerInterceptor interceptor = interceptors.get(i);
                if (!uniqueInterceptors.contains(interceptor)) {
                    uniqueInterceptors.add(interceptor);
                }
            }

            for (ServerInterceptor interceptor : uniqueInterceptors) {
                handler = new InterceptingCallHandler<>(descriptor, interceptor, handler);
            }
        }

        return handler;
    }

    /**
     * A {@link ServerCallHandler} that wraps a {@link ServerCallHandler} with
     * a {@link ServerInterceptor}.
     * <p>
     * If the wrapped {@link ServerInterceptor} implements {@link GrpcServiceDescriptor.Aware}
     * then the {@link GrpcServiceDescriptor.Aware#setServiceDescriptor(GrpcServiceDescriptor)} method
     * will be called before calling {@link ServerInterceptor#interceptCall(ServerCall,
     * Metadata, ServerCallHandler)}.
     *
     * @param <ReqT> the request type
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
            if (interceptor instanceof GrpcServiceDescriptor.Aware) {
                ((GrpcServiceDescriptor.Aware) interceptor).setServiceDescriptor(serviceDefinition);
            }
            return interceptor.interceptCall(call, headers, callHandler);
        }
    }
}
