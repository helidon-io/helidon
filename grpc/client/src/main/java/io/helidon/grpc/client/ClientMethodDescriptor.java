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

package io.helidon.grpc.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.helidon.grpc.core.MarshallerSupplier;

import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import org.eclipse.microprofile.metrics.MetricType;

/**
 * Encapsulates all metadata necessary to define a gRPC method. In addition to wrapping a {@link io.grpc.MethodDescriptor},
 * this class also holds the request and response types of the gRPC method. A
 * {@link io.helidon.grpc.client.ClientServiceDescriptor} can contain zero or more {@link io.grpc.MethodDescriptor}.
 *
 * An instance of ClientMethodDescriptor can be created either from an existing {@link io.grpc.MethodDescriptor} or
 * from one of the factory methods {@link #bidirectional(String, String)}, {@link #clientStreaming(String, String)},
 * {@link #serverStreaming(String, String)} or {@link #unary(String, String)}.
 *
 * @author Mahesh Kannan
 */
public final class ClientMethodDescriptor {

    /**
     * The simple name of the method.
     */
    private String name;

    /**
     * The {@link io.grpc.MethodDescriptor} for this method. This is usually obtained from protocol buffer
     * method getDescriptor (from service getDescriptor).
     */
    private io.grpc.MethodDescriptor descriptor;

    /**
     * The metric type to be used for collecting method level metrics.
     */
    private MetricType metricType;

    /**
     * The list of client interceptors for this method.
     */
    private ArrayList<ClientInterceptor> interceptors;

    private ClientMethodDescriptor(String name,
                                   MethodDescriptor descriptor,
                                   MetricType metricType,
                                   ArrayList<ClientInterceptor> interceptors) {
        this.name = name;
        this.descriptor = descriptor;
        this.metricType = metricType;
        this.interceptors = interceptors;
    }

    /**
     * Creates a new {@link ClientMethodDescriptor.Builder} with the specified name and {@link io.grpc.MethodDescriptor}.
     *
     * @param serviceName the name of the owning gRPC service
     * @param name        the simple method name
     * @param descriptor  the underlying gRPC {@link io.grpc.MethodDescriptor.Builder}
     * @return A new instance of a {@link ClientMethodDescriptor.Builder}
     */
    static  Builder builder(String serviceName,
                                                    String name,
                                                    io.grpc.MethodDescriptor.Builder descriptor) {
        return new Builder(serviceName, name, descriptor);
    }

    /**
     * Creates a new {@link ClientMethodDescriptor.Builder} with the specified
     * name and {@link io.grpc.MethodDescriptor}.
     *
     * @param serviceName the name of the owning gRPC service
     * @param name        the simple method name
     * @param descriptor  the underlying gRPC {@link io.grpc.MethodDescriptor.Builder}
     * @return a new instance of a {@link ClientMethodDescriptor.Builder}
     */
    static  ClientMethodDescriptor create(String serviceName,
                                          String name,
                                          io.grpc.MethodDescriptor.Builder descriptor) {
        return builder(serviceName, name, descriptor).build();
    }

    /**
     * Creates a new unary {@link ClientMethodDescriptor.Builder} with
     * the specified name.
     *
     * @param serviceName the name of the owning gRPC service
     * @param name        the method name
     * @return a new instance of a {@link ClientMethodDescriptor.Builder}
     */
    static  ClientMethodDescriptor.Builder unary(String serviceName, String name) {
        return builder(serviceName, name, MethodDescriptor.MethodType.UNARY);
    }

    /**
     * Creates a new client Streaming {@link ClientMethodDescriptor.Builder} with
     * the specified name.
     *
     * @param serviceName the name of the owning gRPC service
     * @param name        the method name
     * @return a new instance of a {@link ClientMethodDescriptor.Builder}
     */
    static  ClientMethodDescriptor.Builder clientStreaming(String serviceName, String name) {
        return builder(serviceName, name, MethodDescriptor.MethodType.CLIENT_STREAMING);
    }

