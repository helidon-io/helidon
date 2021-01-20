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

import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.junit.jupiter.api.Test;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.*;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.InvocationFallback.NOT_DEFINED;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.InvocationResult.EXCEPTION_THROWN;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.InvocationResult.VALUE_RETURNED;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.enabled;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.getMetricRegistry;

/**
 * Tests for bean metrics.
 */
class MetricsTest extends FaultToleranceTest {

    @Test
    void testEnable() {
        assertThat(enabled(), is(true));
    }

    @Test
    void testInjectCounter() {
        MetricsBean bean = newBean(MetricsBean.class);
        assertThat(bean, notNullValue());
        bean.getCounter().inc();
        assertThat(bean.getCounter().getCount(), is(1L));
    }

    @Test
    void testInjectCounterProgrammatically() {
        MetricRegistry metricRegistry = getMetricRegistry();
        metricRegistry.counter(Metadata.builder()
                .withName("dcounter")
                .withType(MetricType.COUNTER)
                .withUnit(MetricUnits.NONE)
                .build());
        metricRegistry.counter("dcounter").inc();
        assertThat(metricRegistry.counter("dcounter").getCount(), is(1L));
    }

    @Test
    void testGlobalCountersSuccess() {
        MetricsBean bean = newBean(MetricsBean.class);
        bean.retryOne(5);

        Counter total = InvocationsTotal.get(
                getMethodTag(bean, "retryOne"),
                VALUE_RETURNED.get(),
                NOT_DEFINED.get());
        assertThat(total.getCount(), is(1L));

        Counter failedTotal = InvocationsTotal.get(
                getMethodTag(bean, "retryOne"),
                EXCEPTION_THROWN.get(),
                NOT_DEFINED.get());
        assertThat(failedTotal.getCount(), is(0L));
    }

    @Test
    void testGlobalCountersFailure() {
        MetricsBean bean = newBean(MetricsBean.class);
        try {
            bean.retryTwo(10);
        } catch (Exception e) {
            // falls through
        }

        Counter total = InvocationsTotal.get(
                getMethodTag(bean, "retryTwo"),
                VALUE_RETURNED.get(),
                NOT_DEFINED.get());
        assertThat(total.getCount(), is(0L));

        Counter failedTotal = InvocationsTotal.get(
                getMethodTag(bean, "retryTwo"),
                EXCEPTION_THROWN.get(),
                NOT_DEFINED.get());
        assertThat(failedTotal.getCount(), is(1L));
    }

    @Test
    void testRetryCounters() {
        MetricsBean bean = newBean(MetricsBean.class);
        bean.retryThree(5);

        Counter retryRetriesTotal = RetryRetriesTotal.get(
                getMethodTag(bean, "retryThree"));
        assertThat(retryRetriesTotal.getCount(), is(5L));

        Counter retryCallsTotal = RetryCallsTotal.get(
                getMethodTag(bean, "retryThree"),
                RetryRetried.FALSE.get(),
                RetryResult.VALUE_RETURNED.get());
        assertThat(retryCallsTotal.getCount(), is(0L));

        retryCallsTotal = RetryCallsTotal.get(
                getMethodTag(bean, "retryThree"),
                RetryRetried.TRUE.get(),
                RetryResult.VALUE_RETURNED.get());
        assertThat(retryCallsTotal.getCount(), is(1L));

        retryCallsTotal = RetryCallsTotal.get(
                getMethodTag(bean, "retryThree"),
                RetryRetried.TRUE.get(),
                RetryResult.MAX_RETRIES_REACHED.get());
        assertThat(retryCallsTotal.getCount(), is(0L));
    }

    @Test
    void testRetryCountersFailure() {
        MetricsBean bean = newBean(MetricsBean.class);
        try {
            bean.retryFour(10);
        } catch (Exception e) {
            // falls through
        }

        Counter retryRetriesTotal = RetryRetriesTotal.get(
                getMethodTag(bean, "retryFour"));
        assertThat(retryRetriesTotal.getCount(), is(5L));

        Counter retryCallsTotal = RetryCallsTotal.get(
                getMethodTag(bean, "retryFour"),
                RetryRetried.FALSE.get(),
                RetryResult.VALUE_RETURNED.get());
        assertThat(retryCallsTotal.getCount(), is(0L));

        retryCallsTotal = RetryCallsTotal.get(
                getMethodTag(bean, "retryFour"),
                RetryRetried.TRUE.get(),
                RetryResult.VALUE_RETURNED.get());
        assertThat(retryCallsTotal.getCount(), is(0L));

        retryCallsTotal = RetryCallsTotal.get(
                getMethodTag(bean, "retryFour"),
                RetryRetried.TRUE.get(),
                RetryResult.MAX_RETRIES_REACHED.get());
        assertThat(retryCallsTotal.getCount(), is(1L));
    }

