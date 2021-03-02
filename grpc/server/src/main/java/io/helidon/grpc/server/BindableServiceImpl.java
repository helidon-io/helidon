/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.grpc.core.InterceptorPriorities;
import io.helidon.grpc.core.PriorityBag;

import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;
import io.grpc.stub.StreamObserver;

/**
 * A {@link io.grpc.BindableService} implementation that creates {@link io.grpc.ServerServiceDefinition}
 * from a {@link ServiceDescriptor}.
 */
class BindableServiceImpl implements BindableService {
    /**
     * The descriptor of this service.
     */
    private final ServiceDescriptor descriptor;

    /**
     * The global interceptors to apply.
     */
    private final PriorityBag<ServerInterceptor> globalInterceptors;

    private BindableServiceImpl(ServiceDescriptor descriptor, PriorityBag<ServerInterceptor> interceptors) {
        this.descriptor = descriptor;
        this.globalInterceptors = interceptors.copyMe();
    }

    /**
     * Create a {@link BindableServiceImpl} for a gRPC service.
     *
     * @param descriptor    the service descriptor
     * @param interceptors  the bag of interceptors to apply to the service
     *
     * @return a {@link BindableServiceImpl} for the gRPC service
     */
    static BindableServiceImpl create(ServiceDescriptor descriptor, PriorityBag<ServerInterceptor> interceptors) {
        return new BindableServiceImpl(descriptor, interceptors);
    }

    // ---- BindableService implementation ----------------------------------

    @SuppressWarnings("unchecked")
    @Override
    public ServerServiceDefinition bindService() {
        io.grpc.ServiceDescriptor.Builder serviceDescriptorBuilder = io.grpc.ServiceDescriptor.newBuilder(descriptor.name());
        if (descriptor.proto() != null) {
            serviceDescriptorBuilder.setSchemaDescriptor((ProtoFileDescriptorSupplier) descriptor::proto);
        }
        descriptor.methods()
                .forEach(method -> serviceDescriptorBuilder.addMethod(method.descriptor()));

        ServerServiceDefinition.Builder builder = ServerServiceDefinition.builder(serviceDescriptorBuilder.build());
        descriptor.methods()
                .forEach(method -> builder.addMethod(method.descriptor(), wrapCallHandler(method)));

        return builder.build();
    }

    // ---- helpers ---------------------------------------------------------

    private <ReqT, RespT> ServerCallHandler<ReqT, RespT> wrapCallHandler(MethodDescriptor<ReqT, RespT> method) {
        ServerCallHandler<ReqT, RespT> handler = method.callHandler();

        PriorityBag<ServerInterceptor> priorityServerInterceptors = PriorityBag.withDefaultPriority(InterceptorPriorities.USER);
        priorityServerInterceptors.addAll(globalInterceptors);
        priorityServerInterceptors.addAll(descriptor.interceptors());
        priorityServerInterceptors.addAll(method.interceptors());
        List<ServerInterceptor> interceptors = priorityServerInterceptors.stream().collect(Collectors.toList());

        if (!interceptors.isEmpty()) {
            LinkedHashSet<ServerInterceptor> uniqueInterceptors = new LinkedHashSet<>(interceptors.size());

            // iterate the interceptors in reverse order so that the handler chain is in the correct order
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

    static <T> Supplier<T> createSupplier(Callable<T> callable) {
        return new CallableSupplier<>(callable);
    }

    static class CallableSupplier<T> implements Supplier<T> {
        private Callable<T> callable;

        CallableSupplier(Callable<T> callable) {
            this.callable = callable;
        }

        @Override
        public T get() {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new CompletionException(e.getMessage(), e);
            }
        }
    }

    static <T, U> BiConsumer<T, Throwable> completeWithResult(StreamObserver<U> observer) {
        return new CompletionAction<>(observer, true);
    }

    static <U> BiConsumer<Void, Throwable> completeWithoutResult(StreamObserver<U> observer) {
        return new CompletionAction<>(observer, false);
    }

    static class CompletionAction<T, U> implements BiConsumer<T, Throwable> {
        private StreamObserver<U> observer;
        private boolean sendResult;

        CompletionAction(StreamObserver<U> observer, boolean sendResult) {
            this.observer = observer;
            this.sendResult = sendResult;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void accept(T result, Throwable error) {
            if (error != null) {
                observer.onError(error);
            } else {
                if (sendResult) {
                    observer.onNext((U) result);
                }
                observer.onCompleted();
            }
        }
    }

    /**
     * A {@link ServerCallHandler} that wraps a {@link ServerCallHandler} with
     * a {@link ServerInterceptor}.
     * <p>
     * If the wrapped {@link ServerInterceptor} implements {@link ServiceDescriptor.Aware}
     * then the {@link ServiceDescriptor.Aware#setServiceDescriptor(ServiceDescriptor)} method
     * will be called before calling {@link ServerInterceptor#interceptCall(io.grpc.ServerCall,
     * io.grpc.Metadata, io.grpc.ServerCallHandler)}.
     *
     * @param <ReqT>   the request type
     * @param <RespT>  the response type
     */
    static final class InterceptingCallHandler<ReqT, RespT> implements ServerCallHandler<ReqT, RespT> {
        private final ServiceDescriptor serviceDefinition;
        private final ServerInterceptor interceptor;
        private final ServerCallHandler<ReqT, RespT> callHandler;

        private InterceptingCallHandler(ServiceDescriptor serviceDefinition,
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
            if (interceptor instanceof ServiceDescriptor.Aware) {
                ((ServiceDescriptor.Aware) interceptor).setServiceDescriptor(serviceDefinition);
            }
            return interceptor.interceptCall(call, headers, callHandler);
        }
    }
}
