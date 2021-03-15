/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.grpc.metrics;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Priority;

import io.helidon.grpc.core.GrpcHelper;
import io.helidon.grpc.core.InterceptorPriorities;
import io.helidon.grpc.server.MethodDescriptor;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.metrics.RegistryFactory;

import io.grpc.Context;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

/**
 * A {@link io.grpc.ServerInterceptor} that enables capturing of gRPC call metrics.
 */
@Priority(InterceptorPriorities.TRACING + 1)
public class GrpcMetrics
        implements ServerInterceptor, ServiceDescriptor.Configurer, MethodDescriptor.Configurer {

    /**
     * The registry of vendor metrics.
     */
    static final MetricRegistry VENDOR_REGISTRY =
            RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.VENDOR);

    /**
     * The registry of application metrics.
     */
    static final MetricRegistry APP_REGISTRY =
            RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION);

    static final org.eclipse.microprofile.metrics.Metadata GRPC_METER = org.eclipse.microprofile.metrics.Metadata
            .builder()
            .withName("grpc.requests.meter")
            .withDisplayName("Meter for overall gRPC requests")
            .withDescription("Each gRPC request will mark the meter to see overall throughput")
            .withType(MetricType.METERED)
            .withUnit(MetricUnits.NONE)
            .build();

    /**
     * The context key name to use to obtain rules to use when applying metrics.
     */
    private static final String KEY_STRING = GrpcMetrics.class.getName();

    /**
     * The context key to use to obtain rules to use when applying metrics.
     */
    private static final Context.Key<MetricsRules> KEY = Context.keyWithDefault(KEY_STRING, new MetricsRules(MetricType.INVALID));

    /**
     * The metric rules to use.
     */
    private final MetricsRules metricRule;

    /**
     * Create a {@link GrpcMetrics}.
     *
     * @param rules  the metric rules to use
     */
    private GrpcMetrics(MetricsRules rules) {
        this.metricRule = rules;
    }

    @Override
    public void configure(MethodDescriptor.Rules rules) {
        rules.addContextValue(KEY, metricRule);
    }

    @Override
    public void configure(ServiceDescriptor.Rules rules) {
        rules.addContextValue(KEY, metricRule);
    }

    /**
     * Set the tags to apply to the metric.
     *
     * @param tags the tags to apply to the metric
     * @return a {@link io.helidon.grpc.metrics.GrpcMetrics} interceptor
     * @see org.eclipse.microprofile.metrics.Metadata
     */
    public GrpcMetrics tags(Map<String, String> tags) {
        return new GrpcMetrics(metricRule.tags(tags));
    }

    /**
     * Set the description to apply to the metric.
     *
     * @param description the description to apply to the metric
     * @return a {@link io.helidon.grpc.metrics.GrpcMetrics} interceptor
     * @see org.eclipse.microprofile.metrics.Metadata
     */
    public GrpcMetrics description(String description) {
        return new GrpcMetrics(metricRule.description(description));
    }

    /**
     * Set the display name to apply to the metric.
     *
     * @param displayName the display name to apply to the metric
     * @return a {@link io.helidon.grpc.metrics.GrpcMetrics} interceptor
     * @see org.eclipse.microprofile.metrics.Metadata
     */
    public GrpcMetrics displayName(String displayName) {
        return new GrpcMetrics(metricRule.displayName(displayName));
    }

    /**
     * Set the units to apply to the metric.
     *
     * @param units the units to apply to the metric
     * @return a {@link io.helidon.grpc.metrics.GrpcMetrics} interceptor
     * @see org.eclipse.microprofile.metrics.Metadata
     */
    public GrpcMetrics units(String units) {
        return new GrpcMetrics(metricRule.units(units));
    }

    /**
     * Set the reusability of the metric.
     * @param reusable {@code true} if this metric may be reused
     * @return a {@link io.helidon.grpc.metrics.GrpcMetrics} interceptor
     * @see org.eclipse.microprofile.metrics.Metadata
     */
    public GrpcMetrics reusable(boolean reusable) {
        return new GrpcMetrics(metricRule.reusable(reusable));
    }

    /**
     * Obtain the {@link org.eclipse.microprofile.metrics.MetricType}.
     *
     * @return the {@link org.eclipse.microprofile.metrics.MetricType}
     */
    public MetricType metricType() {
        return metricRule.type();
    }

    /**
     * Set the {@link NamingFunction} to use to generate the metric name.
     * <p>
     * The default name will be the {@code <service-name>.<method-name>}.
     *
     * @param function the function to use to create the metric name
     * @return a {@link io.helidon.grpc.metrics.GrpcMetrics} interceptor
     */
    public GrpcMetrics nameFunction(NamingFunction function) {
        return new GrpcMetrics(metricRule.nameFunction(function));
    }

    /**
     * A static factory method to create a {@link GrpcMetrics} instance
     * to count gRPC method calls.
     *
     * @return a {@link GrpcMetrics} instance to capture call counts
     */
    public static GrpcMetrics counted() {
        return new GrpcMetrics(new MetricsRules(MetricType.COUNTER));
    }

    /**
     * A static factory method to create a {@link GrpcMetrics} instance
     * to meter gRPC method calls.
     *
     * @return a {@link GrpcMetrics} instance to meter gRPC calls
     */
    public static GrpcMetrics metered() {
        return new GrpcMetrics(new MetricsRules(MetricType.METERED));
    }

    /**
     * A static factory method to create a {@link GrpcMetrics} instance
     * to create a histogram of gRPC method calls.
     *
     * @return a {@link GrpcMetrics} instance to create a histogram of gRPC method calls
     */
    public static GrpcMetrics histogram() {
        return new GrpcMetrics(new MetricsRules(MetricType.HISTOGRAM));
    }

    /**
     * A static factory method to create a {@link GrpcMetrics} instance
     * to time gRPC method calls.
     *
     * @return a {@link GrpcMetrics} instance to time gRPC method calls
     */
    public static GrpcMetrics timed() {
        return new GrpcMetrics(new MetricsRules(MetricType.TIMER));
    }

    /**
     * A static factory method to create a {@link GrpcMetrics} instance
     * to time gRPC method calls.
     *
     * @return a {@link GrpcMetrics} instance to time gRPC method calls
     */
    public static GrpcMetrics concurrentGauge() {
        return new GrpcMetrics(new MetricsRules(MetricType.CONCURRENT_GAUGE));
    }

    /**
     * A static factory method to create a {@link GrpcMetrics} instance
     * to time gRPC method calls.
     *
     * @return a {@link GrpcMetrics} instance to time gRPC method calls
     */
    public static GrpcMetrics simplyTimed() {
        return new GrpcMetrics(new MetricsRules(MetricType.SIMPLE_TIMER));
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {

        MetricsRules rules = Context.keyWithDefault(KEY_STRING, metricRule).get();
        MetricType type = rules.type();

        String fullMethodName = call.getMethodDescriptor().getFullMethodName();
        String methodName = GrpcHelper.extractMethodName(fullMethodName);
        ServiceDescriptor service = ServiceDescriptor.SERVICE_DESCRIPTOR_KEY.get();
        ServerCall<ReqT, RespT> serverCall;

        switch (type) {
            case COUNTER:
                serverCall = new CountedServerCall<>(APP_REGISTRY.counter(
                        rules.metadata(service, methodName), rules.toTags()), call);
                break;
            case METERED:
                serverCall = new MeteredServerCall<>(APP_REGISTRY.meter(
                        rules.metadata(service, methodName), rules.toTags()), call);
                break;
            case HISTOGRAM:
                serverCall = new HistogramServerCall<>(APP_REGISTRY.histogram(
                        rules.metadata(service, methodName), rules.toTags()), call);
                break;
            case TIMER:
                serverCall = new TimedServerCall<>(APP_REGISTRY.timer(
                        rules.metadata(service, methodName), rules.toTags()), call);
                break;
            case SIMPLE_TIMER:
                serverCall = new SimplyTimedServerCall<>(APP_REGISTRY.simpleTimer(
                        rules.metadata(service, methodName), rules.toTags()), call);
                break;
            case CONCURRENT_GAUGE:
                serverCall = new ConcurrentGaugeServerCall<>(APP_REGISTRY.concurrentGauge(
                        rules.metadata(service, methodName), rules.toTags()), call);
                break;
            case GAUGE:
            case INVALID:
            default:
                serverCall = call;
        }

        serverCall = new MeteredServerCall<>(VENDOR_REGISTRY.meter(GRPC_METER), serverCall);

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
        private MetricServerCall(MetricT metric, ServerCall<ReqT, RespT> delegate) {
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
        private TimedServerCall(Timer timer, ServerCall<ReqT, RespT> delegate) {
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
     * A {@link GrpcMetrics.MeteredServerCall} that captures call times.
     *
     * @param <ReqT>   the call request type
     * @param <RespT>  the call response type
     */
    private class SimplyTimedServerCall<ReqT, RespT>
            extends MetricServerCall<ReqT, RespT, SimpleTimer> {
        /**
         * The method start time.
         */
        private final long startNanos;

        /**
         * Create a {@link SimplyTimedServerCall}.
         *
         * @param delegate the call to time
         */
        private SimplyTimedServerCall(SimpleTimer simpleTimer, ServerCall<ReqT, RespT> delegate) {
            super(simpleTimer, delegate);

            this.startNanos = System.nanoTime();
        }

        @Override
        public void close(Status status, Metadata responseHeaders) {
            super.close(status, responseHeaders);

            long time = System.nanoTime() - startNanos;
            getMetric().update(Duration.ofNanos(time));
        }
    }

    /**
     * A {@link GrpcMetrics.MeteredServerCall} that captures call times.
     *
     * @param <ReqT>   the call request type
     * @param <RespT>  the call response type
     */
    private class ConcurrentGaugeServerCall<ReqT, RespT>
            extends MetricServerCall<ReqT, RespT, ConcurrentGauge> {

        /**
         * Create a {@link SimplyTimedServerCall}.
         *
         * @param delegate the call to time
         */
        private ConcurrentGaugeServerCall(ConcurrentGauge concurrentGauge, ServerCall<ReqT, RespT> delegate) {
            super(concurrentGauge, delegate);
        }

        @Override
        public void close(Status status, Metadata responseHeaders) {
            super.close(status, responseHeaders);

            getMetric().inc();
        }
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
        private CountedServerCall(Counter counter, ServerCall<ReqT, RespT> delegate) {
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
        private MeteredServerCall(Meter meter, ServerCall<ReqT, RespT> delegate) {
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
        private HistogramServerCall(Histogram histogram, ServerCall<ReqT, RespT> delegate) {
            super(histogram, delegate);
        }

        @Override
        public void close(Status status, Metadata responseHeaders) {
            super.close(status, responseHeaders);

            getMetric().update(1);
        }
    }


    /**
     * Implemented by classes that can create a metric name.
     */
    @FunctionalInterface
    public interface NamingFunction {
        /**
         * Create a metric name.
         *
         * @param service    the service descriptor
         * @param methodName the method name
         * @param metricType the metric type
         * @return the metric name
         */
        String createName(ServiceDescriptor service, String methodName, MetricType metricType);
    }


    /**
     * An immutable holder of metrics information.
     * <p>
     * Calls made to mutating methods return a new instance
     * of {@link MetricsRules} with the mutation applied.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static class MetricsRules {

        private static final Tag[] EMPTY_TAGS = new Tag[0];

        /**
         * The metric type.
         */
        private MetricType type;

        /**
         * The tags of the metric.
         *
         * @see org.eclipse.microprofile.metrics.Metadata
         */
        private Optional<HashMap<String, String>> tags = Optional.empty();

        /**
         * The description of the metric.
         *
         * @see org.eclipse.microprofile.metrics.Metadata
         */
        private Optional<String> description = Optional.empty();

        /**
         * The display name of the metric.
         *
         * @see org.eclipse.microprofile.metrics.Metadata
         */
        private String displayName;

        /**
         * The unit of the metric.
         *
         * @see org.eclipse.microprofile.metrics.Metadata
         * @see org.eclipse.microprofile.metrics.MetricUnits
         */
        private Optional<String> units = Optional.empty();

        /**
         * The reusability status of this metric.
         * @see org.eclipse.microprofile.metrics.Metadata
         */
        private boolean reusable;

        /**
         * The function to use to obtain the metric name.
         */
        private Optional<NamingFunction> nameFunction = Optional.empty();

        private MetricsRules(MetricType type) {
            this.type = type;
        }

        private MetricsRules(MetricsRules copy) {
            this.type = copy.type;
            this.tags = copy.tags;
            this.description = copy.description;
            this.displayName = copy.displayName;
            this.units = copy.units;
            this.nameFunction = copy.nameFunction;
            this.reusable = copy.reusable;
        }

        /**
         * Obtain the metric type.
         *
         * @return the metric type
         */
        MetricType type() {
            return type;
        }

        /**
         * Obtain the metrics metadata.
         *
         * @param service the service descriptor
         * @param method the method name
         * @return  the metrics metadata
         */
        org.eclipse.microprofile.metrics.Metadata metadata(ServiceDescriptor service, String method) {
            String name = nameFunction.orElse(this::defaultName).createName(service, method, type);
            MetadataBuilder builder = org.eclipse.microprofile.metrics.Metadata.builder()
                    .withName(name)
                    .withType(type)
                    .reusable(this.reusable);

            this.description.ifPresent(builder::withDescription);
            this.units.ifPresent(builder::withUnit);

            String displayName = this.displayName;
            builder.withDisplayName(displayName == null ? name : displayName);

            return builder.build();
        }

        private String defaultName(ServiceDescriptor service, String methodName, MetricType metricType) {
            return (service.name() + "." + methodName).replaceAll("/", ".");
        }

        private MetricsRules tags(Map<String, String> tags) {
            MetricsRules rules = new MetricsRules(this);
            rules.tags = Optional.of(new HashMap<>(tags));
            return rules;
        }

        private MetricsRules description(String description) {
            MetricsRules rules = new MetricsRules(this);
            rules.description = Optional.of(description);
            return rules;
        }

        private MetricsRules displayName(String displayName) {
            MetricsRules rules = new MetricsRules(this);
            rules.displayName = displayName;
            return rules;
        }

        private MetricsRules nameFunction(NamingFunction function) {
            MetricsRules rules = new MetricsRules(this);
            rules.nameFunction = Optional.of(function);
            return rules;
        }

        private MetricsRules units(String units) {
            MetricsRules rules = new MetricsRules(this);
            rules.units = Optional.of(units);
            return rules;
        }

        private MetricsRules reusable(boolean reusable) {
            MetricsRules rules = new MetricsRules(this);
            rules.reusable = reusable;
            return rules;
        }

        private Tag[] toTags() {
            return tags.isPresent()
                    ? tags.get().entrySet().stream()
                        .map(entry -> new Tag(entry.getKey(), entry.getValue()))
                        .toArray(Tag[]::new)
                    : EMPTY_TAGS;
        }
    }
}
