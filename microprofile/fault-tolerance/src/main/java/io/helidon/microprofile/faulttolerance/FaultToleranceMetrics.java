/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.faulttolerance;

import java.util.Objects;
import java.util.function.Supplier;

import javax.enterprise.inject.spi.CDI;
import javax.enterprise.util.AnnotationLiteral;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

/**
 * Utility class to register and fetch FT metrics.
 */
class FaultToleranceMetrics {

    private static MetricRegistry metricRegistry;

    private FaultToleranceMetrics() {
    }

    static boolean enabled() {
        return getMetricRegistry() != null;
    }

    static synchronized MetricRegistry getMetricRegistry() {
        if (metricRegistry == null) {
            metricRegistry = CDI.current().select(MetricRegistry.class, new BaseRegistryTypeLiteral()).get();
        }
        return metricRegistry;
    }

    /**
     * Annotation literal to inject base registry.
     */
    static class BaseRegistryTypeLiteral extends AnnotationLiteral<RegistryType> implements RegistryType {

        @Override
        public MetricRegistry.Type type() {
            return MetricRegistry.Type.BASE;
        }
    }

    /**
     * Base class for Fault Tolerance metrics. Shares common logic for registration
     * and lookup of metrics.
     */
    abstract static class FaultToleranceMetric {

        abstract String name();

        abstract String description();

        abstract MetricType metricType();

        abstract String unit();

        protected Counter getCounter(Tag... tags) {
            MetricID metricID = new MetricID(name(), tags);
            return (Counter) getMetricRegistry().getMetrics().get(metricID);
        }

        protected Counter registerCounter(Tag... tags) {
            Counter counter = getCounter(tags);
            if (counter == null) {
                Metadata metadata = Metadata.builder()
                        .withName(name())
                        .withDisplayName(name())
                        .withDescription(description())
                        .withType(metricType())
                        .withUnit(unit())
                        .reusable(true)
                        .build();
                try {
                    counter = getMetricRegistry().counter(metadata, tags);
                } catch (IllegalArgumentException e) {
                    // Looks like we lost registration race
                    counter = getCounter(tags);
                    Objects.requireNonNull(counter);
                }
            }
            return counter;
        }

        protected Histogram getHistogram(Tag... tags) {
            MetricID metricID = new MetricID(name(), tags);
            return (Histogram) getMetricRegistry().getMetrics().get(metricID);
        }

        protected Histogram registerHistogram(Tag... tags) {
            Histogram histogram = getHistogram(tags);
            if (histogram == null) {
                Metadata metadata = Metadata.builder()
                        .withName(name())
                        .withDisplayName(name())
                        .withDescription(description())
                        .withType(metricType())
                        .withUnit(unit())
                        .reusable(true)
                        .build();
                try {
                    histogram = getMetricRegistry().histogram(metadata, tags);
                } catch (IllegalArgumentException e) {
                    // Looks like we lost the registration race
                    histogram = getHistogram(tags);
                    Objects.requireNonNull(histogram);
                }
            }
            return histogram;
        }

        @SuppressWarnings("unchecked")
        protected <T> Gauge<T> getGauge(Tag... tags) {
            MetricID metricID = new MetricID(name(), tags);
            return (Gauge<T>) getMetricRegistry().getMetrics().get(metricID);
        }

        @SuppressWarnings("unchecked")
        protected <T> Gauge<T> registerGauge(Gauge<T> newGauge, Tag... tags) {
            Gauge<T> gauge = getGauge(tags);
            if (gauge == null) {
                Metadata metadata = Metadata.builder()
                        .withName(name())
                        .withDisplayName(name())
                        .withDescription(description())
                        .withType(metricType())
                        .withUnit(unit())
                        .reusable(true)
                        .build();
                try {
                    gauge = getMetricRegistry().register(metadata, newGauge, tags);
                } catch (IllegalArgumentException e) {
                    // Looks like we lost the registration race
                    gauge = getGauge(tags);
                    Objects.requireNonNull(gauge);
                }
            }
            return gauge;
        }
    }

    // -- Invocations ---------------------------------------------------------

    enum InvocationResult implements Supplier<Tag> {
        VALUE_RETURNED("valueReturned"),
        EXCEPTION_THROWN("exceptionThrown");

        private final Tag metricTag;

        InvocationResult(String value) {
            metricTag = new Tag("result", value);
        }

        @Override
        public Tag get() {
            return metricTag;
        }
    }

    enum InvocationFallback implements Supplier<Tag> {
        APPLIED("applied"),
        NOT_APPLIED("notApplied"),
        NOT_DEFINED("notDefined");