    /**
     * Creates a new server streaming {@link ClientMethodDescriptor.Builder} with
     * the specified name.
     *
     * @param serviceName the name of the owning gRPC service
     * @param name        the method name
     * @return a new instance of a {@link ClientMethodDescriptor.Builder}
     */
    static  ClientMethodDescriptor.Builder serverStreaming(String serviceName, String name) {
        return builder(serviceName, name, MethodDescriptor.MethodType.SERVER_STREAMING);
    }

    /**
     * Creates a new bidirectional {@link ClientMethodDescriptor.Builder} with
     * the specified name.
     *
     * @param serviceName the name of the owning gRPC service
     * @param name        the method name
     * @return a new instance of a {@link ClientMethodDescriptor.Builder}
     */
    static  ClientMethodDescriptor.Builder bidirectional(String serviceName, String name) {
        return builder(serviceName, name, MethodDescriptor.MethodType.BIDI_STREAMING);
    }

    /**
     * Creates a new {@link ClientMethodDescriptor.Builder} with the specified name.
     *
     * @param serviceName the name of the owning gRPC service
     * @param name        the method name
     * @param methodType  the gRPC method type
     * @return a new instance of a {@link ClientMethodDescriptor.Builder}
     */
    static  ClientMethodDescriptor.Builder builder(String serviceName,
                                                   String name,
                                                   MethodDescriptor.MethodType methodType) {

        MethodDescriptor.Builder builder = MethodDescriptor.newBuilder()
                .setFullMethodName(serviceName + "/" + name)
                .setType(methodType);

        return new Builder(serviceName, name, builder)
                .requestType(Object.class)
                .responseType(Object.class);
    }

    /**
     * Returns the simple name of the method.
     *
     * @return The simple name of the method.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the {@link io.grpc.MethodDescriptor} of this method.
     *
     * @param <ReqT> the request type
     * @param <RespT> the response type
     *
     * @return The {@link io.grpc.MethodDescriptor} of this method.
     */
    @SuppressWarnings("unchecked")
    public <ReqT, RespT> MethodDescriptor<ReqT, RespT> descriptor() {
        return descriptor;
    }

    /**
     * Returns the {@link org.eclipse.microprofile.metrics.MetricType} of this method.
     *
     * @return The {@link org.eclipse.microprofile.metrics.MetricType} of this method.
     */
    public MetricType metricType() {
        return metricType;
    }

    /**
     * Obtain the {@link ClientInterceptor}s to use for this method.
     *
     * @return the {@link ClientInterceptor}s to use for this method
     */
    List<ClientInterceptor> interceptors() {
        return Collections.unmodifiableList(interceptors);
    }

    /**
     * ClientMethod configuration API.
     */
    public interface Config {

        /**
         * Collect metrics for this method using {@link org.eclipse.microprofile.metrics.Counter}.
         *
         * @return this {@link Config} instance for fluent call chaining
         */

        Config counted();

        /**
         * Collect metrics for this method using {@link org.eclipse.microprofile.metrics.Meter}.
         *
         * @return this {@link Config} instance for fluent call chaining
         */
        Config metered();

        /**
         * Collect metrics for this method using {@link org.eclipse.microprofile.metrics.Histogram}.
         *
         * @return this {@link Config} instance for fluent call chaining
         */
        Config histogram();

        /**
         * Collect metrics for this method using {@link org.eclipse.microprofile.metrics.Timer}.
         *
         * @return this {@link Config} instance for fluent call chaining
         */
        Config timed();

        /**
         * Explicitly disable metrics collection for this service.
         *
         * @return this {@link Config} instance for fluent call chaining
         */
        Config disableMetrics();

        /**
         * Sets the type of parameter of this method.
         *
         * @param type The type of parameter of this method.
         * @return this {@link Config} instance for fluent call chaining
         */
        Config requestType(Class type);

        /**
         * Sets the type of parameter of this method.
         *
         * @param type The type of parameter of this method.
         * @return this {@link Config} instance for fluent call chaining
         */
        Config responseType(Class type);

