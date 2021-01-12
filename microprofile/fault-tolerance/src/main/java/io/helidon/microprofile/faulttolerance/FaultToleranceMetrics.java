/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.enterprise.inject.spi.CDI;
import javax.enterprise.util.AnnotationLiteral;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;

import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

/**
 * Utility class for register and fetch FT metrics.
 */
class FaultToleranceMetrics {

    static final String METRIC_NAME_TEMPLATE = "ft.%s.%s.%s";

    private static MetricRegistry metricRegistry;

    private static final Map<String, String> METRIC_DESCRIPTIONS = new HashMap<>();

    static final String INVOCATIONS_TOTAL = "ft.invocations.total";
    static final String RETRY_CALLS_TOTAL = "ft.retry.calls.total";
    static final String RETRY_RETRIES_TOTAL = "ft.retry.retries.total";
    static final String TIMEOUT_CALLS_TOTAL = "ft.timeout.calls.total";
    static final String TIMEOUT_EXECUTIONDURATION = "ft.timeout.executionDuration";
    static final String CIRCUITBREAKER_CALLS_TOTAL = "ft.circuitbreaker.calls.total";
    static final String CIRCUITBREAKER_STATE_TOTAL = "ft.circuitbreaker.state.total";
    static final String CIRCUITBREAKER_OPENED_TOTAL = "ft.circuitbreaker.opened.total";
    static final String BULKHEAD_CALLS_TOTAL = "ft.bulkhead.calls.total";
    static final String BULKHEAD_EXECUTIONSRUNNING = "ft.bulkhead.executionsRunning";
    static final String BULKHEAD_EXECUTIONSWAITING = "ft.bulkhead.executionsWaiting";
    static final String BULKHEAD_RUNNINGDURATION = "ft.bulkhead.runningDuration";
    static final String BULKHEAD_WAITINGDURATION = "ft.bulkhead.waitingDuration";

    static {
        METRIC_DESCRIPTIONS.put(INVOCATIONS_TOTAL, "The number of times the method was called");
        METRIC_DESCRIPTIONS.put(RETRY_CALLS_TOTAL, "The number of times the retry logic was run. " +
                "This will always be once per method call.");
        METRIC_DESCRIPTIONS.put(RETRY_RETRIES_TOTAL, "The number of times the method was retried");
        METRIC_DESCRIPTIONS.put(TIMEOUT_CALLS_TOTAL, "The number of times the timeout logic was run. " +
                "This will usually be once per method call, but may be zero times if the circuit breaker " +
                "prevents execution or more than once if the method is retried.");
        METRIC_DESCRIPTIONS.put(TIMEOUT_EXECUTIONDURATION, "Histogram of execution times for the method");
        METRIC_DESCRIPTIONS.put(CIRCUITBREAKER_CALLS_TOTAL, "The number of times the circuit breaker logic " +
                "was run. This will usually be once per method call, but may be more than once if the method " +
                "call is retried.");
        METRIC_DESCRIPTIONS.put(CIRCUITBREAKER_STATE_TOTAL, "Amount of time the circuit breaker has spent in each state");
        METRIC_DESCRIPTIONS.put(CIRCUITBREAKER_OPENED_TOTAL, "Number of times the circuit breaker has moved from " +
                "closed state to open state");
        METRIC_DESCRIPTIONS.put(BULKHEAD_CALLS_TOTAL, "The number of times the bulkhead logic was run. This will " +
                "usually be once per method call, but may be zero times if the circuit breaker prevented " +
                "execution or more than once if the method call is retried.");
        METRIC_DESCRIPTIONS.put(BULKHEAD_EXECUTIONSRUNNING, "Number of currently running executions");
        METRIC_DESCRIPTIONS.put(BULKHEAD_EXECUTIONSWAITING, "Number of executions currently waiting in the queue");
        METRIC_DESCRIPTIONS.put(BULKHEAD_RUNNINGDURATION, "Histogram of the time that method executions spent running");
        METRIC_DESCRIPTIONS.put(BULKHEAD_WAITINGDURATION, "Histogram of the time that method executions spent waiting " +
                "in the queue");
    }

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
    static abstract class FaultToleranceMetric {

        abstract String name();

        abstract String description();

        abstract MetricType metricType();

        abstract String unit();

        abstract Metric metric(Tag... tags);