    @Test
    void testRetryCountersSuccess() {
        MetricsBean bean = newBean(MetricsBean.class);
        bean.retryFive(0);

        Counter retryRetriesTotal = RetryRetriesTotal.get(
                getMethodTag(bean, "retryFive"));
        assertThat(retryRetriesTotal.getCount(), is(0L));

        Counter retryCallsTotal = RetryCallsTotal.get(
                getMethodTag(bean, "retryFive"),
                RetryRetried.FALSE.get(),
                RetryResult.VALUE_RETURNED.get());
        assertThat(retryCallsTotal.getCount(), is(1L));

        retryCallsTotal = RetryCallsTotal.get(
                getMethodTag(bean, "retryFive"),
                RetryRetried.TRUE.get(),
                RetryResult.VALUE_RETURNED.get());
        assertThat(retryCallsTotal.getCount(), is(0L));

        retryCallsTotal = RetryCallsTotal.get(
                getMethodTag(bean, "retryFive"),
                RetryRetried.TRUE.get(),
                RetryResult.MAX_RETRIES_REACHED.get());
        assertThat(retryCallsTotal.getCount(), is(0L));
    }

    @Test
    void testTimeoutSuccess() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        bean.noTimeout();

        Counter timeoutCallsTotal = TimeoutCallsTotal.get(
                getMethodTag(bean, "noTimeout"),
                TimeoutTimedOut.TRUE.get());
        assertThat(timeoutCallsTotal.getCount(), is(0L));

        timeoutCallsTotal = TimeoutCallsTotal.get(
                getMethodTag(bean, "noTimeout"),
                TimeoutTimedOut.FALSE.get());
        assertThat(timeoutCallsTotal.getCount(), is(1L));

