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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.grpc.core.MarshallerSupplier;

import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.MethodDescriptor;
import org.eclipse.microprofile.metrics.MetricType;

import static io.helidon.grpc.core.GrpcHelper.extractNamePrefix;

/**
 * Encapsulates all metadata necessary to define a gRPC method. In addition to wrapping a {@link io.grpc.MethodDescriptor},
 * this class also holds the request and response types of the gRPC method. A
 * {@link io.helidon.grpc.client.ClientServiceDescriptor} can contain zero or more {@link io.grpc.MethodDescriptor}.
 *
 * An instance of ClientMethodDescriptor can be created either from an existing {@link io.grpc.MethodDescriptor} or from
 * one of the factory methods like {@link io.helidon.grpc.client.ClientMethodDescriptor#unary(String, Class, Class)}, or
 * {@link io.helidon.grpc.client.ClientMethodDescriptor#clientStreaming(String, Class, Class)} etc.
 *
 * @param <ReqT> request type
 * @param <ResT> response type
 *
 * @author Mahesh Kannan
 */
public final class ClientMethodDescriptor<ReqT, ResT> {

    /**
     * The simple name of the method.
     */
    private String name;

    /**
     * The {@link io.grpc.MethodDescriptor} for this method. This is usually obtained from protocol buffer
     * method getDescriptor (from service getDescriptor).
     */
    private io.grpc.MethodDescriptor<ReqT, ResT> descriptor;

    /**
     * The metric type to be used for collecting method level metrics.
     */
    private MetricType metricType;

    /**
     * The context to be used for method invocation.
     */
    private Map<Context.Key, Object> context;

    /**
     * The type of the request parameter of this method.
     */
    private Class requestType;

    /**
     * The type of the return value of this method.
     */
    private Class responseType;

    /**
     * The list of client interceptors for this method.
     */
    private ArrayList<ClientInterceptor> interceptors;

    private ClientMethodDescriptor(String name,
                                  MethodDescriptor<ReqT, ResT> descriptor,
                                  MetricType metricType,
                                  Map<Context.Key, Object> context,
                                  Class requestType,
                                  Class responseType,
                                  ArrayList<ClientInterceptor> interceptors) {
        this.name = name;
        this.descriptor = descriptor;
        this.metricType = metricType;
        this.context = context;
        this.requestType = requestType;
        this.responseType = responseType;
        this.interceptors = interceptors;
    }

    /**
     * Creates a new {@link ClientMethodDescriptor.Builder} with the specified name and {@link io.grpc.MethodDescriptor}.
     *
     * @param simpleName   The simple method name.
     * @param descriptor The {@link io.grpc.MethodDescriptor} to initialize this builder.
     * @param <ReqT>     The request type.
     * @param <ResT>     The response type.
     * @return A new instance of a {@link io.helidon.grpc.client.ClientMethodDescriptor.Builder}
     */
    static <ReqT, ResT> Builder<ReqT, ResT> builder(String simpleName, io.grpc.MethodDescriptor<ReqT, ResT> descriptor) {
        String fullName = descriptor.getFullMethodName();
        int index = fullName.lastIndexOf('/');
        return new Builder<>(fullName.substring(0, index) + "/" + simpleName, descriptor);
    }

    /**
     * Creates a new {@link ClientMethodDescriptor.Builder} with the specified name and {@link io.grpc.MethodDescriptor}.
     *
     * @param <ReqT>     The request type.
     * @param <ResT>     The response type.
     * @return A new instance of a {@link io.helidon.grpc.client.ClientMethodDescriptor.Builder}
     */
    static <ReqT, ResT> Builder<ReqT, ResT> builder(io.grpc.MethodDescriptor<ReqT, ResT> descriptor) {
        return new Builder<>(descriptor);
    }

    /**
     * Creates a new UNARY {@link ClientMethodDescriptor} with the specified name and {@link io.grpc.MethodDescriptor}.
     *
     * @param fullName The full method name.
     * @param <ReqT>   The request type.
     * @param <ResT>   The response type.
     * @return A new instance of a {@link io.helidon.grpc.client.ClientMethodDescriptor.Builder}
     */
    static <ReqT, ResT> ClientMethodDescriptor<ReqT, ResT> unary(String fullName,
                                                                 Class<ReqT> requestType,
                                                                 Class<ResT> responseType) {
        return createMethodDescriptor(fullName, MethodDescriptor.MethodType.UNARY, requestType, responseType);
    }

