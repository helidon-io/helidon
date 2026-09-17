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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

public class ThroughputLimitTest {

    @Test
    public void testUnlimited() throws InterruptedException {
        ThroughputLimit limiter = ThroughputLimit.create();
        int concurrency = 5;
        CountDownLatch cdl = new CountDownLatch(1);
        CountDownLatch threadsCdl = new CountDownLatch(concurrency);

        Lock lock = new ReentrantLock();
        List<String> result = new ArrayList<>(concurrency);

        Thread[] threads = new Thread[concurrency];
        for (int i = 0; i < concurrency; i++) {
            int index = i;
            threads[i] = new Thread(() -> {
                try {
                    limiter.call(() -> {
                        threadsCdl.countDown();
                        cdl.await(10, TimeUnit.SECONDS);
                        lock.lock();
                        try {
                            result.add("result_" + index);
                        } finally {
                            lock.unlock();
                        }
                        return null;
                    });
                } catch (Exception e) {
                    threadsCdl.countDown();
                    throw new RuntimeException(e);
                }
            });
        }
        for (Thread thread : threads) {
            thread.start();
        }
        threadsCdl.await();
        cdl.countDown();
        for (Thread thread : threads) {
            thread.join(Duration.ofSeconds(5));
        }
        assertThat(result, hasSize(concurrency));
    }