        Histogram timeoutExecutionDuration = TimeoutExecutionDuration.get(
                getMethodTag(bean, "noTimeout"));
        assertThat(timeoutExecutionDuration.getCount(), is(1L));
    }

    @Test
    void testTimeoutFailure() {
        MetricsBean bean = newBean(MetricsBean.class);
        try {
            bean.forceTimeout();
        } catch (Exception e) {
            // falls through
        }

        Counter timeoutCallsTotal = TimeoutCallsTotal.get(
                getMethodTag(bean, "forceTimeout"),
                TimeoutTimedOut.TRUE.get());
        assertThat(timeoutCallsTotal.getCount(), is(1L));

        timeoutCallsTotal = TimeoutCallsTotal.get(
                getMethodTag(bean, "forceTimeout"),
                TimeoutTimedOut.FALSE.get());
        assertThat(timeoutCallsTotal.getCount(), is(0L));

        Histogram timeoutExecutionDuration = TimeoutExecutionDuration.get(
                getMethodTag(bean, "forceTimeout"));
        assertThat(timeoutExecutionDuration.getCount(), is(1L));
    }

    @Test
    void testBreakerTrip() {
        MetricsBean bean = newBean(MetricsBean.class);

        for (int i = 0; i < CircuitBreakerBean.REQUEST_VOLUME_THRESHOLD; i++) {
            assertThrows(RuntimeException.class, () -> bean.exerciseBreaker(false));
        }

        assertThrows(CircuitBreakerOpenException.class, () -> bean.exerciseBreaker(false));

        Counter circuitBreakerOpenedTotal = CircuitBreakerOpenedTotal.get(
                getMethodTag(bean, "exerciseBreaker"));
        assertThat(circuitBreakerOpenedTotal.getCount(), is(1L));

        Counter circuitBreakerCallsTotal = CircuitBreakerCallsTotal.get(
                getMethodTag(bean, "exerciseBreaker"),
                CircuitBreakerResult.SUCCESS.get());
        assertThat(circuitBreakerCallsTotal.getCount(), is(0L));

        circuitBreakerCallsTotal = CircuitBreakerCallsTotal.get(
                getMethodTag(bean, "exerciseBreaker"),
                CircuitBreakerResult.FAILURE.get());
        assertThat(circuitBreakerCallsTotal.getCount(), is((long) CircuitBreakerBean.REQUEST_VOLUME_THRESHOLD));

        circuitBreakerCallsTotal = CircuitBreakerCallsTotal.get(
                getMethodTag(bean, "exerciseBreaker"),
                CircuitBreakerResult.CIRCUIT_BREAKER_OPEN.get());
        assertThat(circuitBreakerCallsTotal.getCount(), is(1L));
    }

    @Test
    void testBreakerGauges() {
        MetricsBean bean = newBean(MetricsBean.class);

        Gauge<Long> closedStateTotal = null;
        Gauge<Long> openStateTotal = null;
        Gauge<Long> halfOpenStateTotal = null;

        for (int i = 0; i < CircuitBreakerBean.REQUEST_VOLUME_THRESHOLD - 1; i++) {
            assertThrows(RuntimeException.class, () -> bean.exerciseGauges(false));

            closedStateTotal = CircuitBreakerStateTotal.get(
                    getMethodTag(bean, "exerciseGauges"),
                    CircuitBreakerState.CLOSED.get());
            assertThat(closedStateTotal.getValue(), is(not(0L)));

            openStateTotal = CircuitBreakerStateTotal.get(
                    getMethodTag(bean, "exerciseGauges"),
                    CircuitBreakerState.OPEN.get());
            assertThat(openStateTotal.getValue(), is(0L));

            halfOpenStateTotal = CircuitBreakerStateTotal.get(
                    getMethodTag(bean, "exerciseGauges"),
                    CircuitBreakerState.HALF_OPEN.get());
            assertThat(halfOpenStateTotal.getValue(), is(0L));
        }
        assertThrows(RuntimeException.class, () -> bean.exerciseGauges(false));
        assertThrows(CircuitBreakerOpenException.class, () -> bean.exerciseGauges(false));

        assertThat(closedStateTotal.getValue(), is(not(0L)));
        assertThat(openStateTotal.getValue(), is(not(0L)));
        assertThat(halfOpenStateTotal.getValue(), is(0L));
    }

    @Test
    void testBreakerExceptionCounters() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);

        Counter successCallsTotal = CircuitBreakerCallsTotal.get(
                getMethodTag(bean, "exerciseBreakerException"),
                CircuitBreakerResult.SUCCESS.get());

        Counter failureCallsTotal = CircuitBreakerCallsTotal.get(
                getMethodTag(bean, "exerciseBreakerException"),
                CircuitBreakerResult.FAILURE.get());

        Counter circuitBreakerOpenTotal = CircuitBreakerCallsTotal.get(
                getMethodTag(bean, "exerciseBreakerException"),
                CircuitBreakerResult.CIRCUIT_BREAKER_OPEN.get());

        // First failure
        assertThrows(MetricsBean.TestException.class, () -> bean.exerciseBreakerException(false));  // failure
        assertThat(successCallsTotal.getCount(), is(0L));
        assertThat(failureCallsTotal.getCount(), is(1L));
        assertThat(circuitBreakerOpenTotal.getCount(), is(0L));

        // Second failure
        assertThrows(MetricsBean.TestException.class, () -> bean.exerciseBreakerException(false));  // failure
        assertThat(successCallsTotal.getCount(), is(0L));
        assertThat(failureCallsTotal.getCount(), is(2L));
        assertThat(circuitBreakerOpenTotal.getCount(), is(0L));

        assertThrows(Exception.class, () -> bean.exerciseBreakerException(true));  // failure
        assertThat(successCallsTotal.getCount(), is(0L));
        assertThat(failureCallsTotal.getCount(), is(2L));
        assertThat(circuitBreakerOpenTotal.getCount(), is(1L));

        // Sleep longer than circuit breaker delay
        Thread.sleep(1500);

        // Following calls should succeed
        for (int i = 0; i < 2; i++) {
            try {
                bean.exerciseBreakerException(true);    // success
            } catch (RuntimeException e) {
                // expected
            }
        }
        assertThat(successCallsTotal.getCount(), is(2L));
        assertThat(failureCallsTotal.getCount(), is(2L));
        assertThat(circuitBreakerOpenTotal.getCount(), is(1L));

        try {
            bean.exerciseBreakerException(true);    // success
        } catch (RuntimeException e) {
            // expected
        }
        assertThat(successCallsTotal.getCount(), is(3L));
        assertThat(failureCallsTotal.getCount(), is(2L));
        assertThat(circuitBreakerOpenTotal.getCount(), is(1L));
    }


    @Test
    void testFallbackMetrics() {
        MetricsBean bean = newBean(MetricsBean.class);

        Counter fallbackApplied = InvocationsTotal.get(
                getMethodTag(bean, "fallback"),
                InvocationResult.VALUE_RETURNED.get(),
                InvocationFallback.APPLIED.get());
        Counter fallbackNotApplied = InvocationsTotal.get(
                getMethodTag(bean, "fallback"),
                InvocationResult.VALUE_RETURNED.get(),
                InvocationFallback.NOT_APPLIED.get());
        Counter fallbackNotDefined = InvocationsTotal.get(
                getMethodTag(bean, "fallback"),
                InvocationResult.VALUE_RETURNED.get(),
                InvocationFallback.NOT_DEFINED.get());

        assertThat(fallbackApplied.getCount(), is(0L));
        assertThat(fallbackNotApplied.getCount(), is(0L));
        assertThat(fallbackNotDefined.getCount(), is(0L));

        bean.fallback();

        assertThat(fallbackApplied.getCount(), is(1L));
        assertThat(fallbackNotApplied.getCount(), is(0L));
        assertThat(fallbackNotDefined.getCount(), is(0L));
    }

    @Test
    void testBulkheadMetrics() {
        MetricsBean bean = newBean(MetricsBean.class);
        CompletableFuture<String>[] calls = getAsyncConcurrentCalls(
                () -> bean.concurrent(200), BulkheadBean.TOTAL_CALLS);
        waitFor(calls);

        Gauge<Long> executionsRunning = BulkheadExecutionsRunning.get(
                getMethodTag(bean, "concurrent"));
        assertThat(executionsRunning.getValue(), is(0L));

        Gauge<Long> executionsWaiting = BulkheadExecutionsWaiting.get(
                getMethodTag(bean, "concurrent"));
        assertThat(executionsWaiting.getValue(), is(0L));

        Counter acceptedCallsTotal = BulkheadCallsTotal.get(
                getMethodTag(bean, "concurrent"),
                BulkheadResult.ACCEPTED.get());
        assertThat(acceptedCallsTotal.getCount(), is((long) BulkheadBean.TOTAL_CALLS));

        Counter rejectedCallsTotal = BulkheadCallsTotal.get(
                getMethodTag(bean, "concurrent"),
                BulkheadResult.REJECTED.get());
        assertThat(rejectedCallsTotal.getCount(), is(0L));

        Histogram runningDuration = BulkheadRunningDuration.get(
                getMethodTag(bean, "concurrent"));
        assertThat(runningDuration.getCount(), is(greaterThan(0L)));

        Histogram awaitingDuration = BulkheadWaitingDuration.get(
                getMethodTag(bean, "concurrent"));
        assertThat(awaitingDuration.getCount(), is(greaterThan(0L)));
    }

    @Test
    void testBulkheadMetricsAsync() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        CompletableFuture<String>[] calls = getConcurrentCalls(
                () -> {
                    try {
                        return bean.concurrentAsync(200).get();
                    } catch (Exception e) {
                        return "failure";
                    }
                }, BulkheadBean.TOTAL_CALLS);
        CompletableFuture.allOf(calls).get();

        Gauge<Long> executionsRunning = BulkheadExecutionsRunning.get(
                getMethodTag(bean, "concurrentAsync"));
        assertThat(executionsRunning.getValue(), is(0L));

        Gauge<Long> executionsWaiting = BulkheadExecutionsWaiting.get(
                getMethodTag(bean, "concurrentAsync"));
        assertThat(executionsWaiting.getValue(), is(0L));

        Counter acceptedCallsTotal = BulkheadCallsTotal.get(
                getMethodTag(bean, "concurrentAsync"),
                BulkheadResult.ACCEPTED.get());
        assertThat(acceptedCallsTotal.getCount(), is((long) BulkheadBean.TOTAL_CALLS));

        Counter rejectedCallsTotal = BulkheadCallsTotal.get(
                getMethodTag(bean, "concurrentAsync"),
                BulkheadResult.REJECTED.get());
        assertThat(rejectedCallsTotal.getCount(), is(0L));

        Histogram runningDuration = BulkheadRunningDuration.get(
                getMethodTag(bean, "concurrentAsync"));
        assertThat(runningDuration.getCount(), is(greaterThan(0L)));

        Histogram awaitingDuration = BulkheadWaitingDuration.get(
                getMethodTag(bean, "concurrentAsync"));
        assertThat(awaitingDuration.getCount(), is(greaterThan(0L)));
    }
}