    /**
     * Creates a new Client Streaming {@link ClientMethodDescriptor} with the specified name and {@link io.grpc.MethodDescriptor}.
     *
     * @param fullName The full method name.
     * @param <ReqT>   The request type.
     * @param <ResT>   The response type.
     * @return A new instance of a {@link io.helidon.grpc.client.ClientMethodDescriptor.Builder}
     */
    static <ReqT, ResT> ClientMethodDescriptor<ReqT, ResT> clientStreaming(String fullName,
                                                                 Class<ReqT> requestType,
                                                                 Class<ResT> responseType) {
        return createMethodDescriptor(fullName, MethodDescriptor.MethodType.CLIENT_STREAMING, requestType, responseType);
    }

    /**
     * Creates a new Server Streaming {@link ClientMethodDescriptor} with the specified name and {@link io.grpc.MethodDescriptor}.
     *
     * @param fullName The full method name.
     * @param <ReqT>   The request type.
     * @param <ResT>   The response type.
     * @return A new instance of a {@link io.helidon.grpc.client.ClientMethodDescriptor.Builder}
     */
    static <ReqT, ResT> ClientMethodDescriptor<ReqT, ResT> serverStreaming(String fullName,
                                                                           Class<ReqT> requestType,
                                                                           Class<ResT> responseType) {
        return createMethodDescriptor(fullName, MethodDescriptor.MethodType.SERVER_STREAMING, requestType, responseType);
    }

    /**
     * Creates a new BiDi {@link ClientMethodDescriptor} with the specified name and {@link io.grpc.MethodDescriptor}.
     *
     * @param fullName The full method name.
     * @param <ReqT>   The request type.
     * @param <ResT>   The response type.
     * @return A new instance of a {@link io.helidon.grpc.client.ClientMethodDescriptor.Builder}
     */
    static <ReqT, ResT> ClientMethodDescriptor<ReqT, ResT> bidirectional(String fullName,
                                                                           Class<ReqT> requestType,
                                                                           Class<ResT> responseType) {
        return createMethodDescriptor(fullName, MethodDescriptor.MethodType.BIDI_STREAMING, requestType, responseType);
    }

    // private helper method
    private static <ReqT, ResT> ClientMethodDescriptor<ReqT, ResT> createMethodDescriptor(String fullName,
                                                                                          MethodDescriptor.MethodType methodType,
                                                                                          Class<ReqT> requestType,
                                                                                          Class<ResT> responseType) {
        MethodDescriptor<ReqT, ResT> cmd = MethodDescriptor.<ReqT, ResT>newBuilder()
                .setType(methodType)
                .setFullMethodName(fullName)
                .setRequestMarshaller(MarshallerSupplier.defaultInstance().get(requestType))  // otherwise NPE
                .setResponseMarshaller(MarshallerSupplier.defaultInstance().get(responseType))  // otherwise NPE
                .build();

        String prefix = extractNamePrefix(fullName);
        String simpleName = fullName.substring(prefix.length() + 1);
        return ClientMethodDescriptor.builder(simpleName, cmd)
                .requestType(requestType)
                .responseType(responseType)
                .build();
    }

    /**
     * Creates a new {@link ClientMethodDescriptor} with the specified name and {@link io.grpc.MethodDescriptor}.
     *
     * @param name       The method name.
     * @param descriptor The {@link io.grpc.MethodDescriptor}.
     * @param <ReqT>     The request type.
     * @param <ResT>     The response type.
     * @return A new instance of a {@link io.helidon.grpc.client.ClientMethodDescriptor.Builder}
     */
    static <ReqT, ResT> ClientMethodDescriptor<ReqT, ResT> create(String name, io.grpc.MethodDescriptor<ReqT, ResT> descriptor) {
        return builder(name, descriptor).build();
    }

