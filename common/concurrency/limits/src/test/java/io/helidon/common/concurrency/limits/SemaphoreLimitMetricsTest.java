/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.common.concurrency.limits;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome.Disposition.ACCEPTED;
import static io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome.Disposition.REJECTED;
import static io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome.Timing.DEFERRED;
import static io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome.Timing.IMMEDIATE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class SemaphoreLimitMetricsTest {

    @ParameterizedTest
    @EnumSource(LimitType.class)
    void testQueueTimeoutIsCounted(LimitType type) {
        try (var fixture = new MetricLimit(type, 0, 1, Duration.ofMillis(1))) {
            assertThat(fixture.rejectedRequests.value().intValue(), is(0));
            for (int expected = 1; expected <= 2; expected++) {
                var outcome = fixture.limit.tryAcquireOutcome(true);
                assertThat(outcome.disposition(), is(REJECTED));
                assertThat(outcome.timing(), is(DEFERRED));
                assertThat(fixture.rejectedRequests.value().intValue(), is(expected));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(LimitType.class)
    void testFullQueueRejectionIsCounted(LimitType type) throws Exception {
        try (var fixture = new MetricLimit(type, 0, 1, Duration.ofSeconds(30));
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var queued = executor.submit(() -> {
                fixture.semaphore.acquire();
                return null;
            });
            try {
                awaitQueuedThread(fixture.semaphore);
                var outcome = fixture.limit.tryAcquireOutcome(true);
                assertThat(outcome.disposition(), is(REJECTED));
                assertThat(fixture.rejectedRequests.value().intValue(), is(1));
            } finally {
                queued.cancel(true);
                executor.shutdownNow();
                assertThat("queued thread terminated", executor.awaitTermination(5, TimeUnit.SECONDS), is(true));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(LimitType.class)
    void testNonWaitingRejectionIsCounted(LimitType type) {
        try (var fixture = new MetricLimit(type, 0, 1, Duration.ofSeconds(30))) {
            var outcome = fixture.limit.tryAcquireOutcome(false);
            assertThat(outcome.disposition(), is(REJECTED));
            assertThat(outcome.timing(), is(IMMEDIATE));
            assertThat(fixture.rejectedRequests.value().intValue(), is(1));
        }
    }

    @ParameterizedTest
    @EnumSource(LimitType.class)
    void testRejectionWithoutQueueIsCounted(LimitType type) {
        try (var fixture = new MetricLimit(type, 0, 0, Duration.ofSeconds(30))) {
            var outcome = fixture.limit.tryAcquireOutcome(true);
            assertThat(outcome.disposition(), is(REJECTED));
            assertThat(outcome.timing(), is(IMMEDIATE));
            assertThat(fixture.rejectedRequests.value().intValue(), is(1));
        }
    }

    @ParameterizedTest
    @EnumSource(LimitType.class)
    void testImmediateAcceptanceIsNotCounted(LimitType type) {
        try (var fixture = new MetricLimit(type, 1, 1, Duration.ofSeconds(30))) {
            var outcome = fixture.limit.tryAcquireOutcome(true);
            assertThat(outcome.disposition(), is(ACCEPTED));
            assertThat(outcome.timing(), is(IMMEDIATE));
            ((LimitAlgorithm.Outcome.Accepted) outcome).token().success();
            assertThat(fixture.rejectedRequests.value().intValue(), is(0));
        }
    }

    @ParameterizedTest
    @EnumSource(LimitType.class)
    void testQueuedAcceptanceIsNotCounted(LimitType type) throws Exception {
        try (var fixture = new MetricLimit(type, 0, 1, Duration.ofSeconds(30));
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var queued = executor.submit(() -> fixture.limit.tryAcquireOutcome(true));
            try {
                awaitQueuedThread(fixture.semaphore);
                fixture.semaphore.release();
                var outcome = queued.get(5, TimeUnit.SECONDS);
                assertThat(outcome.disposition(), is(ACCEPTED));
                assertThat(outcome.timing(), is(DEFERRED));
                ((LimitAlgorithm.Outcome.Accepted) outcome).token().success();
                assertThat(fixture.rejectedRequests.value().intValue(), is(0));
            } finally {
                queued.cancel(true);
                executor.shutdownNow();
                assertThat("queued thread terminated", executor.awaitTermination(5, TimeUnit.SECONDS), is(true));
            }
        }
    }

    private static void awaitQueuedThread(Semaphore semaphore) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!semaphore.hasQueuedThreads() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(1);
        }
        assertThat("request queued on semaphore", semaphore.getQueueLength(), is(1));
    }

    enum LimitType {
        FIXED,
        THROUGHPUT
    }

    private static class MetricLimit implements AutoCloseable {
        private final MeterRegistry registry = Metrics.globalRegistry();
        private final String name = "limit_" + UUID.randomUUID();
        private final Semaphore semaphore;
        private final Limit limit;
        private final Gauge<?> rejectedRequests;

        private MetricLimit(LimitType type, int permits, int queueLength, Duration queueTimeout) {
            semaphore = new Semaphore(permits);
            // Freeze throughput refill so only an explicit release can make a permit available.
            limit = switch (type) {
            case FIXED -> FixedLimit.builder()
                    .name(name)
                    .semaphore(semaphore)
                    .queueLength(queueLength)
                    .queueTimeout(queueTimeout)
                    .clock(() -> 0L)
                    .enableMetrics(true)
                    .build();
            case THROUGHPUT -> ThroughputLimit.builder()
                    .name(name)
                    .amount(1)
                    .duration(Duration.ofSeconds(30))
                    .semaphore(semaphore)
                    .queueLength(queueLength)
                    .queueTimeout(queueTimeout)
                    .clock(() -> 0L)
                    .enableMetrics(true)
                    .build();
            };
            limit.init("@default");
            rejectedRequests = registry.gauge(name + "_rejected_requests", List.of()).orElseThrow();
        }

        @Override
        public void close() {
            registry.meters(meter -> meter.id().name().startsWith(name)).forEach(registry::remove);
        }
    }
}