        /**
         * Register one or more {@link ClientInterceptor interceptors} for the method.
         *
         * @param interceptors the interceptor(s) to register
         * @return this {@link io.helidon.grpc.client.ClientMethodDescriptor.Config} instance for fluent call chaining
         */
        Config intercept(ClientInterceptor... interceptors);

        /**
         * Register the {@link MarshallerSupplier} for the method.
         * <p>
         * If not set the default {@link MarshallerSupplier} from the service will be used.
         *
         * @param marshallerSupplier the {@link MarshallerSupplier} for the service
         * @return this {@link Config} instance for fluent call chaining
         */
        Config marshallerSupplier(MarshallerSupplier marshallerSupplier);
    }

    /**
     * {@link MethodDescriptor} builder implementation.
     */
    public static class Builder
            implements Config, io.helidon.common.Builder<ClientMethodDescriptor> {

        private String name;
        private io.grpc.MethodDescriptor.Builder descriptor;
        private MetricType metricType;
        private Class<?> requestType;
        private Class<?> responseType;
        private ArrayList<ClientInterceptor> interceptors = new ArrayList<>();
        private MarshallerSupplier defaultMarshallerSupplier = MarshallerSupplier.defaultInstance();
        private MarshallerSupplier marshallerSupplier;

        /**
         * Constructs a new Builder instance.
         *
         * @param serviceName The name of the service ths method belongs to
         * @param name the name of this method
         * @param descriptor The gRPC method descriptor builder
         */
        Builder(String serviceName, String name, MethodDescriptor.Builder descriptor) {
            this.name = name;
            this.descriptor = descriptor.setFullMethodName(serviceName + "/" + name);
        }

        @Override
        public Builder counted() {
            return metricType(MetricType.COUNTER);
        }

        @Override
        public Builder metered() {
            return metricType(MetricType.METERED);
        }

        @Override
        public Builder histogram() {
            return metricType(MetricType.HISTOGRAM);
        }

        @Override
        public Builder timed() {
            return metricType(MetricType.TIMER);
        }

        @Override
        public Builder disableMetrics() {
            return metricType(MetricType.INVALID);
        }

        private Builder metricType(MetricType metricType) {
            this.metricType = metricType;
            return this;
        }

        @Override
        public Builder requestType(Class type) {
            this.requestType = type;
            return this;
        }

        @Override
        public Builder responseType(Class type) {
            this.responseType = type;
            return this;
        }

        @Override
        public Builder intercept(ClientInterceptor... interceptors) {
            Collections.addAll(this.interceptors, interceptors);
            return this;
        }

        @Override
        public Builder marshallerSupplier(MarshallerSupplier supplier) {
            this.marshallerSupplier = supplier;
            return this;
        }

        Builder defaultMarshallerSupplier(MarshallerSupplier supplier) {
            if (supplier == null) {
                this.defaultMarshallerSupplier = MarshallerSupplier.defaultInstance();
            } else {
                this.defaultMarshallerSupplier = supplier;
            }
            return this;
        }

        /**
         * Sets the full name of this Method.
         *
         * @param fullName the full name of the method
         * @return this builder instance for fluent API
         */
        Builder fullName(String fullName) {
            descriptor.setFullMethodName(fullName);
            this.name = fullName.substring(fullName.lastIndexOf('/') + 1);
            return this;
        }

        /**
         * Builds and returns a new instance of {@link ClientMethodDescriptor}.
         *
         * @return a new instance of {@link ClientMethodDescriptor}
         */
        @Override
        @SuppressWarnings("unchecked")
        public ClientMethodDescriptor build() {
            MarshallerSupplier supplier = this.marshallerSupplier;

            if (supplier == null) {
                supplier = defaultMarshallerSupplier;
            }

            if (requestType != null) {
                descriptor.setRequestMarshaller(supplier.get(requestType));
            }

            if (responseType != null) {
                descriptor.setResponseMarshaller(supplier.get(responseType));
            }

            return new ClientMethodDescriptor(name,
                                              descriptor.build(),
                                              metricType,
                                              interceptors);
        }

    }
}