        private final Tag metricTag;

        InvocationFallback(String value) {
            metricTag = new Tag("fallback", value);
        }

        @Override
        public Tag get() {
            return metricTag;
        }
    }

    /**
     * Class for "ft.invocations.total" counters.
     */
    static class InvocationsTotal extends FaultToleranceMetric {

        static final InvocationsTotal INSTANCE = new InvocationsTotal();

        private InvocationsTotal() {
        }

        @Override
        String name() {
            return "ft.invocations.total";
        }

        @Override
        String description() {
            return "The number of times the method was called";
        }

        @Override
        MetricType metricType() {
            return MetricType.COUNTER;
        }

        @Override
        String unit() {
            return MetricUnits.NONE;
        }

        static Counter get(Tag... tags) {
            return INSTANCE.registerCounter(tags);
        }

        static Counter register(Tag... tags) {
            return INSTANCE.registerCounter(tags);
        }
    }

    // -- Retries -------------------------------------------------------------

    enum RetryResult implements Supplier<Tag> {
        VALUE_RETURNED("valueReturned"),
        EXCEPTION_NOT_RETRYABLE("exceptionNotRetryable"),
        MAX_RETRIES_REACHED("maxRetriesReached"),
        MAX_DURATION_REACHED("maxDurationReached");

        private final Tag metricTag;

        RetryResult(String value) {
            metricTag = new Tag("retryResult", value);
        }

        @Override
        public Tag get() {
            return metricTag;
        }
    }

    enum RetryRetried implements Supplier<Tag> {
        TRUE("true"),
        FALSE("false");

        private final Tag metricTag;

        RetryRetried(String value) {
            metricTag = new Tag("retried", value);
        }

        @Override
        public Tag get() {
            return metricTag;
        }
    }

    /**
     * Class for "ft.retry.calls.total" counters.
     */
    static class RetryCallsTotal extends FaultToleranceMetric {

        static final RetryCallsTotal INSTANCE = new RetryCallsTotal();

        private RetryCallsTotal() {
        }

        @Override
        String name() {
            return "ft.retry.calls.total";
        }

        @Override
        String description() {
            return "The number of times the retry logic was run. This will always be once per method call.";
        }

        @Override
        MetricType metricType() {
            return MetricType.COUNTER;
        }

        @Override
        String unit() {
            return MetricUnits.NONE;
        }

        static Counter get(Tag... tags) {
            return INSTANCE.registerCounter(tags);
        }

        static Counter register(Tag... tags) {
            return INSTANCE.registerCounter(tags);
        }
    }

    /**
     * Class for "ft.retry.retries.total" counters.
     */
    static class RetryRetriesTotal extends FaultToleranceMetric {

        static final RetryRetriesTotal INSTANCE = new RetryRetriesTotal();

        private RetryRetriesTotal() {
        }

        @Override
        String name() {
            return "ft.retry.retries.total";
        }

        @Override
        String description() {
            return "The number of times the method was retried";
        }

        @Override
        MetricType metricType() {
            return MetricType.COUNTER;
        }

        @Override
        String unit() {
            return MetricUnits.NONE;
        }

        static Counter get(Tag... tags) {
            return INSTANCE.registerCounter(tags);
        }

        static Counter register(Tag... tags) {
            return INSTANCE.registerCounter(tags);
        }
    }

    // -- Timeouts ------------------------------------------------------------

    enum TimeoutTimedOut implements Supplier<Tag> {
        TRUE("true"),
        FALSE("false");

        private final Tag metricTag;

        TimeoutTimedOut(String value) {
            this.metricTag = new Tag("timedOut", value);
        }

        public Tag get() {
            return metricTag;
        }
    }

    /**
     * Class for "ft.timeout.calls.total" counters.
     */
    static class TimeoutCallsTotal extends FaultToleranceMetric {

        static final TimeoutCallsTotal INSTANCE = new TimeoutCallsTotal();

        private TimeoutCallsTotal() {
        }

        @Override
        String name() {
            return "ft.timeout.calls.total";
        }

        @Override
        String description() {
            return "The number of times the timeout logic was run. This will usually be once "
                    + "per method call, but may be zero times if the circuit breaker prevents "
                    + "execution or more than once if the method is retried.";
        }

        @Override
        MetricType metricType() {
            return MetricType.COUNTER;
        }

        @Override
        String unit() {
            return MetricUnits.NONE;
        }

        static Counter get(Tag... tags) {
            return INSTANCE.registerCounter(tags);
        }

        static Counter register(Tag... tags) {
            return INSTANCE.registerCounter(tags);
        }
    }

