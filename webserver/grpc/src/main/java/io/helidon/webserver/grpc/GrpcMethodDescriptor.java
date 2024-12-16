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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.helidon.grpc.core.InterceptorWeights;
import io.helidon.grpc.core.MarshallerSupplier;
import io.helidon.grpc.core.WeightedBag;

import io.grpc.Context;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * Encapsulates all metadata necessary to define a gRPC method.
 *
 * @param <ReqT> request type
 * @param <ResT> response type
 */
public class GrpcMethodDescriptor<ReqT, ResT> {
    private final String name;
    private final MethodDescriptor<ReqT, ResT> descriptor;
    private final ServerCallHandler<ReqT, ResT> callHandler;
    private final Map<Context.Key<?>, Object> context;
    private final WeightedBag<ServerInterceptor> interceptors;
    private final Class<ReqT> requestType;
    private final Class<ResT> responseType;

    private GrpcMethodDescriptor(String name,
                                 MethodDescriptor<ReqT, ResT> descriptor,
                                 Class<ReqT> requestType,
                                 Class<ResT> responseType,
                                 ServerCallHandler<ReqT, ResT> callHandler,
                                 Map<Context.Key<?>, Object> context,
                                 WeightedBag<ServerInterceptor> interceptors) {
        this.name = name;
        this.descriptor = descriptor;
        this.requestType = requestType;
        this.responseType = responseType;
        this.callHandler = callHandler;
        this.context = context;
        this.interceptors = interceptors.copyMe();
    }

    /**
     * Return the name of the method.
     *
     * @return method name
     */
    public String name() {
        return name;
    }

    /**
     * Return gRPC method descriptor.
     *
     * @return gRPC method descriptor
     */
    public MethodDescriptor<ReqT, ResT> descriptor() {
        return descriptor;
    }

    /**
     * Return the call handler.
     *
     * @return call handler
     */
    public ServerCallHandler<ReqT, ResT> callHandler() {
        return callHandler;
    }

    /**
     * Return the method's request type.
     *
     * @return request type
     */
    public Class<ReqT> requestType() {
        return requestType;
    }

    /**
     * Return the method's response type.
     *
     * @return response type
     */
    public Class<ResT> responseType() {
        return responseType;
    }

    /**
     * Obtain the {@link Map} of {@link Context.Key}s and values to add to the
     * call context when this method is invoked.
     *
     * @return an unmodifiable {@link Map} of {@link Context.Key}s and values to
     * add to the call context when this method is invoked
     */
    public Map<Context.Key<?>, Object> context() {
        return Collections.unmodifiableMap(context);
    }

    /**
     * Obtain the {@link io.grpc.ServerInterceptor}s to use for this method.
     *
     * @return the {@link io.grpc.ServerInterceptor}s to use for this method
     */
    public WeightedBag<ServerInterceptor> interceptors() {
        return interceptors.readOnly();
    }

    static <ReqT, ResT> Builder<ReqT, ResT> builder(String serviceName,
                                                    String name,
                                                    MethodDescriptor.Builder<ReqT, ResT> descriptor,
                                                    ServerCallHandler<ReqT, ResT> callHandler) {
        return new Builder<>(serviceName, name, descriptor, callHandler);
    }

    static <ReqT, ResT> GrpcMethodDescriptor<ReqT, ResT> create(String serviceName,
                                                                String name,
                                                                MethodDescriptor.Builder<ReqT, ResT> descriptor,
                                                                ServerCallHandler<ReqT, ResT> callHandler) {
        return builder(serviceName, name, descriptor, callHandler).build();
    }

    @Override
    public String toString() {
        String fullName = descriptor == null ? null : descriptor.getFullMethodName();
        return "MethodDescriptor("
                + "name='" + name + '\''
                + "fullName='" + fullName + "\')";
    }

    /**
     * Method configuration API.
     *
     * @param <ReqT> request type
     * @param <ResT> response type
     */
    public interface Rules<ReqT, ResT> {
        /**
         * Add a {@link Context.Key} and value to be added to the call {@link io.grpc.Context}
         * when this method is invoked.
         *
         * @param key the {@link Context.Key} to add
         * @param value the value to map to the {@link Context.Key}
         * @param <T> the type of the {@link Context.Key} and value
         * @return this {@link Rules} instance for fluent call chaining
         * @throws NullPointerException if the key parameter is null
         */

        <T> Rules<ReqT, ResT> addContextValue(Context.Key<T> key, T value);

        /**
         * Register one or more {@link io.grpc.ServerInterceptor interceptors} for the method.
         *
         * @param interceptors one or more {@link ServerInterceptor}s to register
         * @return this builder to allow fluent method chaining
         */
        Rules<ReqT, ResT> intercept(ServerInterceptor... interceptors);

        /**
         * Register one or more {@link io.grpc.ServerInterceptor interceptors} for the method.
         * <p>
         * The added interceptors will be applied using the specified priority.
         *
         * @param priority the priority to assign to the interceptors
         * @param interceptors one or more {@link ServerInterceptor}s to register
         * @return this builder to allow fluent method chaining
         */
        Rules<ReqT, ResT> intercept(int priority, ServerInterceptor... interceptors);

        /**
         * Register the {@link MarshallerSupplier} for the method.
         * <p>
         * If not set the default {@link MarshallerSupplier} from the service will be used.
         *
         * @param marshallerSupplier the {@link MarshallerSupplier} for the service
         * @return this {@link GrpcServiceDescriptor.Rules} instance for fluent call chaining
         */
        Rules<ReqT, ResT> marshallerSupplier(MarshallerSupplier marshallerSupplier);