    @Test
    public void testLimit() throws Exception {
        TestNanoClock clock = new TestNanoClock();

        ThroughputLimit limiter = ThroughputLimit.builder()
                .amount(1)
                .duration(Duration.ofSeconds(1))
                .clock(clock::getNanos)
                .build();

        int concurrency = 5;
        CountDownLatch cdl = new CountDownLatch(1);
        CountDownLatch threadsCdl = new CountDownLatch(concurrency);

        Lock lock = new ReentrantLock();
        List<String> result = new ArrayList<>(concurrency);
        AtomicInteger failures = new AtomicInteger();

        Thread[] threads = new Thread[concurrency];
        for (int i = 0; i < concurrency; i++) {
            int index = i;
            threads[i] = new Thread(() -> {
                try {
                    limiter.call(() -> {
                        threadsCdl.countDown();
                        cdl.await(10, TimeUnit.SECONDS);
                        lock.lock();
                        try {
                            result.add("result_" + index);
                        } finally {
                            lock.unlock();
                        }
                        return null;
                    });
                } catch (LimitException e) {
                    threadsCdl.countDown();
                    failures.incrementAndGet();
                } catch (Exception e) {
                    threadsCdl.countDown();
                    throw new RuntimeException(e);
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }
        // wait for all threads to reach appropriate destination
        threadsCdl.await();
        cdl.countDown();
        for (Thread thread : threads) {
            thread.join(Duration.ofSeconds(5));
        }
        assertThat(failures.get(), is(concurrency - 1));
        assertThat(result.size(), is(1));
    }

    @Test
    public void testSemaphoreReleased() throws Exception {
        TestNanoClock clock = new TestNanoClock();

        Limit limit = ThroughputLimit.builder()
                .amount(50)
                .duration(Duration.ofSeconds(1))
                .clock(clock::getNanos)
                .build();

        for (int i = 0; i < 5000; i++) {
            if ((i % 50) == 0) {
                clock.advance(Duration.ofSeconds(1));
            }
            limit.run(() -> {
            });
        }
    }

    @Test
    public void testSemaphoreReleasedWithQueue() throws Exception {
        TestNanoClock clock = new TestNanoClock();

        Limit limit = ThroughputLimit.builder()
                .amount(40)
                .duration(Duration.ofSeconds(1))
                .queueLength(10)
                .queueTimeout(Duration.ofMillis(100))
                .clock(clock::getNanos)
                .build();

        for (int i = 0; i < 5000; i++) {
            if ((i % 50) == 0) {
                clock.advance(Duration.ofMillis(1250)); // enough time to clear queue and refill bucket
            }
            limit.run(() -> {
            });
        }
    }

    @Test
    public void testSemaphoreReleasedWithToken() {
        TestNanoClock clock = new TestNanoClock();

        Limit limit = ThroughputLimit.builder()
                .amount(5)
                .queueLength(10)
                .queueTimeout(Duration.ofMillis(100))
                .clock(clock::getNanos)
                .build();

        for (int i = 0; i < 5000; i++) {
            if ((i % 5) == 0) {
                clock.advance(Duration.ofSeconds(1));
            }
            LimitAlgorithm.Outcome outcome = limit.tryAcquireOutcome();
            assertThat(outcome.disposition(), is(LimitAlgorithm.Outcome.Disposition.ACCEPTED));
            ((LimitAlgorithm.Outcome.Accepted) outcome).token().success();
        }
    }

    @Test
    public void testDroppedRequestReleasesConcurrentGauge() {
        TestNanoClock clock = new TestNanoClock();

        ThroughputLimit limiter = ThroughputLimit.builder()
                .amount(1)
                .duration(Duration.ofSeconds(1))
                .clock(clock::getNanos)
                .build();

        LimitAlgorithm.Outcome.Accepted accepted =
                (LimitAlgorithm.Outcome.Accepted) limiter.tryAcquireOutcome(true);

        assertThat(limiter.concurrentRequests().get(), is(1));

        accepted.token().dropped();

        assertThat(limiter.concurrentRequests().get(), is(0));
    }

    @Test
    public void testQueuedSubMillisecondRefill() {
        for (RateLimitingAlgorithmType algorithm : RateLimitingAlgorithmType.values()) {
            assertQueuedRefill(algorithm, 1, Duration.ofNanos(500_000), 1);
        }
    }

    @Test
    public void testQueuedFractionalMillisecondRefill() {
        for (RateLimitingAlgorithmType algorithm : RateLimitingAlgorithmType.values()) {
            assertQueuedRefill(algorithm, 1, Duration.ofNanos(1_500_000), 2);
        }
    }

    @ParameterizedTest
    @EnumSource(RateLimitingAlgorithmType.class)
    void testMultipleOperationsPerNanosecond(RateLimitingAlgorithmType algorithm) {
        var clock = new AtomicLong();
        var limiter = ThroughputLimit.builder()
                .amount(200)
                .duration(Duration.ofNanos(100))
                .rateLimitingAlgorithm(algorithm)
                .clock(clock::get)
                .build();

        assertAccepted(limiter, algorithm == RateLimitingAlgorithmType.TOKEN_BUCKET ? 200 : 1);
        assertThat(limiter.tryAcquireOutcome().disposition(), is(LimitAlgorithm.Outcome.Disposition.REJECTED));
        for (int i = 0; i < 10; i++) {
            clock.incrementAndGet();
            assertAccepted(limiter, 2);
            assertThat("Only two operations are earned per nanosecond",
                       limiter.tryAcquireOutcome().disposition(), is(LimitAlgorithm.Outcome.Disposition.REJECTED));
        }
    }

    @ParameterizedTest
    @EnumSource(RateLimitingAlgorithmType.class)
    void testFractionalNanosecondRefill(RateLimitingAlgorithmType algorithm) {
        var clock = new AtomicLong();
        var limiter = ThroughputLimit.builder()
                .amount(5)
                .duration(Duration.ofNanos(3))
                .rateLimitingAlgorithm(algorithm)
                .clock(clock::get)
                .build();

        assertAccepted(limiter, algorithm == RateLimitingAlgorithmType.TOKEN_BUCKET ? 5 : 1);
        for (int cycle = 0; cycle < 10; cycle++) {
            for (int expected : new int[] {1, 2, 2}) {
                clock.incrementAndGet();
                assertAccepted(limiter, expected);
                assertThat("Fractional refill credit must carry into the next nanosecond",
                           limiter.tryAcquireOutcome().disposition(), is(LimitAlgorithm.Outcome.Disposition.REJECTED));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(RateLimitingAlgorithmType.class)
    void testHighRateAfterLongIdle(RateLimitingAlgorithmType algorithm) {
        var clock = new AtomicLong();
        var limiter = ThroughputLimit.builder()
                .amount(200)
                .duration(Duration.ofNanos(100))
                .rateLimitingAlgorithm(algorithm)
                .clock(clock::get)
                .build();

        assertAccepted(limiter, algorithm == RateLimitingAlgorithmType.TOKEN_BUCKET ? 200 : 1);
        clock.set(Long.MAX_VALUE);
        if (algorithm == RateLimitingAlgorithmType.TOKEN_BUCKET) {
            assertAccepted(limiter, 400);
        } else {
            assertAccepted(limiter, 2);
            assertThat("Fixed rate discards idle credit beyond the current nanosecond",
                       limiter.tryAcquireOutcome().disposition(), is(LimitAlgorithm.Outcome.Disposition.REJECTED));
        }
    }

    @ParameterizedTest
    @EnumSource(RateLimitingAlgorithmType.class)
    void testHighRateAcrossClockWrap(RateLimitingAlgorithmType algorithm) {
        var clock = new AtomicLong(Long.MAX_VALUE);
        var limiter = ThroughputLimit.builder()
                .amount(200)
                .duration(Duration.ofNanos(100))
                .rateLimitingAlgorithm(algorithm)
                .clock(clock::get)
                .build();

        assertAccepted(limiter, algorithm == RateLimitingAlgorithmType.TOKEN_BUCKET ? 200 : 1);
        clock.incrementAndGet();
        assertAccepted(limiter, 2);
        assertThat(limiter.tryAcquireOutcome().disposition(), is(LimitAlgorithm.Outcome.Disposition.REJECTED));
    }

    @Test
    void testTokenBucketAfterLongIdle() {
        var clock = new AtomicLong();
        var limiter = ThroughputLimit.builder()
                .amount(2)
                .duration(Duration.ofNanos(2))
                .clock(clock::get)
                .build();

        assertAccepted(limiter, 2);
        clock.set(Long.MAX_VALUE);
        assertAccepted(limiter, 20);
    }

    @ParameterizedTest
    @EnumSource(RateLimitingAlgorithmType.class)
    void testQueuedHighRateRefill(RateLimitingAlgorithmType algorithm) {
        assertQueuedRefill(algorithm, 200, Duration.ofNanos(100), 1);
    }

    @Test
    void testConcurrentHighRateRefillPreservesCapacity() throws Exception {
        var clock = new AtomicLong();
        var maximumPermits = new AtomicInteger();
        var semaphore = new Semaphore(0) {
            @Override
            public void release(int permits) {
                super.release(permits);
                maximumPermits.accumulateAndGet(availablePermits(), Math::max);
            }
        };
        var limiter = ThroughputLimit.builder()
                .amount(5)
                .duration(Duration.ofNanos(3))
                .semaphore(semaphore)
                .clock(clock::get)
                .build();
        clock.set(Long.MAX_VALUE);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        List<Future<?>> requests = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                requests.add(executor.submit(() -> {
                    assertThat("Concurrent requests did not start", start.await(5, TimeUnit.SECONDS), is(true));
                    for (int request = 0; request < 1000; request++) {
                        if (limiter.tryAcquireOutcome() instanceof LimitAlgorithm.Outcome.Accepted accepted) {
                            accepted.token().success();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> request : requests) {
                request.get(10, TimeUnit.SECONDS);
            }
            assertThat("The bucket was never refilled", maximumPermits.get(), greaterThan(0));
            assertThat("Concurrent refills must not exceed bucket capacity", maximumPermits.get(), lessThanOrEqualTo(5));
            assertThat(limiter.concurrentRequests().get(), is(0));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat("Concurrent requests did not terminate", executor.awaitTermination(5, TimeUnit.SECONDS), is(true));
        }
    }

    private void assertQueuedRefill(RateLimitingAlgorithmType algorithm,
                                    int amount,
                                    Duration interval,
                                    long expectedWaitMillis) {
        TestNanoClock clock = new TestNanoClock();
        List<Long> waits = new ArrayList<>();
        Semaphore semaphore = new Semaphore(0) {
            @Override
            public boolean tryAcquire(long timeout, TimeUnit unit) {
                waits.add(unit.toMillis(timeout));
                if (super.tryAcquire()) {
                    return true;
                }
                clock.advance(Duration.ofNanos(unit.toNanos(timeout)));
                return false;
            }
        };
        ThroughputLimit limiter = ThroughputLimit.builder()
                .amount(amount)
                .duration(interval)
                .rateLimitingAlgorithm(algorithm)
                .semaphore(semaphore)
                .clock(clock::getNanos)
                .queueLength(1)
                .queueTimeout(Duration.ofSeconds(1))
                .build();

        LimitAlgorithm.Outcome outcome = limiter.tryAcquireOutcome(true);

        assertThat(algorithm + " refill waits", waits, contains(expectedWaitMillis, expectedWaitMillis));
        assertThat(algorithm + " queued request", outcome.disposition(), is(LimitAlgorithm.Outcome.Disposition.ACCEPTED));
        assertThat(outcome.timing(), is(LimitAlgorithm.Outcome.Timing.DEFERRED));
        ((LimitAlgorithm.Outcome.Accepted) outcome).token().success();
    }

    private void assertAccepted(ThroughputLimit limiter, int count) {
        for (int i = 0; i < count; i++) {
            var outcome = limiter.tryAcquireOutcome();
            assertThat("Admission " + (i + 1) + " of " + count,
                       outcome.disposition(), is(LimitAlgorithm.Outcome.Disposition.ACCEPTED));
            ((LimitAlgorithm.Outcome.Accepted) outcome).token().success();
        }
    }

    private static class TestNanoClock {
        private final AtomicLong nanos = new AtomicLong(System.nanoTime());

        public long getNanos() {
            return nanos.get();
        }

        public void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }
}
