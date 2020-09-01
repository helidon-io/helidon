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

import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.greaterThan;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CALLS_FAILED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CALLS_PREVENTED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CALLS_SUCCEEDED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_CLOSED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_HALF_OPEN_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_OPENED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BREAKER_OPEN_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_CALLS_ACCEPTED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_CALLS_REJECTED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_CONCURRENT_EXECUTIONS;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.BULKHEAD_EXECUTION_DURATION;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.FALLBACK_CALLS_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.INVOCATIONS_FAILED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.INVOCATIONS_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.RETRY_CALLS_FAILED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.RETRY_CALLS_SUCCEEDED_NOT_RETRIED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.RETRY_CALLS_SUCCEEDED_RETRIED_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.RETRY_RETRIES_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.TIMEOUT_CALLS_NOT_TIMED_OUT_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.TIMEOUT_CALLS_TIMED_OUT_TOTAL;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.TIMEOUT_EXECUTION_DURATION;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.enabled;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.getCounter;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.getGauge;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.getHistogram;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.getMetricRegistry;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Class MetricsTest.
 */
public class MetricsTest extends FaultToleranceTest {

    @Test
    public void testEnable() {
        assertThat(enabled(), is(true));
    }

    @Test
    public void testInjectCounter() {
        MetricsBean bean = newBean(MetricsBean.class);
        assertThat(bean, notNullValue());
        bean.getCounter().inc();
        assertThat(bean.getCounter().getCount(), is(1L));
    }

    @Test
    public void testInjectCounterProgrammatically() {
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
    public void testGlobalCountersSuccess() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        bean.retryOne(5);
        assertThat(getCounter(bean, "retryOne",
                                   INVOCATIONS_TOTAL, int.class),
                   is(1L));
        assertThat(getCounter(bean, "retryOne",
                                   INVOCATIONS_FAILED_TOTAL, int.class),
                   is(0L));
    }

