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

import javax.enterprise.inject.spi.CDI;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;

import static io.helidon.microprofile.faulttolerance.FaultToleranceExtension.getRealClass;
import static io.helidon.microprofile.faulttolerance.FaultToleranceExtension.isFaultToleranceMetricsEnabled;

/**
 * Class FaultToleranceMetrics.
 */
class FaultToleranceMetrics {

    static final String METRIC_NAME_TEMPLATE = "ft.%s.%s.%s";

    private static MetricRegistry metricRegistry;

    private FaultToleranceMetrics() {
    }

    static boolean enabled() {
        return getMetricRegistry() != null;
    }

    static synchronized MetricRegistry getMetricRegistry() {
        if (metricRegistry == null) {
            metricRegistry = CDI.current().select(MetricRegistry.class).get();
        }
        return metricRegistry;
    }

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
        Method method = getRealClass(bean).getMethod(methodName, params);
        return getCounter(method, name).getCount();
    }

    static Histogram getHistogram(Object bean, String methodName, String name,
                                  Class<?>... params) throws Exception {
        Method method = getRealClass(bean).getMethod(methodName, params);
        return getHistogram(method, name);
    }

    static <T> Gauge<T> getGauge(Object bean, String methodName, String name,
                                 Class<?>... params) throws Exception {
        Method method = getRealClass(bean).getMethod(methodName, params);
        return getGauge(method, name);
    }

    // -- Global --------------------------------------------------------------

    static final String INVOCATIONS_TOTAL = "invocations.total";
    static final String INVOCATIONS_FAILED_TOTAL = "invocations.failed.total";

    /**
     * Register global method counters for a method.
     *
     * @param method The method.
     */
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
        registerHistogram(
                String.format(METRIC_NAME_TEMPLATE,
                        method.getDeclaringClass().getName(),
                        method.getName(),
                        BULKHEAD_WAITING_DURATION),
                "Histogram of the time executions spend waiting in the queue.");
    }

    // -- Utility methods ----------------------------------------------------

    /**
     * Register a single counter.
     *
     * @param name Name of counter.
     * @param description Description of counter.
     * @return The counter created.
     */
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
     */
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
     */
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
}
