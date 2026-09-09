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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ThroughputLimitTest {
    private static final int TEST_TIMEOUT_SECONDS = 10;
    private static final int RELEASE_TIMEOUT_SECONDS = 30;

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
            if ((i % 40) == 0) {
                clock.advance(Duration.ofSeconds(1));
            }
            limit.run(() -> {
            });
        }
    }

    @Test
    void tokenBucketCapsPermitsAfterLongIdle() {
        TestNanoClock clock = new TestNanoClock();

        Limit limit = ThroughputLimit.builder()
                .amount(5)
                .duration(Duration.ofSeconds(1))
                .clock(clock::getNanos)
                .build();

        clock.advance(Duration.ofSeconds(60));

        assertThat(acceptedBeforeRejection(limit, 6), is(5));

        clock.advance(Duration.ofSeconds(60));

        assertThat(acceptedBeforeRejection(limit, 6), is(5));
    }

    @Test
    void tokenBucketHandlesMoreThanIntegerMaxElapsedTokens() {
        TestNanoClock clock = new TestNanoClock();

        Limit limit = ThroughputLimit.builder()
                .amount(1)
                .duration(Duration.ofNanos(1))
                .clock(clock::getNanos)
                .build();

        assertThat(acceptedBeforeRejection(limit, 2), is(1));

        clock.advance(Duration.ofNanos(Integer.MAX_VALUE + 1L));

        assertThat(acceptedBeforeRejection(limit, 2), is(1));
    }

    @Test
    void tokenBucketPreservesPartialTokenInterval() {
        TestNanoClock clock = new TestNanoClock();

        Limit limit = ThroughputLimit.builder()
                .amount(4)
                .duration(Duration.ofSeconds(1))
                .clock(clock::getNanos)
                .build();

        assertThat(acceptedBeforeRejection(limit, 5), is(4));

        clock.advance(Duration.ofMillis(375));
        assertThat(acceptedBeforeRejection(limit, 2), is(1));

        clock.advance(Duration.ofMillis(125));
        assertThat(acceptedBeforeRejection(limit, 2), is(1));
    }

    @Test
    void tokenBucketRefillDoesNotBlockCompetingCallers() throws Exception {
        int amount = 5;
        TestNanoClock clock = new TestNanoClock();
        BlockingReleaseSemaphore semaphore = new BlockingReleaseSemaphore();

        Limit limit = ThroughputLimit.builder()
                .amount(amount)
                .duration(Duration.ofSeconds(1))
                .queueLength(0)
                .semaphore(semaphore)
                .clock(clock::getNanos)
                .build();

        clock.advance(Duration.ofSeconds(1));
        semaphore.blockNextRelease();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<LimitAlgorithm.Outcome> refilling = executor.submit(() -> limit.tryAcquireOutcome(false));
            assertThat("Timed out waiting for permit publication to pause",
                       semaphore.awaitBlockedRelease(), is(true));

            Future<LimitAlgorithm.Outcome> competing = executor.submit(() -> limit.tryAcquireOutcome(false));
            try {
                assertThat(competing.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).disposition(),
                           is(LimitAlgorithm.Outcome.Disposition.REJECTED));
            } finally {
                semaphore.unblockRelease();
            }

            LimitAlgorithm.Outcome refilled = refilling.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(refilled.disposition(), is(LimitAlgorithm.Outcome.Disposition.ACCEPTED));
            ((LimitAlgorithm.Outcome.Accepted) refilled).token().success();
            assertThat(acceptedBeforeRejection(limit, amount), is(amount - 1));
            assertThat(limit.tryAcquireOutcome(false).disposition(),
                       is(LimitAlgorithm.Outcome.Disposition.REJECTED));
        } finally {
            semaphore.unblockRelease();
        }
        assertThat(semaphore.availablePermits(), is(0));
    }

    @Test
    void tokenBucketDoesNotReuseTimeSpentPublishingPermits() throws Exception {
        int amount = 5;
        TestNanoClock clock = new TestNanoClock();
        BlockingReleaseSemaphore semaphore = new BlockingReleaseSemaphore();

        Limit limit = ThroughputLimit.builder()
                .amount(amount)
                .duration(Duration.ofSeconds(1))
                .queueLength(amount)
                .queueTimeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                .semaphore(semaphore)
                .clock(clock::getNanos)
                .build();

        clock.advance(Duration.ofSeconds(1));
        semaphore.blockNextRelease();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<LimitAlgorithm.Outcome> refilling = executor.submit(() -> limit.tryAcquireOutcome(true));
            assertThat("Timed out waiting for permit publication to pause",
                       semaphore.awaitBlockedRelease(), is(true));

            List<Future<LimitAlgorithm.Outcome>> queued = new ArrayList<>(amount - 1);
            for (int i = 0; i < amount - 1; i++) {
                queued.add(executor.submit(() -> limit.tryAcquireOutcome(true)));
            }
            assertThat("Timed out waiting for callers to queue on the pending refill",
                       semaphore.awaitQueueLength(amount - 1), is(true));

            clock.advance(Duration.ofSeconds(1));
            semaphore.unblockRelease();

            List<LimitAlgorithm.Outcome> outcomes = new ArrayList<>(amount);
            outcomes.add(refilling.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            for (Future<LimitAlgorithm.Outcome> future : queued) {
                outcomes.add(future.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }

            for (LimitAlgorithm.Outcome outcome : outcomes) {
                assertThat(outcome.disposition(), is(LimitAlgorithm.Outcome.Disposition.ACCEPTED));
                ((LimitAlgorithm.Outcome.Accepted) outcome).token().success();
            }

            assertThat(limit.tryAcquireOutcome(false).disposition(),
                       is(LimitAlgorithm.Outcome.Disposition.REJECTED));
        } finally {
            semaphore.unblockRelease();
        }
        assertThat(semaphore.availablePermits(), is(0));
    }

    @Test
    void tokenBucketPublishesPermitsBeforeRefillTimestamp() {
        TestNanoClock clock = new TestNanoClock();
        FailOnceBeforeReleaseSemaphore semaphore = new FailOnceBeforeReleaseSemaphore();

        Limit limit = ThroughputLimit.builder()
                .amount(1)
                .duration(Duration.ofSeconds(1))
                .queueLength(0)
                .semaphore(semaphore)
                .clock(clock::getNanos)
                .build();

        clock.advance(Duration.ofSeconds(1));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                      () -> limit.tryAcquireOutcome(false));
        assertThat(failure.getMessage(), is(FailOnceBeforeReleaseSemaphore.FAILURE_MESSAGE));

        LimitAlgorithm.Outcome retry = limit.tryAcquireOutcome(false);
        assertThat(retry.disposition(), is(LimitAlgorithm.Outcome.Disposition.ACCEPTED));
        ((LimitAlgorithm.Outcome.Accepted) retry).token().success();
        assertThat(limit.tryAcquireOutcome(false).disposition(),
                   is(LimitAlgorithm.Outcome.Disposition.REJECTED));
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

    private static int acceptedBeforeRejection(Limit limit, int maxAttempts) {
        int accepted = 0;
        for (int i = 0; i < maxAttempts; i++) {
            LimitAlgorithm.Outcome outcome = limit.tryAcquireOutcome(false);
            if (!(outcome instanceof LimitAlgorithm.Outcome.Accepted acceptedOutcome)) {
                return accepted;
            }
            accepted++;
            acceptedOutcome.token().success();
        }
        return accepted;
    }

    private static class BlockingReleaseSemaphore extends Semaphore {
        private final CountDownLatch releaseBlocked = new CountDownLatch(1);
        private final CountDownLatch releasePermitted = new CountDownLatch(1);
        private volatile boolean blockNextRelease;

        private BlockingReleaseSemaphore() {
            super(0);
        }

        @Override
        public void release(int permits) {
            if (blockNextRelease) {
                blockNextRelease = false;
                releaseBlocked.countDown();
                try {
                    if (!releasePermitted.await(RELEASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to finish permit release");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while releasing permits", e);
                }
            }
            super.release(permits);
        }

        private void blockNextRelease() {
            blockNextRelease = true;
        }

        private boolean awaitBlockedRelease() throws InterruptedException {
            return releaseBlocked.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        private boolean awaitQueueLength(int queueLength) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TEST_TIMEOUT_SECONDS);
            while (System.nanoTime() < deadline) {
                if (getQueueLength() >= queueLength) {
                    return true;
                }
                Thread.onSpinWait();
            }
            return false;
        }

        private void unblockRelease() {
            releasePermitted.countDown();
        }
    }

    private static class FailOnceBeforeReleaseSemaphore extends Semaphore {
        private static final String FAILURE_MESSAGE = "Deliberate permit publication failure";
        private boolean fail = true;

        private FailOnceBeforeReleaseSemaphore() {
            super(0);
        }

        @Override
        public void release(int permits) {
            if (fail) {
                fail = false;
                throw new IllegalStateException(FAILURE_MESSAGE);
            }
            super.release(permits);
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