    /**
     * Class for "ft.timeout.executionDuration" histograms.
     */
    static class TimeoutExecutionDuration extends FaultToleranceMetric {

        static final TimeoutExecutionDuration INSTANCE = new TimeoutExecutionDuration();

        private TimeoutExecutionDuration() {
        }

        @Override
        String name() {
            return "ft.timeout.executionDuration";
        }

        @Override
        String description() {
            return "Histogram of execution times for the method";
        }

        @Override
        MetricType metricType() {
            return MetricType.HISTOGRAM;
        }

        @Override
        String unit() {
            return MetricUnits.NANOSECONDS;
        }

        static Histogram get(Tag... tags) {
            return INSTANCE.registerHistogram(tags);
        }

        static Histogram register(Tag... tags) {
            return INSTANCE.registerHistogram(tags);
        }
    }

    // --- CircuitBreakers ----------------------------------------------------

    enum CircuitBreakerResult implements Supplier<Tag> {
        SUCCESS("success"),
        FAILURE("failure"),
        CIRCUIT_BREAKER_OPEN("circuitBreakerOpen");

        private final Tag metricTag;

        CircuitBreakerResult(String value) {
            metricTag = new Tag("circuitBreakerResult", value);
        }

        @Override
        public Tag get() {
            return metricTag;
        }
    }

    enum CircuitBreakerState implements Supplier<Tag> {
        OPEN("open"),
        CLOSED("closed"),
        HALF_OPEN("halfOpen");

        private final Tag metricTag;

        CircuitBreakerState(String value) {
            metricTag = new Tag("state", value);
        }

        @Override
        public Tag get() {
            return metricTag;
        }
    }

    /**
     * Class for "ft.circuitbreaker.calls.total" counters.
     */
    static class CircuitBreakerCallsTotal extends FaultToleranceMetric {

        static final CircuitBreakerCallsTotal INSTANCE = new CircuitBreakerCallsTotal();

        private CircuitBreakerCallsTotal() {
        }

        @Override
        String name() {
            return "ft.circuitbreaker.calls.total";
        }

        @Override
        String description() {
            return "The number of times the circuit breaker logic was run. This will usually be once "
                    + "per method call, but may be more than once if the method call is retried.";
        }

        @Override
        MetricType metricType() {
            return MetricType.COUNTER;
        }

        @Override
        String unit() {
            return MetricUnits.NONE;
        }

        static Counter get(Tag... tags) {
            return INSTANCE.registerCounter(tags);
        }

        static Counter register(Tag... tags) {
            return INSTANCE.registerCounter(tags);
        }
    }

    /**
     * Class for "ft.circuitbreaker.state.total" gauges.
     */
    static class CircuitBreakerStateTotal extends FaultToleranceMetric {

        static final CircuitBreakerStateTotal INSTANCE = new CircuitBreakerStateTotal();

        private CircuitBreakerStateTotal() {
        }

        @Override
        String name() {
            return "ft.circuitbreaker.state.total";
        }

        @Override
        String description() {
            return "Amount of time the circuit breaker has spent in each state";
        }

        @Override
        MetricType metricType() {
            return MetricType.GAUGE;
        }

        @Override
        String unit() {
            return MetricUnits.NANOSECONDS;
        }


        static Gauge<Long> get(Tag... tags) {
            return INSTANCE.getGauge(tags);
        }

        static Gauge<Long> register(Gauge<Long> gauge, Tag... tags) {
            return INSTANCE.registerGauge(gauge, tags);
        }
    }

    /**
     * Class for "ft.circuitbreaker.opened.total" counters.
     */
    static class CircuitBreakerOpenedTotal extends FaultToleranceMetric {

        static final CircuitBreakerOpenedTotal INSTANCE = new CircuitBreakerOpenedTotal();

        private CircuitBreakerOpenedTotal() {
        }

        @Override
        String name() {
            return "ft.circuitbreaker.opened.total";
        }

        @Override
        String description() {
            return "Number of times the circuit breaker has moved from closed state to open state";
        }

        @Override
        MetricType metricType() {
            return MetricType.COUNTER;
        }

        @Override
        String unit() {
            return MetricUnits.NONE;
        }

        static Counter get(Tag... tags) {
            return INSTANCE.registerCounter(tags);
        }

        static Counter register(Tag... tags) {
            return INSTANCE.registerCounter(tags);
        }
    }

    // --- Bulkheads ----------------------------------------------------------

    enum BulkheadResult implements Supplier<Tag> {
        ACCEPTED("accepted"),
        REJECTED("rejected");

        private final Tag metricTag;