        protected Counter getOrRegisterCounter(Tag... tags) {
            MetricID metricID = new MetricID(name(), tags);
            Counter counter = (Counter) getMetricRegistry().getMetrics().get(metricID);
            if (counter == null) {
                Metadata metadata = Metadata.builder()
                        .withName(name())
                        .withDisplayName(name())
                        .withDescription(description())
                        .withType(metricType())
                        .withUnit(unit())
                        .reusable(true)
                        .build();
                counter = getMetricRegistry().counter(metadata, tags);
            }
            return counter;
        }

        protected Histogram getOrRegisterHistogram(Tag... tags) {
            MetricID metricID = new MetricID(name(), tags);
            Histogram histogram = (Histogram) getMetricRegistry().getMetrics().get(metricID);
            if (histogram == null) {
                Metadata metadata = Metadata.builder()
                        .withName(name())
                        .withDisplayName(name())
                        .withDescription(description())
                        .withType(metricType())
                        .withUnit(unit())
                        .reusable(true)
                        .build();
                histogram = getMetricRegistry().histogram(metadata, tags);
            }
            return histogram;
        }

        protected <T> Gauge<T> registerGauge(Gauge<T> gauge, Tag... tags) {
            MetricID metricID = new MetricID(name(), tags);
            Gauge<T> existing = (Gauge<T>) getMetricRegistry().getGauges().get(metricID);
            if (existing == null) {
                Metadata metadata = Metadata.builder()
                        .withName(name())
                        .withDisplayName(name())
                        .withDescription(description())
                        .withType(metricType())
                        .withUnit(unit())
                        .reusable(true)
                        .build();
                existing = getMetricRegistry().register(metadata, gauge, tags);
            }
            return existing;
        }
    }

    // -- ft.invocations.total ------------------------------------------------

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

        final static InvocationsTotal INSTANCE = new InvocationsTotal();

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

        @Override
        Counter metric(Tag... tags) {
            return getOrRegisterCounter(tags);
        }

