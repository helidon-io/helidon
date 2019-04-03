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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.helidon.metrics.RegistryFactory;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Timer;

import static io.helidon.grpc.core.GrpcHelper.extractMethodName;

/**
 * A {@link io.grpc.ServerInterceptor} that enables capturing of gRPC call metrics.
 */
public class GrpcMetrics
        implements ServerInterceptor, ServiceDescriptor.Aware {

    /**
     * The registry of vendor metrics.
     */
    private static final MetricRegistry VENDOR_REGISTRY =
            RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.VENDOR);

    /**
     * The registry of application metrics.
     */
    private static final MetricRegistry APP_REGISTRY =
            RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION);

    /**
     * The type of metrics to be captured.
     */
    private MetricType type;

    /**
     * Service descriptor.
     */
    private ServiceDescriptor serviceDescriptor;

    /**
     * Create a {@link GrpcMetrics}.
     *
     * @param type  the type pf metrics to be captured
     */
    private GrpcMetrics(MetricType type) {
        this.type = type;
    }

    /**
     * A static factory method to create a {@link GrpcMetrics} instance
     * to count gRPC method calls.
     *
     * @return a {@link GrpcMetrics} instance to capture call counts
     */
    public static GrpcMetrics counted() {
        return new GrpcMetrics(MetricType.COUNTER);
    }

    /**
     * A static factory method to create a {@link GrpcMetrics} instance
     * to meter gRPC method calls.
     *
     * @return a {@link GrpcMetrics} instance to meter gRPC calls
     */
    public static GrpcMetrics metered() {
        return new GrpcMetrics(MetricType.METERED);
    }

    /**
     * A static factory method to create a {@link GrpcMetrics} instance
     * to create a histogram of gRPC method calls.
     *
     * @return a {@link GrpcMetrics} instance to create a histogram of gRPC method calls
     */
    public static GrpcMetrics histogram() {
        return new GrpcMetrics(MetricType.HISTOGRAM);
    }

    /**
     * A static factory method to create a {@link GrpcMetrics} instance
     * to time gRPC method calls.
     *
     * @return a {@link GrpcMetrics} instance to time gRPC method calls
     */
    public static GrpcMetrics timed() {
        return new GrpcMetrics(MetricType.TIMER);
    }

    @Override
    public void setServiceDescriptor(ServiceDescriptor descriptor) {
        this.serviceDescriptor = descriptor;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {

        String fullMethodName = call.getMethodDescriptor().getFullMethodName();
        String methodName = extractMethodName(fullMethodName);
        String metricName = fullMethodName.replace('/', '.');

        ServerCall<ReqT, RespT> serverCall;
        MetricType type = this.type;

        if (serviceDescriptor != null) {
            type = Optional.ofNullable(serviceDescriptor.metricType()).orElse(type);

            MethodDescriptor method = serviceDescriptor.method(methodName);
            if (method != null) {
                type = Optional.ofNullable(method.metricType()).orElse(type);
            }
        }

        switch (type) {
            case COUNTER:
                serverCall = new CountedServerCall<>(APP_REGISTRY.counter(metricName), call);
                break;
            case METERED:
                serverCall = new MeteredServerCall<>(APP_REGISTRY.meter(metricName), call);
                break;
            case HISTOGRAM:
                serverCall = new HistogramServerCall<>(APP_REGISTRY.histogram(metricName), call);
                break;
            case TIMER:
                serverCall = new TimedServerCall<>(APP_REGISTRY.timer(metricName), call);
                break;
            case GAUGE:
            case INVALID:
            default:
                serverCall = call;
        }

        serverCall = new MeteredServerCall<>(VENDOR_REGISTRY.meter("grpc.requests.meter"), serverCall);
        serverCall = new CountedServerCall<>(VENDOR_REGISTRY.counter("grpc.requests.count"), serverCall);

        return next.startCall(serverCall, headers);
    }

    /**
     * A {@link io.grpc.ServerCall} that captures metrics for a gRPC call.
     *
     * @param <ReqT>     the call request type
     * @param <RespT>    the call response type
     * @param <MetricT>  the type of metric to capture
     */
    private abstract class MetricServerCall<ReqT, RespT, MetricT>
            extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {
        /**
         * The metric to update.
         */
        private final MetricT metric;

        /**
         * Create a {@link TimedServerCall}.
         *
         * @param delegate the call to time
         */
        MetricServerCall(MetricT metric, ServerCall<ReqT, RespT> delegate) {
            super(delegate);

            this.metric = metric;
        }

        /**
         * Obtain the metric being tracked.
         *
         * @return  the metric being tracked
         */
        protected MetricT getMetric() {
            return metric;
        }
    }

    /**
     * A {@link GrpcMetrics.MeteredServerCall} that captures call times.
     *
     * @param <ReqT>   the call request type
     * @param <RespT>  the call response type
     */
    private class TimedServerCall<ReqT, RespT>
            extends MetricServerCall<ReqT, RespT, Timer> {
        /**
         * The method start time.
         */
        private final long startNanos;

        /**
         * Create a {@link TimedServerCall}.
         *
         * @param delegate the call to time
         */
        TimedServerCall(Timer timer, ServerCall<ReqT, RespT> delegate) {
            super(timer, delegate);

            this.startNanos = System.nanoTime();
        }

        @Override
        public void close(Status status, Metadata responseHeaders) {
            super.close(status, responseHeaders);

            long time = System.nanoTime() - startNanos;
            getMetric().update(time, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * A {@link GrpcMetrics.MeteredServerCall} that captures call counts.
     *
     * @param <ReqT>   the call request type
     * @param <RespT>  the call response type
     */
    private class CountedServerCall<ReqT, RespT>
            extends MetricServerCall<ReqT, RespT, Counter> {
        /**
         * Create a {@link CountedServerCall}.
         *
         * @param delegate the call to time
         */
        CountedServerCall(Counter counter, ServerCall<ReqT, RespT> delegate) {
            super(counter, delegate);
        }

        @Override
        public void close(Status status, Metadata responseHeaders) {
            super.close(status, responseHeaders);

            getMetric().inc();
        }
    }

    /**
     * A {@link GrpcMetrics.MeteredServerCall} that meters gRPC calls.
     *
     * @param <ReqT>   the call request type
     * @param <RespT>  the call response type
     */
    private class MeteredServerCall<ReqT, RespT>
            extends MetricServerCall<ReqT, RespT, Meter> {
        /**
         * Create a {@link MeteredServerCall}.
         *
         * @param delegate the call to time
         */
        MeteredServerCall(Meter meter, ServerCall<ReqT, RespT> delegate) {
            super(meter, delegate);
        }

        @Override
        public void close(Status status, Metadata responseHeaders) {
            super.close(status, responseHeaders);

            getMetric().mark();
        }
    }

    /**
     * A {@link GrpcMetrics.MeteredServerCall} that creates a histogram for gRPC calls.
     *
     * @param <ReqT>   the call request type
     * @param <RespT>  the call response type
     */
    private class HistogramServerCall<ReqT, RespT>
            extends MetricServerCall<ReqT, RespT, Histogram> {
        /**
         * Create a {@link HistogramServerCall}.
         *
         * @param delegate the call to time
         */
        HistogramServerCall(Histogram histogram, ServerCall<ReqT, RespT> delegate) {
            super(histogram, delegate);
        }

        @Override
        public void close(Status status, Metadata responseHeaders) {
            super.close(status, responseHeaders);

            getMetric().update(1);
        }
    }
}