    @Test
    public void testGlobalCountersFailure() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        try {
            bean.retryTwo(10);
        } catch (Exception e) {
            // falls through
        }
        assertThat(getCounter(bean, "retryTwo",
                              INVOCATIONS_TOTAL, int.class),
                   is(1L));
        assertThat(getCounter(bean, "retryTwo",
                                   INVOCATIONS_FAILED_TOTAL, int.class),
                   is(1L));
    }

    @Test
    public void testRetryCounters() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        bean.retryThree(5);
        assertThat(getCounter(bean, "retryThree",
                                   RETRY_CALLS_SUCCEEDED_NOT_RETRIED_TOTAL, int.class),
                   is(0L));
        assertThat(getCounter(bean, "retryThree",
                                   RETRY_CALLS_SUCCEEDED_RETRIED_TOTAL, int.class),
                   is(1L));
        assertThat(getCounter(bean, "retryThree",
                                   RETRY_CALLS_FAILED_TOTAL, int.class),
                   is(0L));
        assertThat(getCounter(bean, "retryThree",
                                   RETRY_RETRIES_TOTAL, int.class),
                   is(5L));
    }

    @Test
    public void testRetryCountersFailure() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        try {
            bean.retryFour(10);
        } catch (Exception e) {
            // falls through
        }
        assertThat(getCounter(bean, "retryFour",
                                   RETRY_CALLS_SUCCEEDED_NOT_RETRIED_TOTAL, int.class),
                   is(0L));
        assertThat(getCounter(bean, "retryFour",
                                   RETRY_CALLS_SUCCEEDED_RETRIED_TOTAL, int.class),
                   is(0L));
        assertThat(getCounter(bean, "retryFour",
                                   RETRY_CALLS_FAILED_TOTAL, int.class),
                   is(1L));
        assertThat(getCounter(bean, "retryFour",
                                   RETRY_RETRIES_TOTAL, int.class),
                   is(5L));
    }

    @Test
    public void testRetryCountersSuccess() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        bean.retryFive(0);
        assertThat(getCounter(bean, "retryFive",
                                   RETRY_CALLS_SUCCEEDED_NOT_RETRIED_TOTAL, int.class),
                   is(1L));
        assertThat(getCounter(bean, "retryFive",
                                   RETRY_CALLS_SUCCEEDED_RETRIED_TOTAL, int.class),
                   is(0L));
        assertThat(getCounter(bean, "retryFive",
                                   RETRY_CALLS_FAILED_TOTAL, int.class),
                   is(0L));
        assertThat(getCounter(bean, "retryFive",
                                   RETRY_RETRIES_TOTAL, int.class),
                   is(0L));
    }

    @Test
    public void testTimeoutSuccess() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        bean.noTimeout();
        assertThat(getHistogram(bean, "noTimeout",
                                     TIMEOUT_EXECUTION_DURATION).getCount(),
                   is(1L));
        assertThat(getCounter(bean, "noTimeout",
                                   TIMEOUT_CALLS_NOT_TIMED_OUT_TOTAL),
                   is(1L));
        assertThat(getCounter(bean, "noTimeout",
                                   TIMEOUT_CALLS_TIMED_OUT_TOTAL),
                   is(0L));
    }

    @Test
    public void testTimeoutFailure() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        try {
            bean.forceTimeout();
        } catch (Exception e) {
            // falls through
        }
        assertThat(getHistogram(bean, "forceTimeout",
                                     TIMEOUT_EXECUTION_DURATION).getCount(),
                   is(1L));
        assertThat(getCounter(bean, "forceTimeout",
                                   TIMEOUT_CALLS_NOT_TIMED_OUT_TOTAL),
                   is(0L));
        assertThat(getCounter(bean, "forceTimeout",
                                   TIMEOUT_CALLS_TIMED_OUT_TOTAL),
                   is(1L));
    }

    @Test
    public void testBreakerTrip() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);

        for (int i = 0; i < CircuitBreakerBean.REQUEST_VOLUME_THRESHOLD ; i++) {
            assertThrows(RuntimeException.class, () -> bean.exerciseBreaker(false));
        }
        assertThrows(CircuitBreakerOpenException.class, () -> bean.exerciseBreaker(false));

        assertThat(getCounter(bean, "exerciseBreaker",
                BREAKER_OPENED_TOTAL, boolean.class),
                   is(1L));
        assertThat(getCounter(bean, "exerciseBreaker",
                                   BREAKER_CALLS_SUCCEEDED_TOTAL, boolean.class),
                   is(0L));
        assertThat(getCounter(bean, "exerciseBreaker",
                                BREAKER_CALLS_FAILED_TOTAL, boolean.class),
                   is((long) CircuitBreakerBean.REQUEST_VOLUME_THRESHOLD));
        assertThat(getCounter(bean, "exerciseBreaker",
                                   BREAKER_CALLS_PREVENTED_TOTAL, boolean.class),
                   is(1L));
    }

    @Test
    public void testBreakerGauges() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        for (int i = 0; i < CircuitBreakerBean.REQUEST_VOLUME_THRESHOLD - 1; i++) {
            assertThrows(RuntimeException.class, () -> bean.exerciseGauges(false));

            assertThat(getGauge(bean, "exerciseGauges",
                                BREAKER_CLOSED_TOTAL, boolean.class).getValue(),
                       is(not(0L)));
            assertThat(getGauge(bean, "exerciseGauges",
                                BREAKER_OPEN_TOTAL, boolean.class).getValue(),
                       is(0L));
            assertThat(getGauge(bean, "exerciseGauges",
                                BREAKER_HALF_OPEN_TOTAL, boolean.class).getValue(),
                       is(0L));

        }
        assertThrows(RuntimeException.class, () -> bean.exerciseGauges(false));
        assertThrows(CircuitBreakerOpenException.class, () -> bean.exerciseGauges(false));

        assertThat(getGauge(bean, "exerciseGauges",
                            BREAKER_CLOSED_TOTAL, boolean.class).getValue(),
                   is(not(0L)));
        assertThat(getGauge(bean, "exerciseGauges",
                            BREAKER_OPEN_TOTAL, boolean.class).getValue(),
                   is(not(0L)));
        assertThat(getGauge(bean, "exerciseGauges",
                            BREAKER_HALF_OPEN_TOTAL, boolean.class).getValue(),
                   is(0L));
    }

    @Test
    public void testBreakerExceptionCounters() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);

        // First failure
        assertThrows(MetricsBean.TestException.class, () -> bean.exerciseBreakerException(false));  // failure
        assertThat(getCounter(bean, "exerciseBreakerException",
                BREAKER_CALLS_SUCCEEDED_TOTAL, boolean.class),
                is(0L));
        assertThat(getCounter(bean, "exerciseBreakerException",
                BREAKER_CALLS_FAILED_TOTAL, boolean.class),
                is(1L));
        assertThat(getCounter(bean, "exerciseBreakerException",
                BREAKER_OPENED_TOTAL, boolean.class),
                is(0L));

        // Second failure
        assertThrows(MetricsBean.TestException.class, () -> bean.exerciseBreakerException(false));  // failure
        assertThat(getCounter(bean, "exerciseBreakerException",
                BREAKER_CALLS_SUCCEEDED_TOTAL, boolean.class),
                is(0L));
        assertThat(getCounter(bean, "exerciseBreakerException",
                BREAKER_CALLS_FAILED_TOTAL, boolean.class),
                is(2L));
        assertThat(getCounter(bean, "exerciseBreakerException",
                BREAKER_OPENED_TOTAL, boolean.class),
                is(1L));

        assertThrows(Exception.class, () -> bean.exerciseBreakerException(true));  // failure
        assertThat(getCounter(bean, "exerciseBreakerException",
                BREAKER_CALLS_SUCCEEDED_TOTAL, boolean.class),
                is(0L));

        // Sleep longer than circuit breaker delay
        Thread.sleep(1500);

        // Following calls should succeed due to FailOn
        for (int i = 0; i < 2; i++) {
            try {
                bean.exerciseBreakerException(true);    // success
            } catch (RuntimeException e) {
                // expected
            }
        }

        // Check counters after successful calls
        assertThat(getCounter(bean, "exerciseBreakerException",
                BREAKER_CALLS_SUCCEEDED_TOTAL, boolean.class),
                is(2L));

        try {
            bean.exerciseBreakerException(true);    // success
        } catch (RuntimeException e) {
            // expected
        }

        // Check counters after successful calls
        assertThat(getCounter(bean, "exerciseBreakerException",
                BREAKER_CALLS_SUCCEEDED_TOTAL, boolean.class),
                is(3L));
    }

    @Test
    public void testFallbackMetrics() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        assertThat(getCounter(bean, "fallback", FALLBACK_CALLS_TOTAL), is(0L));
        bean.fallback();
        assertThat(getCounter(bean, "fallback", FALLBACK_CALLS_TOTAL), is(1L));
    }

    @Test
    public void testBulkheadMetrics() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        CompletableFuture<String>[] calls = getAsyncConcurrentCalls(
            () -> bean.concurrent(200), BulkheadBean.TOTAL_CALLS);
        waitFor(calls);
        assertThat(getGauge(bean, "concurrent",
                              BULKHEAD_CONCURRENT_EXECUTIONS, long.class).getValue(),
                   is(0L));
        assertThat(getCounter(bean, "concurrent",
                                BULKHEAD_CALLS_ACCEPTED_TOTAL, long.class),
                   is((long) BulkheadBean.TOTAL_CALLS));
        assertThat(getCounter(bean, "concurrent",
                                BULKHEAD_CALLS_REJECTED_TOTAL, long.class),
                   is(0L));
        assertThat(getHistogram(bean, "concurrent",
                                  BULKHEAD_EXECUTION_DURATION, long.class).getCount(),
                   is(greaterThan(0L)));
    }

    @Test
    public void testBulkheadMetricsAsync() throws Exception {
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
        assertThat(getHistogram(bean, "concurrentAsync",
                                  BULKHEAD_EXECUTION_DURATION, long.class).getCount(),
                   is(greaterThan(0L)));
    }
}