        /**
         * Set the request type.
         * <p>
         * Setting the request type is optional as it is used to obtain the
         * correct marshaller so if the marshaller supplier being used is type
         * agnostic, such as Java serialization then whereas some marshallers
         * such as Protocol Buffers require a type.
         *
         * @param requestType the type of the request message
         * @param <Rnew> the type of the request message
         * @return this {@link GrpcServiceDescriptor.Rules} instance
         * for fluent call chaining
         */
        <Rnew> Rules<Rnew, ResT> requestType(Class<Rnew> requestType);

        /**
         * Set the response type.
         * <p>
         * Setting the response type is optional as it is used to obtain the
         * correct marshaller so if the marshaller supplier being used is type
         * agnostic, such as Java serialization then whereas some marshallers
         * such as Protocol Buffers require a type.
         *
         * @param responseType the type of the request message
         * @param <Rnew> the type of the request message
         * @return this {@link GrpcServiceDescriptor.Rules} instance
         * for fluent call chaining
         */
        <Rnew> Rules<ReqT, Rnew> responseType(Class<Rnew> responseType);
    }

    /**
     * An interface implemented by classes that can configure
     * a {@link Rules}.
     *
     * @param <ReqT> request type
     * @param <ResT> response type
     */
    @FunctionalInterface
    public interface Configurer<ReqT, ResT> {
        /**
         * Apply extra configuration to a {@link Rules}.
         *
         * @param rules the {@link Rules} to configure
         */
        void configure(Rules<ReqT, ResT> rules);
    }

    /**
     * {@link GrpcMethodDescriptor} builder implementation.
     *
     * @param <ReqT> request type
     * @param <ResT> response type
     */
    static final class Builder<ReqT, ResT>
            implements Rules<ReqT, ResT>,
            io.helidon.common.Builder<Builder<ReqT, ResT>, GrpcMethodDescriptor<ReqT, ResT>> {
        private final String name;
        private final MethodDescriptor.Builder<ReqT, ResT> descriptor;
        private final ServerCallHandler<ReqT, ResT> callHandler;

        private final WeightedBag<ServerInterceptor> interceptors = WeightedBag.create(InterceptorWeights.USER);

        private final Map<Context.Key<?>, Object> context = new HashMap<>();

        private Class<?> requestType;

        private Class<?> responseType;

        private MarshallerSupplier defaultMarshallerSupplier = MarshallerSupplier.create();
        private MarshallerSupplier marshallerSupplier;

        Builder(String serviceName,
                String name,
                MethodDescriptor.Builder<ReqT, ResT> descriptor,
                ServerCallHandler<ReqT, ResT> callHandler) {

            this.name = name;
            this.callHandler = callHandler;
            this.descriptor = descriptor.setFullMethodName(serviceName + "/" + name);
        }

        Builder<ReqT, ResT> fullname(String name) {
            descriptor.setFullMethodName(name);
            return this;
        }

        @Override
        public Builder<ReqT, ResT> marshallerSupplier(MarshallerSupplier supplier) {
            this.marshallerSupplier = supplier;
            return this;
        }

        Builder<ReqT, ResT> defaultMarshallerSupplier(MarshallerSupplier supplier) {
            if (supplier == null) {
                this.defaultMarshallerSupplier = MarshallerSupplier.create();
            } else {
                this.defaultMarshallerSupplier = supplier;
            }
            return this;
        }

        @Override
        public <T> Builder<ReqT, ResT> addContextValue(Context.Key<T> key, T value) {
            context.put(Objects.requireNonNull(key, "The context key cannot be null"), value);
            return this;
        }

        @Override
        public Builder<ReqT, ResT> intercept(ServerInterceptor... interceptors) {
            this.interceptors.addAll(Arrays.asList(interceptors));
            processInterceptors(interceptors);
            return this;
        }

        @Override
        public Rules<ReqT, ResT> intercept(int priority, ServerInterceptor... interceptors) {
            this.interceptors.addAll(Arrays.asList(interceptors), priority);
            processInterceptors(interceptors);
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <Rnew> Builder<Rnew, ResT> requestType(Class<Rnew> requestType) {
            this.requestType = requestType;
            return (Builder<Rnew, ResT>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <Rnew> Builder<ReqT, Rnew> responseType(Class<Rnew> responseType) {
            this.responseType = responseType;
            return (Builder<ReqT, Rnew>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public GrpcMethodDescriptor<ReqT, ResT> build() {
            MarshallerSupplier supplier = this.marshallerSupplier;

            if (supplier == null) {
                supplier = defaultMarshallerSupplier;
            }

            if (requestType != null) {
                descriptor.setRequestMarshaller((MethodDescriptor.Marshaller<ReqT>) supplier.get(requestType));
            }

            if (responseType != null) {
                descriptor.setResponseMarshaller((MethodDescriptor.Marshaller<ResT>) supplier.get(responseType));
            }

            return new GrpcMethodDescriptor<>(name,
                    descriptor.build(),
                    (Class) requestType,
                    (Class) requestType,
                    callHandler,
                    context,
                    interceptors);
        }

        @SuppressWarnings("unchecked")
        private void processInterceptors(ServerInterceptor... interceptors) {
            // If any interceptors implement MethodDescriptor.Configurer allow them to apply further configuration
            Arrays.stream(interceptors)
                    .filter(interceptor -> Configurer.class.isAssignableFrom(interceptor.getClass()))
                    .map(Configurer.class::cast)
                    .forEach(interceptor -> interceptor.configure(this));
        }
    }
}
