/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

public class AimdLimitTest {
    @Test
    void decreaseOnDrops() {
        AimdLimitConfig config = AimdLimitConfig.builder()
                .initialLimit(30)
                .buildPrototype();

        AimdLimitImpl limiter = new AimdLimitImpl(config);

        assertThat(limiter.currentLimit(), is(30));
        limiter.updateWithSample(0, 0, 0, false);
        assertThat(limiter.currentLimit(), is(27));
    }

    @Test
    void decreaseOnTimeoutExceeded() {
        Duration timeout = Duration.ofSeconds(1);
        AimdLimitConfig config = AimdLimitConfig.builder()
                .initialLimit(30)
                .timeout(timeout)
                .buildPrototype();
        AimdLimitImpl limiter = new AimdLimitImpl(config);
        limiter.updateWithSample(0, timeout.toNanos() + 1, 0, true);
        assertThat(limiter.currentLimit(), is(27));
    }

    @Test
    void increaseOnSuccess() {
        AimdLimitConfig config = AimdLimitConfig.builder()
                .initialLimit(20)
                .buildPrototype();
        AimdLimitImpl limiter = new AimdLimitImpl(config);
        limiter.updateWithSample(0, Duration.ofMillis(1).toNanos(), 10, true);
        assertThat(limiter.currentLimit(), is(21));
    }

    @Test
    void successOverflow() {
        AimdLimitConfig config = AimdLimitConfig.builder()
                .initialLimit(21)
                .maxLimit(21)
                .minLimit(0)
                .buildPrototype();
        AimdLimitImpl limiter = new AimdLimitImpl(config);
        limiter.updateWithSample(0, Duration.ofMillis(1).toNanos(), 10, true);
        // after success limit should still be at the max.
        assertThat(limiter.currentLimit(), is(21));
    }

    @Test
    void testDefault() {
        AimdLimitConfig config = AimdLimitConfig.builder()
                .minLimit(10)
                .initialLimit(10)
                .buildPrototype();
        AimdLimitImpl limiter = new AimdLimitImpl(config);
        assertThat(limiter.currentLimit(), is(10));
    }

    @Test
    void concurrentUpdatesAndReads() throws InterruptedException {
        AimdLimitConfig config = AimdLimitConfig.builder()
                .initialLimit(1)
                .backoffRatio(0.9)
                .timeout(Duration.ofMillis(100))
                .minLimit(1)
                .maxLimit(200)
                .buildPrototype();
        AimdLimitImpl limit = new AimdLimitImpl(config);

        int threadCount = 100;
        int operationsPerThread = 1_000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger timeoutCount = new AtomicInteger(0);
        AtomicInteger dropCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int j = 0; j < operationsPerThread; j++) {
                        long startTime = System.nanoTime();
                        long rtt = (long) (Math.random() * 200_000_000); // 0-200ms
                        int concurrentRequests = (int) (Math.random() * limit.currentLimit() * 2);
                        boolean didDrop = Math.random() < 0.01; // 1% chance of drop

                        limit.updateWithSample(startTime, rtt, concurrentRequests, !didDrop);

                        if (didDrop) {
                            dropCount.incrementAndGet();
                        } else if (rtt > config.timeout().toNanos()) {
                            timeoutCount.incrementAndGet();
                        } else {
                            successCount.incrementAndGet();
                        }

                        // Read the current limit
                        int currentLimit = limit.currentLimit();
                        assertThat(currentLimit, is(greaterThanOrEqualTo(config.minLimit())));
                        assertThat(currentLimit, is(lessThanOrEqualTo(config.maxLimit())));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        boolean finished = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat("Test did not complete in time", finished, is(true));

        assertThat("Total operations mismatch",
                   threadCount * operationsPerThread,
                   is(successCount.get() + timeoutCount.get() + dropCount.get()));
    }

    @Test
    public void testSemaphoreReleased() throws Exception {
        Limit limit = AimdLimit.builder()
                .minLimit(5)
                .initialLimit(5)
                .build();

        for (int i = 0; i < 5000; i++) {
            limit.invoke(() -> {});
        }
    }

    @Test
    public void testSemaphoreReleasedWithToken() {
        Limit limit = AimdLimit.builder()
                .minLimit(5)
                .initialLimit(5)
                .build();

        for (int i = 0; i < 5000; i++) {
            Optional<LimitAlgorithm.Token> token = limit.tryAcquire();
            assertThat(token, not(Optional.empty()));
            token.get().success();
        }
    }
}
