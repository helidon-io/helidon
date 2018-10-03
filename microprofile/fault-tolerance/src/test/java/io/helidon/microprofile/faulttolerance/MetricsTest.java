/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Class MetricsTest.
 */
public class MetricsTest extends FaultToleranceTest {

    @Test
    public void testEnable() {
        assertTrue(enabled());
    }

    @Test
    public void testInjectCounter() {
        MetricsBean bean = newBean(MetricsBean.class);
        assertNotNull(bean);
        bean.getCounter().inc();
        assertEquals(1L, bean.getCounter().getCount());
    }

    @Test
    public void testInjectCounterProgrammatically() {
        MetricRegistry metricRegistry = getMetricRegistry();
        metricRegistry.counter(new Metadata("dcounter",
                                            "",
                                            "",
                                            MetricType.COUNTER,
                                            MetricUnits.NONE));
        metricRegistry.counter("dcounter").inc();
        assertEquals(1L, metricRegistry.counter("dcounter").getCount());
    }

    @Test
    public void testGlobalCountersSuccess() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        bean.retryOne(5);
        assertEquals(1, getCounter(bean, "retryOne",
                                   INVOCATIONS_TOTAL, int.class));
        assertEquals(5, getCounter(bean, "retryOne",
                                   INVOCATIONS_FAILED_TOTAL, int.class));
    }

    @Test
    public void testGlobalCountersFailure() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        try {
            bean.retryTwo(10);
        } catch (Exception e) {
            // falls through
        }
        assertEquals(0, getCounter(bean, "retryTwo",
                                   INVOCATIONS_TOTAL, int.class));
        assertEquals(6, getCounter(bean, "retryTwo",
                                   INVOCATIONS_FAILED_TOTAL, int.class));
    }

    @Test
    public void testRetryCounters() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        bean.retryThree(5);
        assertEquals(0, getCounter(bean, "retryThree",
                                   RETRY_CALLS_SUCCEEDED_NOT_RETRIED_TOTAL, int.class));
        assertEquals(1, getCounter(bean, "retryThree",
                                   RETRY_CALLS_SUCCEEDED_RETRIED_TOTAL, int.class));
        assertEquals(4, getCounter(bean, "retryThree",
                                   RETRY_CALLS_FAILED_TOTAL, int.class));
        assertEquals(5, getCounter(bean, "retryThree",
                                   RETRY_RETRIES_TOTAL, int.class));
    }

    @Test
    public void testRetryCountersFailure() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        try {
            bean.retryFour(10);
        } catch (Exception e) {
            // falls through
        }
        assertEquals(0, getCounter(bean, "retryFour",
                                   RETRY_CALLS_SUCCEEDED_NOT_RETRIED_TOTAL, int.class));
        assertEquals(0, getCounter(bean, "retryFour",
                                   RETRY_CALLS_SUCCEEDED_RETRIED_TOTAL, int.class));
        assertEquals(5, getCounter(bean, "retryFour",
                                   RETRY_CALLS_FAILED_TOTAL, int.class));
        assertEquals(5, getCounter(bean, "retryFour",
                                   RETRY_RETRIES_TOTAL, int.class));
    }

    @Test
    public void testRetryCountersSuccess() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        bean.retryFive(0);
        assertEquals(1, getCounter(bean, "retryFive",
                                   RETRY_CALLS_SUCCEEDED_NOT_RETRIED_TOTAL, int.class));
        assertEquals(0, getCounter(bean, "retryFive",
                                   RETRY_CALLS_SUCCEEDED_RETRIED_TOTAL, int.class));
        assertEquals(0, getCounter(bean, "retryFive",
                                   RETRY_CALLS_FAILED_TOTAL, int.class));
        assertEquals(0, getCounter(bean, "retryFive",
                                   RETRY_RETRIES_TOTAL, int.class));
    }

    @Test
    public void testTimeoutSuccess() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        bean.noTimeout();
        assertEquals(1, getHistogram(bean, "noTimeout",
                                     TIMEOUT_EXECUTION_DURATION).getCount());
        assertEquals(1, getCounter(bean, "noTimeout",
                                   TIMEOUT_CALLS_NOT_TIMED_OUT_TOTAL));
        assertEquals(0, getCounter(bean, "noTimeout",
                                   TIMEOUT_CALLS_TIMED_OUT_TOTAL));
    }

    @Test
    public void testTimeoutFailure() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        try {
            bean.forceTimeout();
        } catch (Exception e) {
            // falls through
        }
        assertEquals(1, getHistogram(bean, "forceTimeout",
                                     TIMEOUT_EXECUTION_DURATION).getCount());
        assertEquals(0, getCounter(bean, "forceTimeout",
                                   TIMEOUT_CALLS_NOT_TIMED_OUT_TOTAL));
        assertEquals(1, getCounter(bean, "forceTimeout",
                                   TIMEOUT_CALLS_TIMED_OUT_TOTAL));
    }

    @Test
    public void testBreakerTrip() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        for (int i = 0; i < CircuitBreakerBean.REQUEST_VOLUME_THRESHOLD; i++) {
            assertThrows(RuntimeException.class, () -> bean.exerciseBreaker(false));
        }
        Thread.sleep(1000);
        assertThrows(CircuitBreakerOpenException.class, () -> bean.exerciseBreaker(false));

        assertEquals(1, getCounter(bean, "exerciseBreaker",
                                   BREAKER_OPENED_TOTAL, boolean.class));
        assertEquals(0, getCounter(bean, "exerciseBreaker",
                                   BREAKER_CALLS_SUCCEEDED_TOTAL, boolean.class));
        assertEquals(CircuitBreakerBean.REQUEST_VOLUME_THRESHOLD,
                     getCounter(bean, "exerciseBreaker",
                                BREAKER_CALLS_FAILED_TOTAL, boolean.class));
        assertEquals(1, getCounter(bean, "exerciseBreaker",
                                   BREAKER_CALLS_PREVENTED_TOTAL, boolean.class));
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
        Thread.sleep(1000);
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
    public void testFallbackMetrics() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        assertEquals(0, getCounter(bean, "fallback", FALLBACK_CALLS_TOTAL));
        bean.fallback();
        assertEquals(1, getCounter(bean, "fallback", FALLBACK_CALLS_TOTAL));
    }

    @Test
    public void testBulkheadMetrics() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        CompletableFuture<String>[] calls = getConcurrentCalls(
            () -> bean.concurrent(100), BulkheadBean.MAX_CONCURRENT_CALLS);
        CompletableFuture.allOf(calls).get();
        assertEquals(0,
                     getGauge(bean, "concurrent",
                              BULKHEAD_CONCURRENT_EXECUTIONS, long.class).getValue());
        assertEquals(BulkheadBean.MAX_CONCURRENT_CALLS,
                     getCounter(bean, "concurrent",
                                BULKHEAD_CALLS_ACCEPTED_TOTAL, long.class));
        assertEquals(0,
                     getCounter(bean, "concurrent",
                                BULKHEAD_CALLS_REJECTED_TOTAL, long.class));
        assertEquals(BulkheadBean.MAX_CONCURRENT_CALLS,
                     getHistogram(bean, "concurrent",
                                  BULKHEAD_EXECUTION_DURATION, long.class).getCount());
    }

    @Test
    public void testBulkheadMetricsAsync() throws Exception {
        MetricsBean bean = newBean(MetricsBean.class);
        CompletableFuture<String>[] calls = getConcurrentCalls(
            () -> {
                try {
                    return bean.concurrentAsync(100).get();
                } catch (Exception e) {
                    return "failure";
                }
            }, BulkheadBean.MAX_CONCURRENT_CALLS);
        CompletableFuture.allOf(calls).get();
        assertEquals(BulkheadBean.MAX_CONCURRENT_CALLS,
                     getHistogram(bean, "concurrentAsync",
                                  BULKHEAD_EXECUTION_DURATION, long.class).getCount());
    }
}