        static Counter get(Tag... tags) {
            return INSTANCE.metric(tags);
        }
    }

    // -- ft.retry.calls.total ------------------------------------------------

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

        final static RetryCallsTotal INSTANCE = new RetryCallsTotal();

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

        @Override
        Counter metric(Tag... tags) {
            return getOrRegisterCounter(tags);
        }

        static Counter get(Tag... tags) {
            return INSTANCE.metric(tags);
        }
    }

    // -- ft.retry.retries.total ----------------------------------------------

    /**
     * Class for "ft.retry.retries.total" counters.
     */
    static class RetryRetriesTotal extends FaultToleranceMetric {

        final static RetryRetriesTotal INSTANCE = new RetryRetriesTotal();

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

        @Override
        Counter metric(Tag... tags) {
            return getOrRegisterCounter(tags);
        }

        static Counter get(Tag... tags) {
            return INSTANCE.metric(tags);
        }
    }

    // -- ft.timeout.calls.total ----------------------------------------------

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

        final static TimeoutCallsTotal INSTANCE = new TimeoutCallsTotal();

        private TimeoutCallsTotal() {
        }

        @Override
        String name() {
            return "ft.timeout.calls.total";
        }

        @Override
        String description() {
            return "The number of times the timeout logic was run. This will usually be once " +
                    "per method call, but may be zero times if the circuit breaker prevents " +
                    "execution or more than once if the method is retried.";
        }

        @Override
        MetricType metricType() {
            return MetricType.COUNTER;
        }

        @Override
        String unit() {
            return MetricUnits.NONE;
        }

        @Override
        Counter metric(Tag... tags) {
            return getOrRegisterCounter(tags);
        }

        static Counter get(Tag... tags) {
            return INSTANCE.metric(tags);
        }
    }

    /**
     * Class for "ft.timeout.executionDuration" counters.
     */
    static class TimeoutExecutionDuration extends FaultToleranceMetric {

        final static TimeoutExecutionDuration INSTANCE = new TimeoutExecutionDuration();

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

        @Override
        Histogram metric(Tag... tags) {
            return getOrRegisterHistogram(tags);
        }

        static Histogram get(Tag... tags) {
            return INSTANCE.metric(tags);
        }
    }

    // ---

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

        final static CircuitBreakerCallsTotal INSTANCE = new CircuitBreakerCallsTotal();

        private CircuitBreakerCallsTotal() {
        }

        @Override
        String name() {
            return "ft.circuitbreaker.calls.total";
        }

        @Override
        String description() {
            return "The number of times the circuit breaker logic was run. This will usually be once " +
                    "per method call, but may be more than once if the method call is retried.";
        }

        @Override
        MetricType metricType() {
            return MetricType.COUNTER;
        }

        @Override
        String unit() {
            return MetricUnits.NONE;
        }

        @Override
        Counter metric(Tag... tags) {
            return getOrRegisterCounter(tags);
        }

        static Counter get(Tag... tags) {
            return INSTANCE.metric(tags);
        }
    }

    /**
     * Class for "ft.circuitbreaker.state.total" counters.
     */
    static class CircuitBreakerStateTotal extends FaultToleranceMetric {

        final static CircuitBreakerStateTotal INSTANCE = new CircuitBreakerStateTotal();

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

        @Override
        @SuppressWarnings("unchecked")
        Gauge<Long> metric(Tag... tags) {
            MetricID metricID = new MetricID(name(), tags);
            return getMetricRegistry().getGauges().get(metricID);
        }

        static Gauge<Long> register(Gauge<Long> gauge, Tag... tags) {
            return INSTANCE.registerGauge(gauge, tags);
        }
    }

    /**
     * Class for "ft.circuitbreaker.opened.total" counters.
     */
    static class CircuitBreakerOpenedTotal extends FaultToleranceMetric {

        final static CircuitBreakerOpenedTotal INSTANCE = new CircuitBreakerOpenedTotal();

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

        @Override
        Counter metric(Tag... tags) {
            return getOrRegisterCounter(tags);
        }

        static Counter get(Tag... tags) {
            return INSTANCE.metric(tags);
        }
    }

    // ---

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

    // ---

    static Counter getCounter(Method method, String name, Tag... tags) {
        MetricID metricID = new MetricID(name, tags);
        return (Counter) getMetricRegistry().getMetrics().get(metricID);
    }

    static Counter getOrRegisterCounter(Method method, String name, Tag... tags) {
        Counter counter = getCounter(method, name, tags);
        if (counter == null) {
            String description = METRIC_DESCRIPTIONS.get(name);
            Metadata metadata = Metadata.builder()
                    .withName(name)
                    .withDisplayName(name)
                    .withDescription(description == null ? "" : description)
                    .withType(MetricType.COUNTER)
                    .withUnit(MetricUnits.NONE)
                    .reusable(true)
                    .build();
            counter = getMetricRegistry().counter(metadata, tags);
        }
        return counter;
    }

    static Histogram getHistogram(Method method, String name, Tag... tags) {
        MetricID metricID = new MetricID(name, tags);
        return (Histogram) getMetricRegistry().getMetrics().get(metricID);
    }

    static Histogram getOrRegisterHistogram(Method method, String name, Tag... tags) {
        Histogram histogram = getHistogram(method, name, tags);
        if (histogram == null) {
            String description = METRIC_DESCRIPTIONS.get(name);
            Metadata metadata = Metadata.builder()
                    .withName(name)
                    .withDisplayName(name)
                    .withDescription(description == null ? "" : description)
                    .withType(MetricType.HISTOGRAM)
                    .withUnit(MetricUnits.NONE)
                    .reusable(true)
                    .build();
            histogram = getMetricRegistry().histogram(metadata, tags);
        }
        return histogram;
    }

    // -- old ----------------------------------------------------------------------------------------------------------

    /*
    @SuppressWarnings("unchecked")
    static <T extends Metric> T getMetric(Method method, String name) {
        MetricID metricID = newMetricID(String.format(METRIC_NAME_TEMPLATE,
                method.getDeclaringClass().getName(),
                method.getName(), name));
        return (T) getMetricRegistry().getMetrics().get(metricID);
    }

    static Counter getCounter(Method method, String name) {
        return (Counter) getMetric(method, name);
    }

    static Histogram getHistogram(Method method, String name) {
        return (Histogram) getMetric(method, name);
    }

    @SuppressWarnings("unchecked")
    static <T> Gauge<T> getGauge(Method method, String name) {
        return (Gauge<T>) getMetric(method, name);
    }

    static long getCounter(Object bean, String methodName, String name,
                           Class<?>... params) throws Exception {
        Method method = findMethod(getRealClass(bean), methodName, params);
        return getCounter(method, name).getCount();
    }

    static Histogram getHistogram(Object bean, String methodName, String name,
                                  Class<?>... params) throws Exception {
        Method method = findMethod(getRealClass(bean), methodName, params);
        return getHistogram(method, name);
    }

    static <T> Gauge<T> getGauge(Object bean, String methodName, String name,
                                 Class<?>... params) throws Exception {
        Method method = findMethod(getRealClass(bean), methodName, params);
        return getGauge(method, name);
    }

    /**
     * Attempts to find a method even if not accessible.
     *
     * @param beanClass bean class.
     * @param methodName name of method.
     * @param params param types.
     * @return method found.
     * @throws NoSuchMethodException if not found.
     * /
    private static Method findMethod(Class<?> beanClass, String methodName,
                                     Class<?>... params) throws NoSuchMethodException {
        try {
            Method method = beanClass.getDeclaredMethod(methodName, params);
            method.setAccessible(true);
            return method;
        } catch (Exception e) {
            return beanClass.getMethod(methodName, params);
        }
    }

    // -- Global --------------------------------------------------------------

    static final String INVOCATIONS_FAILED_TOTAL = "invocations.failed.total";

    /**
     * Register global method counters for a method.
     *
     * @param method The method.
     * /
    static void registerMetrics(Method method) {
        if (!isFaultToleranceMetricsEnabled()) {
            return;
        }

        registerCounter(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          INVOCATIONS_TOTAL),
            "The number of times the method was called");
        registerCounter(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          INVOCATIONS_FAILED_TOTAL),
            "The number of times the method was called and, "
            + "after all Fault Tolerance actions had been processed, "
            + "threw a Throwable");
    }

    // -- Retry ---------------------------------------------------------------

    static final String RETRY_CALLS_SUCCEEDED_NOT_RETRIED_TOTAL = "retry.callsSucceededNotRetried.total";
    static final String RETRY_CALLS_SUCCEEDED_RETRIED_TOTAL = "retry.callsSucceededRetried.total";
    static final String RETRY_CALLS_FAILED_TOTAL = "retry.callsFailed.total";
    static final String RETRY_RETRIES_TOTAL = "retry.retries.total";

    static void registerRetryMetrics(Method method) {
        if (!isFaultToleranceMetricsEnabled()) {
            return;
        }

        registerCounter(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          RETRY_CALLS_SUCCEEDED_NOT_RETRIED_TOTAL),
            "The number of times the method was called and succeeded without retrying");
        registerCounter(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          RETRY_CALLS_SUCCEEDED_RETRIED_TOTAL),
            "The number of times the method was called and succeeded after retrying at least once");
        registerCounter(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          RETRY_CALLS_FAILED_TOTAL),
            "The number of times the method was called and ultimately failed after retrying");
        registerCounter(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          RETRY_RETRIES_TOTAL),
            "The total number of times the method was retried");
    }


    // -- Timeout ---------------------------------------------------------------

    static final String TIMEOUT_EXECUTION_DURATION = "timeout.executionDuration";
    static final String TIMEOUT_CALLS_TIMED_OUT_TOTAL = "timeout.callsTimedOut.total";
    static final String TIMEOUT_CALLS_NOT_TIMED_OUT_TOTAL = "timeout.callsNotTimedOut.total";

    static void registerTimeoutMetrics(Method method) {
        if (!isFaultToleranceMetricsEnabled()) {
            return;
        }

        registerHistogram(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          TIMEOUT_EXECUTION_DURATION),
            "Histogram of execution times for the method");
        registerCounter(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          TIMEOUT_CALLS_TIMED_OUT_TOTAL),
            "The number of times the method timed out");
        registerCounter(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          TIMEOUT_CALLS_NOT_TIMED_OUT_TOTAL),
            "The number of times the method completed without timing out");
    }

    // -- CircuitBreaker -----------------------------------------------------

    static final String BREAKER_CALLS_SUCCEEDED_TOTAL = "circuitbreaker.callsSucceeded.total";
    static final String BREAKER_CALLS_FAILED_TOTAL = "circuitbreaker.callsFailed.total";
    static final String BREAKER_CALLS_PREVENTED_TOTAL = "circuitbreaker.callsPrevented.total";
    static final String BREAKER_OPENED_TOTAL = "circuitbreaker.opened.total";

    static final String BREAKER_OPEN_TOTAL = "circuitbreaker.open.total";
    static final String BREAKER_CLOSED_TOTAL = "circuitbreaker.closed.total";
    static final String BREAKER_HALF_OPEN_TOTAL = "circuitbreaker.halfOpen.total";

    static void registerCircuitBreakerMetrics(Method method) {
        if (!isFaultToleranceMetricsEnabled()) {
            return;
        }

        registerCounter(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          BREAKER_CALLS_SUCCEEDED_TOTAL),
            "Number of calls allowed to run by the circuit breaker that "
            + "returned successfully");
        registerCounter(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          BREAKER_CALLS_FAILED_TOTAL),
            "Number of calls allowed to run by the circuit breaker that then failed");
        registerCounter(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          BREAKER_CALLS_PREVENTED_TOTAL),
            "Number of calls prevented from running by an open circuit breaker");
        registerCounter(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          BREAKER_OPENED_TOTAL),
            "Number of times the circuit breaker has moved from closed state to open state");
    }

    // -- Fallback -----------------------------------------------------------

    static final String FALLBACK_CALLS_TOTAL = "fallback.calls.total";

    static void registerFallbackMetrics(Method method) {
        if (!isFaultToleranceMetricsEnabled()) {
            return;
        }

        registerCounter(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          FALLBACK_CALLS_TOTAL),
            "Number of times the fallback handler or method was called");
    }

    // -- Bulkhead -----------------------------------------------------------

    static final String BULKHEAD_CONCURRENT_EXECUTIONS = "bulkhead.concurrentExecutions";
    static final String BULKHEAD_CALLS_ACCEPTED_TOTAL = "bulkhead.callsAccepted.total";
    static final String BULKHEAD_CALLS_REJECTED_TOTAL = "bulkhead.callsRejected.total";
    static final String BULKHEAD_EXECUTION_DURATION = "bulkhead.executionDuration";
    static final String BULKHEAD_WAITING_QUEUE_POPULATION = "bulkhead.waitingQueue.population";
    static final String BULKHEAD_WAITING_DURATION = "bulkhead.waiting.duration";

    static void registerBulkheadMetrics(Method method) {
        if (!isFaultToleranceMetricsEnabled()) {
            return;
        }

        registerCounter(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          BULKHEAD_CALLS_ACCEPTED_TOTAL),
            "Number of calls accepted by the bulkhead");
        registerCounter(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          BULKHEAD_CALLS_REJECTED_TOTAL),
            "Number of calls rejected by the bulkhead");
        registerHistogram(
            String.format(METRIC_NAME_TEMPLATE,
                          method.getDeclaringClass().getName(),
                          method.getName(),
                          BULKHEAD_EXECUTION_DURATION),
            "Histogram of method execution times. This does not include any "
            + "time spent waiting in the bulkhead queue.");
    }

    // -- Utility methods ----------------------------------------------------

    /**
     * Register a single counter.
     *
     * @param name Name of counter.
     * @param description Description of counter.
     * @return The counter created.
     * /
    private static Counter registerCounter(String name, String description) {
        return getMetricRegistry().counter(
                newMetadata(name, name, description, MetricType.COUNTER, MetricUnits.NONE,
                        true));
    }

    /**
     * Register a histogram with nanos as unit.
     *
     * @param name Name of histogram.
     * @param description Description of histogram.
     * @return The histogram created.
     * /
    static Histogram registerHistogram(String name, String description) {
        return getMetricRegistry().histogram(
                newMetadata(name, name, description, MetricType.HISTOGRAM, MetricUnits.NANOSECONDS,
                        true));
    }

    /**
     * Register a gauge with nanos as unit. Checks if gauge is already registered
     * using synchronization.
     *
     * @param metricName Name of metric.
     * @param description Description of gauge.
     * @return The gauge created or existing if already created.
     * /
    @SuppressWarnings("unchecked")
    static synchronized <T> Gauge<T> registerGauge(Method method, String metricName, String description, Gauge<T> gauge) {
        MetricID metricID = newMetricID(String.format(METRIC_NAME_TEMPLATE,
                method.getDeclaringClass().getName(),
                method.getName(),
                metricName));
        Gauge<T> existing = getMetricRegistry().getGauges().get(metricID);
        if (existing == null) {
            getMetricRegistry().register(
                    newMetadata(metricID.getName(), metricID.getName(), description, MetricType.GAUGE, MetricUnits.NANOSECONDS,
                            true),
                    gauge);
        }
        return existing;
    }

    private static MetricID newMetricID(String name) {
        return new MetricID(name);
    }

    private static Metadata newMetadata(String name, String displayName, String description, MetricType metricType,
                                        String metricUnits, boolean isReusable) {
        return Metadata.builder()
                .withName(name)
                .withDisplayName(displayName)
                .withDescription(description)
                .withType(metricType)
                .withUnit(metricUnits)
                .reusable(isReusable)
                .build();
    }
     */
}
