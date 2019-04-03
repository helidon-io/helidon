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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.grpc.Context;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.eclipse.microprofile.metrics.MetricType;

import static io.helidon.grpc.core.GrpcHelper.extractNamePrefix;

/**
 * Encapsulates all metadata necessary to define a gRPC method.
 *
 * @param <ReqT> request type
 * @param <ResT> response type
 *
 * @author Aleksandar Seovic  2019.03.18
 */
public class MethodDescriptor<ReqT, ResT> {
    private final String name;
    private final io.grpc.MethodDescriptor<ReqT, ResT> descriptor;
    private final ServerCallHandler<ReqT, ResT> callHandler;
    private final MetricType metricType;
    private final Map<Context.Key, Object> context;
    private final List<ServerInterceptor> interceptors;

    private MethodDescriptor(String name,
                     io.grpc.MethodDescriptor<ReqT, ResT> descriptor,
                     ServerCallHandler<ReqT, ResT> callHandler,
                     MetricType metricType,
                     Map<Context.Key, Object> context,
                     List<ServerInterceptor> interceptors) {
        this.name = name;
        this.descriptor = descriptor;
        this.callHandler = callHandler;
        this.metricType = metricType;
        this.context = context;
        this.interceptors = new ArrayList<>(interceptors);
    }

    /**
     * Return the name of the method.
     * @return method name
     */
    public String name() {
        return name;
    }

    /**
     * Return gRPC method descriptor.
     * @return gRPC method descriptor
     */
    public io.grpc.MethodDescriptor<ReqT, ResT> descriptor() {
        return descriptor;
    }

    /**
     * Return the call handler.
     * @return call handler
     */
    public ServerCallHandler<ReqT, ResT> callHandler() {
        return callHandler;
    }

    /**
     * Return the type of metric that should be collected for this method.
     * @return metric type
     */
    public MetricType metricType() {
        return metricType;
    }

    /**
     * Obtain the {@link Map} of {@link Context.Key}s and values to add to the
     * call context when this method is invoked.
     *
     * @return  an unmodifiable {@link Map} of {@link Context.Key}s and values to
     *          add to the call context when this method is invoked
     */
    public Map<Context.Key, Object> context() {
        return Collections.unmodifiableMap(context);
    }

    /**
     * Obtain the {@link io.grpc.ServerInterceptor}s to use for this method.
     *
     * @return the {@link io.grpc.ServerInterceptor}s to use for this method
     */
    public List<ServerInterceptor> interceptors() {
        return Collections.unmodifiableList(interceptors);
    }

    static <ReqT, ResT> Builder<ReqT, ResT> builder(String name,
                                                    io.grpc.MethodDescriptor<ReqT, ResT> descriptor,
                                                    ServerCallHandler<ReqT, ResT> callHandler) {
        return new Builder<>(name, descriptor, callHandler);
    }

    static <ReqT, ResT> MethodDescriptor<ReqT, ResT> create(String name,
                                                            io.grpc.MethodDescriptor<ReqT, ResT> descriptor,
                                                            ServerCallHandler<ReqT, ResT> callHandler) {
        return builder(name, descriptor, callHandler).build();
    }

    /**
     * Method configuration API.
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
         * Add a {@link Context.Key} and value to be added to the call {@link io.grpc.Context}
         * when this method is invoked.
         *
         * @param key    the {@link Context.Key} to add
         * @param value  the value to map to the {@link Context.Key}
         * @param <T>    the type of the {@link Context.Key} and value
         *
         * @return this {@link Config} instance for fluent call chaining
         *
         * @throws java.lang.NullPointerException if the key parameter is null
         */

        <T> Config<ReqT, ResT>  addContextKey(Context.Key<T> key, T value);

        /**
         * Register one or more {@link io.grpc.ServerInterceptor interceptors} for the method.
         *
         * @param interceptors the interceptor(s) to register
         *
         * @return this {@link io.helidon.grpc.server.ServiceDescriptor.Config} instance for fluent call chaining
         */
        Config<ReqT, ResT> intercept(ServerInterceptor... interceptors);
    }

    /**
     * {@link MethodDescriptor} builder implementation.
     *
     * @param <ReqT> request type
     * @param <ResT> response type
     */
    static final class Builder<ReqT, ResT> implements Config<ReqT, ResT>, io.helidon.common.Builder<MethodDescriptor<ReqT, ResT>> {
        private final String name;
        private final io.grpc.MethodDescriptor.Builder<ReqT, ResT> descriptor;
        private final ServerCallHandler<ReqT, ResT> callHandler;

        private List<ServerInterceptor> interceptors = new ArrayList<>();

        private final Map<Context.Key, Object> context = new HashMap<>();

        private MetricType metricType;

        Builder(String name,
                io.grpc.MethodDescriptor<ReqT, ResT> descriptor,
                ServerCallHandler<ReqT, ResT> callHandler) {
            this.name = name;
            this.callHandler = callHandler;

            String fullName = descriptor.getFullMethodName();
            String prefix = extractNamePrefix(fullName);

            this.descriptor = descriptor.toBuilder()
                    .setFullMethodName(prefix + "/" + name);
        }

        @Override
        public Builder<ReqT, ResT> counted() {
            return metricType(MetricType.COUNTER);
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

        Builder<ReqT, ResT> fullname(String name) {
            descriptor.setFullMethodName(name);
            return this;
        }

        private Builder<ReqT, ResT> metricType(MetricType metricType) {
            this.metricType = metricType;
            return this;
        }

        @Override
        public <T> Builder<ReqT, ResT> addContextKey(Context.Key<T> key, T value) {
            context.put(Objects.requireNonNull(key, "The context key cannot be null"), value);
            return this;
        }

        @Override
        public Builder<ReqT, ResT> intercept(ServerInterceptor... interceptors) {
            Collections.addAll(this.interceptors, interceptors);
            return this;
        }

        @Override
        public MethodDescriptor<ReqT, ResT> build() {
            return new MethodDescriptor<>(name, descriptor.build(), callHandler, metricType,
                                          context, interceptors);
        }
    }
}
