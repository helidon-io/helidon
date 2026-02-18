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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
                    limiter.invoke(() -> {
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
                    limiter.invoke(() -> {
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
            limit.invoke(() -> {
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
            limit.invoke(() -> {
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
            Optional<LimitAlgorithm.Token> token = limit.tryAcquire();
            assertThat(token, not(Optional.empty()));
            token.get().success();
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
