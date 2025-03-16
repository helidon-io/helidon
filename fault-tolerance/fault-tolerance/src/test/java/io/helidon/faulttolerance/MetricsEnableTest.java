/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.faulttolerance;

import java.time.Duration;
import java.util.NoSuchElementException;

import io.helidon.config.Config;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Tag;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.faulttolerance.Bulkhead.FT_BULKHEAD_CALLS_TOTAL;
import static io.helidon.faulttolerance.CircuitBreaker.FT_CIRCUITBREAKER_CALLS_TOTAL;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@Testing.Test
class MetricsEnableTest extends CircuitBreakerBaseTest {

    private static final long WAIT_TIMEOUT_MILLIS = 5000;

    @BeforeAll
    static void setupTest() {
        FaultTolerance.config(Config.empty());      // empty config
    }

    @Test
    void testEnableBulkhead() throws InterruptedException {
        Bulkhead bulkhead = BulkheadConfig.builder()
                .enableMetrics(true)    // metrics enabled
                .build();

        BulkheadBaseTest.Task inProgress = new BulkheadBaseTest.Task(0);
        Async.invokeStatic(() -> bulkhead.invoke(inProgress::run));
        if (!inProgress.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task inProgress not started");
        }

        Tag nameTag = Tag.create("name", bulkhead.name());
        Counter callsTotal = MetricsUtils.counter(FT_BULKHEAD_CALLS_TOTAL, nameTag);
        assertThat(callsTotal.count(), is(1L));
    }

    @Test
    void testDisableBulkhead() throws InterruptedException {
        Bulkhead bulkhead = BulkheadConfig.builder()
                .enableMetrics(false)       // metrics disabled
                .build();

        BulkheadBaseTest.Task inProgress = new BulkheadBaseTest.Task(0);
        Async.invokeStatic(() -> bulkhead.invoke(inProgress::run));
        if (!inProgress.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task inProgress not started");
        }

        Tag nameTag = Tag.create("name", bulkhead.name());
        assertThrows(NoSuchElementException.class,
                     () -> MetricsUtils.counter(FT_BULKHEAD_CALLS_TOTAL, nameTag));
    }

    @Test
    void testEnableCircuitBreaker() {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .enableMetrics(true)        // metrics enabled
                .delay(Duration.ofMillis(200))
                .build();

        good(breaker);
        good(breaker);

        Counter callsCounter = MetricsUtils.counter(FT_CIRCUITBREAKER_CALLS_TOTAL, Tag.create("name", breaker.name()));
        assertThat(callsCounter.count(), Matchers.is(2L));
    }

    @Test
    void testDisableCircuitBreaker() {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .enableMetrics(false)        // metrics disabled
                .delay(Duration.ofMillis(200))
                .build();

        good(breaker);
        good(breaker);

        assertThrows(NoSuchElementException.class,
                     () -> MetricsUtils.counter(FT_CIRCUITBREAKER_CALLS_TOTAL, Tag.create("name", breaker.name())));
    }

    @Test
    void testEnableRetry() {
        Retry retry = Retry.builder()
                .enableMetrics(true)        // metrics enabled
                .build();

        retry.invoke(() -> 0);

        Tag nameTag = Tag.create("name", retry.name());
        Counter callsCounter = MetricsUtils.counter(Retry.FT_RETRY_CALLS_TOTAL, nameTag);
        assertThat(callsCounter.count(), is(1L));
    }

    @Test
    void testDisableRetry() {
        Retry retry = Retry.builder()
                .enableMetrics(false)        // metrics disabled
                .build();

        retry.invoke(() -> 0);

        Tag nameTag = Tag.create("name", retry.name());
        assertThrows(NoSuchElementException.class,
                     () -> MetricsUtils.counter(Retry.FT_RETRY_CALLS_TOTAL, nameTag));
    }

    @Test
    void testEnableTimeout() {
        Timeout timeout = Timeout.builder()
                .enableMetrics(true)        // metrics enabled
                .build();

        timeout.invoke(() -> 0);

        Tag nameTag = Tag.create("name", timeout.name());
        Counter callsCounter = MetricsUtils.counter(Timeout.FT_TIMEOUT_CALLS_TOTAL, nameTag);
        assertThat(callsCounter.count(), is(1L));
    }

    @Test
    void testDisableTimeout() {
        Timeout timeout = Timeout.builder()
                .enableMetrics(false)        // metrics disabled
                .build();

        timeout.invoke(() -> 0);

        Tag nameTag = Tag.create("name", timeout.name());
        assertThrows(NoSuchElementException.class,
                     () -> MetricsUtils.counter(Timeout.FT_TIMEOUT_CALLS_TOTAL, nameTag));
    }
}