        BulkheadResult(String value) {
            metricTag = new Tag("bulkheadResult", value);
        }

        @Override
        public Tag get() {
            return metricTag;
        }
    }

    /**
     * Class for "ft.bulkhead.calls.total" counters.
     */
    static class BulkheadCallsTotal extends FaultToleranceMetric {

        static final BulkheadCallsTotal INSTANCE = new BulkheadCallsTotal();

        private BulkheadCallsTotal() {
        }

        @Override
        String name() {
            return "ft.bulkhead.calls.total";
        }

        @Override
        String description() {
            return "The number of times the bulkhead logic was run. This will usually be once per "
                    + "method call, but may be zero times if the circuit breaker prevented execution "
                    + "or more than once if the method call is retried.";
        }

        @Override
        MetricType metricType() {
            return MetricType.COUNTER;
        }

        @Override
        String unit() {
            return MetricUnits.NONE;
        }

        static Counter get(Tag... tags) {
            return INSTANCE.registerCounter(tags);
        }

        static Counter register(Tag... tags) {
            return INSTANCE.registerCounter(tags);
        }
    }

    /**
     * Class for "ft.bulkhead.executionsRunning" gauges.
     */
    static class BulkheadExecutionsRunning extends FaultToleranceMetric {

        static final BulkheadExecutionsRunning INSTANCE = new BulkheadExecutionsRunning();

        private BulkheadExecutionsRunning() {
        }

        @Override
        String name() {
            return "ft.bulkhead.executionsRunning";
        }

        @Override
        String description() {
            return "Number of currently running executions";
        }

        @Override
        MetricType metricType() {
            return MetricType.GAUGE;
        }

        @Override
        String unit() {
            return MetricUnits.NONE;
        }

        static Gauge<Long> get(Tag... tags) {
            return INSTANCE.getGauge(tags);
        }

        static Gauge<Long> register(Gauge<Long> gauge, Tag... tags) {
            return INSTANCE.registerGauge(gauge, tags);
        }
    }

    /**
     * Class for "ft.bulkhead.executionsWaiting" gauges.
     */
    static class BulkheadExecutionsWaiting extends FaultToleranceMetric {

        static final BulkheadExecutionsWaiting INSTANCE = new BulkheadExecutionsWaiting();

        private BulkheadExecutionsWaiting() {
        }

        @Override
        String name() {
            return "ft.bulkhead.executionsWaiting";
        }

        @Override
        String description() {
            return "Number of executions currently waiting in the queue";
        }

        @Override
        MetricType metricType() {
            return MetricType.GAUGE;
        }

        @Override
        String unit() {
            return MetricUnits.NONE;
        }

        static Gauge<Long> get(Tag... tags) {
            return INSTANCE.getGauge(tags);
        }

        static Gauge<Long> register(Gauge<Long> gauge, Tag... tags) {
            return INSTANCE.registerGauge(gauge, tags);
        }
    }

    /**
     * Class for "ft.bulkhead.runningDuration" histograms.
     */
    static class BulkheadRunningDuration extends FaultToleranceMetric {

        static final BulkheadRunningDuration INSTANCE = new BulkheadRunningDuration();

        private BulkheadRunningDuration() {
        }

        @Override
        String name() {
            return "ft.bulkhead.runningDuration";
        }

        @Override
        String description() {
            return "Histogram of the time that method executions spent running";
        }

        @Override
        MetricType metricType() {
            return MetricType.HISTOGRAM;
        }

        @Override
        String unit() {
            return MetricUnits.NANOSECONDS;
        }

        static Histogram get(Tag... tags) {
            return INSTANCE.registerHistogram(tags);
        }

        static Histogram register(Tag... tags) {
            return INSTANCE.registerHistogram(tags);
        }
    }

    /**
     * Class for "ft.bulkhead.waitingDuration" histograms.
     */
    static class BulkheadWaitingDuration extends FaultToleranceMetric {

        static final BulkheadWaitingDuration INSTANCE = new BulkheadWaitingDuration();

        private BulkheadWaitingDuration() {
        }

        @Override
        String name() {
            return "ft.bulkhead.waitingDuration";
        }

        @Override
        String description() {
            return "Histogram of the time that method executions spent waiting in the queue";
        }

        @Override
        MetricType metricType() {
            return MetricType.HISTOGRAM;
        }

        @Override
        String unit() {
            return MetricUnits.NANOSECONDS;
        }

        static Histogram get(Tag... tags) {
            return INSTANCE.registerHistogram(tags);
        }

        static Histogram register(Tag... tags) {
            return INSTANCE.registerHistogram(tags);
        }
    }
}