    ClientMethodDescriptor.Builder toBuilder() {
        return builder(this.name, this.descriptor)
                .requestType(this.requestType)
                .responseType(this.responseType)
                .metricType(this.metricType)
                .intercept(this.interceptors)
                .context(this.context);
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
     * @return The {@link io.grpc.MethodDescriptor} of this method.
     */
    public MethodDescriptor<ReqT, ResT> descriptor() {
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
     * Returns the context.
     *
     * @return The context object.
     */
    public Map<Context.Key, Object> context() {
        return context;
    }

    /**
     * Returns the type of parameter of this method.
     *
     * @return the type of parameter of this method.
     */
    public Class requestType() {
        return requestType;
    }

    /**
     * Returns the type of return value of this method.
     *
     * @return the type of return value of this method.
     */
    public Class responseType() {
        return responseType;
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
     * Checks if this object is identical to the specified {@link ClientMethodDescriptor}. The two instances are considered
     * identical if both have identical requestType, responseType, context, interceptors and descriptor.
     * @param o The other {@link ClientMethodDescriptor}.
     * @return true if they are identical, false otherwise.
     */
    public boolean isIdentical(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClientMethodDescriptor<?, ?> that = (ClientMethodDescriptor<?, ?>) o;
        return Objects.equals(name, that.name)
                && metricType == that.metricType
                && Objects.equals(requestType, that.requestType)
                && Objects.equals(responseType, that.responseType)
                && Objects.equals(context, that.context)
                && Objects.equals(interceptors, that.interceptors)
                && isIdentical(descriptor, that.descriptor);
    }

    private boolean isIdentical(MethodDescriptor d1, MethodDescriptor d2) {
        return d1.getRequestMarshaller() == d2.getRequestMarshaller()
                && d1.getResponseMarshaller() == d2.getResponseMarshaller()
                && d1.getType() == d2.getType()
                && d1.getFullMethodName().equals(d2.getFullMethodName())
                && d1.isIdempotent() == d2.isIdempotent()
                && d1.isSafe() == d2.isSafe()
                && d1.isSampledToLocalTracing() == d2.isSampledToLocalTracing()
                && d1.getSchemaDescriptor() == d2.getSchemaDescriptor();
    }

    /**
     * ClientMethod configuration API.
     *
     * @param <ReqT> request type
     * @param <ResT> response type
     */
    public interface Config<ReqT, ResT> {

        /**
         * Collect metrics for this method using {@link org.eclipse.microprofile.metrics.Counter}.
         *
         * @return this {@link Config} instance for fluent call chaining
         */

        Config<ReqT, ResT> counted();

        /**
         * Collect metrics for this method using {@link org.eclipse.microprofile.metrics.Gauge}.
         *
         * @return this {@link Config} instance for fluent call chaining
         */
        Config<ReqT, ResT> gauged();

        /**
         * Collect metrics for this method using {@link org.eclipse.microprofile.metrics.Meter}.
         *
         * @return this {@link Config} instance for fluent call chaining
         */
        Config<ReqT, ResT> metered();

        /**
         * Collect metrics for this method using {@link org.eclipse.microprofile.metrics.Histogram}.
         *
         * @return this {@link Config} instance for fluent call chaining
         */
        Config<ReqT, ResT> histogram();

        /**
         * Collect metrics for this method using {@link org.eclipse.microprofile.metrics.Timer}.
         *
         * @return this {@link Config} instance for fluent call chaining
         */
        Config<ReqT, ResT> timed();

        /**
         * Explicitly disable metrics collection for this service.
         *
         * @return this {@link Config} instance for fluent call chaining
         */
        Config<ReqT, ResT> disableMetrics();

        /**
         * Add a {@link Context.Key} and value to be added to the call {@link Context}
         * when this method is invoked.
         *
         * @param key   the {@link Context.Key} to add
         * @param value the value to map to the {@link Context.Key}
         * @param <T>   the type of the {@link Context.Key} and value
         * @return this {@link Config} instance for fluent call chaining
         * @throws NullPointerException if the key parameter is null
         */
        <T> Config<ReqT, ResT> addContextKey(Context.Key<T> key, T value);

        /**
         * Sets the type of parameter of this method.
         *
         * @param type The type of parameter of this method.
         * @return this {@link Config} instance for fluent call chaining
         */
        Config<ReqT, ResT> requestType(Class type);

        /**
         * Sets the type of parameter of this method.
         *
         * @param type The type of parameter of this method.
         * @return this {@link Config} instance for fluent call chaining
         */
        Config<ReqT, ResT> responseType(Class type);

        /**
         * Register one or more {@link ClientInterceptor interceptors} for the method.
         *
         * @param interceptors the interceptor(s) to register
         * @return this {@link io.helidon.grpc.client.ClientMethodDescriptor.Config} instance for fluent call chaining
         */
        Config<ReqT, ResT> intercept(ClientInterceptor... interceptors);
    }

    /**
     * {@link MethodDescriptor} builder implementation.
     *
     * @param <ReqT> request type
     * @param <ResT> response type
     */
    public static class Builder<ReqT, ResT>
            implements Config<ReqT, ResT> {

        private String name;
        private io.grpc.MethodDescriptor<ReqT, ResT> descriptor;
        private MetricType metricType;
        private Map<Context.Key, Object> context = new HashMap<>();
        private Class requestType;
        private Class responseType;
        private ArrayList<ClientInterceptor> interceptors = new ArrayList<>();
        private io.grpc.MethodDescriptor.Builder<ReqT, ResT> descBuilder;

        /**
         * Constructs a new Builder instance.
         *
         * @param descriptor The gRPC method descriptor.
         */
        Builder(MethodDescriptor<ReqT, ResT> descriptor) {
            this(descriptor.getFullMethodName(), descriptor);
        }

        /**
         * Constructs a new Builder instance.
         *
         * @param fullName The full method name.
         * @param descriptor The gRPC method descriptor.
         */
        Builder(String fullName, MethodDescriptor<ReqT, ResT> descriptor) {
            this.descBuilder = descriptor.toBuilder();
            fullName(fullName);
        }

        @Override
        public Builder<ReqT, ResT> counted() {
            return metricType(MetricType.COUNTER);
        }

        @Override
        public Builder<ReqT, ResT> gauged() {
            return metricType(MetricType.GAUGE);
        }

        @Override
        public Builder<ReqT, ResT> metered() {
            return metricType(MetricType.METERED);
        }

        @Override
        public Builder<ReqT, ResT> histogram() {
            return metricType(MetricType.HISTOGRAM);
        }

        @Override
        public Builder<ReqT, ResT> timed() {
            return metricType(MetricType.TIMER);
        }

        @Override
        public Builder<ReqT, ResT> disableMetrics() {
            return metricType(MetricType.INVALID);
        }

        /**
         * Sets the simple name of this Method.
         *
         * @param name the simple name of the method.
         * @return This builder instance for fluent API.
         */
        public Builder<ReqT, ResT> name(String name) {
            String prefix = extractNamePrefix(descBuilder.build().getFullMethodName());
            return fullName(prefix + "/" + name);
        }

        /**
         * Sets the full name of this Method.
         *
         * @param fullName the simple name of the method.
         * @return This builder instance for fluent API.
         */
        public Builder<ReqT, ResT> fullName(String fullName) {
            descBuilder.setFullMethodName(fullName);
            this.name = fullName.substring(fullName.lastIndexOf('/') + 1);
            return this;
        }

        private Builder<ReqT, ResT> metricType(MetricType metricType) {
            this.metricType = metricType;
            return this;
        }

        @Override
        public <T> Builder<ReqT, ResT> addContextKey(Context.Key<T> key, T value) {
            this.context.put(Objects.requireNonNull(key, "The context key cannot be null"), value);
            return this;
        }

        @Override
        public Builder<ReqT, ResT> requestType(Class type) {
            this.requestType = type;
            return this;
        }

        @Override
        public Builder<ReqT, ResT> responseType(Class type) {
            this.responseType = type;
            return this;
        }

        @Override
        public Builder<ReqT, ResT> intercept(ClientInterceptor... interceptors) {
            Collections.addAll(this.interceptors, interceptors);
            return this;
        }

        // Internal method
        private Builder<ReqT, ResT> intercept(ArrayList<ClientInterceptor> interceptors) {
            this.interceptors.addAll(interceptors);
            return this;
        }

        // Internal method
        private Builder<ReqT, ResT> context(Map<Context.Key, Object> ctx) {
            this.context.putAll(ctx);
            return this;
        }

        /**
         * Builds and returns a new instance of {@link ClientMethodDescriptor}.
         *
         * @return A new instance of {@link ClientMethodDescriptor}.
         */
        @SuppressWarnings("unchecked")
        public ClientMethodDescriptor<ReqT, ResT> build() {
            if (this.requestType != null) {
                descBuilder.setRequestMarshaller(MarshallerSupplier.defaultInstance().get(this.requestType));
            }
            if (this.responseType != null) {
                descBuilder.setResponseMarshaller(MarshallerSupplier.defaultInstance().get(this.responseType));
            }

            this.descriptor = descBuilder.build();
            return new ClientMethodDescriptor<>(name,
                                                descriptor,
                                                metricType,
                                                context,
                                                requestType,
                                                responseType,
                                                interceptors);
        }

    }
}
