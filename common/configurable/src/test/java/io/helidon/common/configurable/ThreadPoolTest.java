/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.configurable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for class {@link ThreadPool}.
 */
class ThreadPoolTest {

    private static int MAX_WAIT_SECONDS = Integer.parseInt(System.getProperty("thread.pool.test.max.wait", "10"));

    @Test
    void testNullName() {
        assertIllegalArgument(() -> ThreadPool.create(null,
                                                      5,
                                                      10,
                                                      10,
                                                      20,
                                                      2,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionPolicy()
        ), "name is null or empty");
    }

    @Test
    void testEmptyName() {
        assertIllegalArgument(() -> ThreadPool.create("",
                                                      5,
                                                      10,
                                                      10,
                                                      20,
                                                      2,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionPolicy()
        ), "name is null or empty");
    }

    @Test
    void testNegativeCoreSize() {
        assertIllegalArgument(() -> ThreadPool.create("test",
                                                      -1,
                                                      10,
                                                      10,
                                                      20,
                                                      2,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionPolicy()
        ), "corePoolSize < 0");
    }

    @Test
    void testNegativeMaxSize() {
        assertIllegalArgument(() -> ThreadPool.create("test",
                                                      10,
                                                      -1,
                                                      10,
                                                      20,
                                                      2,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionPolicy()
        ), "maxPoolSize < 0");
    }

    @Test
    void testMaxSizeLessThanCoreSize() {
        assertIllegalArgument(() -> ThreadPool.create("test",
                                                      10,
                                                      9,
                                                      10,
                                                      20,
                                                      2,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionPolicy()
        ), "maxPoolSize < corePoolSize");
    }

    @Test
    void testNegativeGrowthThreshold() {
        assertIllegalArgument(() -> ThreadPool.create("test",
                                                      10,
                                                      10,
                                                      -1,
                                                      20,
                                                      2,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionPolicy()
        ), "growthThreshold < 0");
    }

    @Test
    void testNegativeGrowthRate() {
        assertIllegalArgument(() -> ThreadPool.create("test",
                                                      10,
                                                      10,
                                                      10,
                                                      -1,
                                                      2,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionPolicy()
        ), "growthRate < 0");
    }

    @Test
    void testGrowthRateGreaterThan100() {
        assertIllegalArgument(() -> ThreadPool.create("test",
                                                      10,
                                                      10,
                                                      10,
                                                      101,
                                                      2,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionPolicy()
        ), "growthRate > 100");
    }

    @Test
    void testKeepAliveMinutesLessThanOne() {
        assertIllegalArgument(() -> ThreadPool.create("test",
                                                      10,
                                                      10,
                                                      10,
                                                      20,
                                                      0,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionPolicy()
        ), "keepAliveMinutes < 1");
    }

    @Test
    void testQueueCapacityLessThanOne() {
        assertIllegalArgument(() -> ThreadPool.create("test",
                                                      10,
                                                      10,
                                                      10,
                                                      20,
                                                      1,
                                                      0,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionPolicy()
        ), "workQueueCapacity < 1");
    }

    @Test
    void testNullThreadNamePrefix() {
        assertIllegalArgument(() -> ThreadPool.create("test",
                                                      10,
                                                      10,
                                                      10,
                                                      20,
                                                      1,
                                                      1000,
                                                      null,
                                                      true,
                                                      new ThreadPool.RejectionPolicy()
        ), "threadNamePrefix is null or empty");
    }

    @Test
    void testEmptyThreadNamePrefix() {
        assertIllegalArgument(() -> ThreadPool.create("test",
                                                      10,
                                                      10,
                                                      10,
                                                      20,
                                                      1,
                                                      1000,
                                                      "",
                                                      true,
                                                      new ThreadPool.RejectionPolicy()
        ), "threadNamePrefix is null or empty");
    }

    @Test
    void testNullRejectionPolicy() {
        assertIllegalArgument(() -> ThreadPool.create("test",
                                                      10,
                                                      10,
                                                      10,
                                                      20,
                                                      1,
                                                      1000,
                                                      "helidon",
                                                      true,
                                                      null
        ), "rejectionPolicy is null");
    }

    @Test
    void testGrowth() throws Exception {
        int coreSize = 2;
        int maxSize = 8;
        int growthThreshold = 10;
        int growthRate = 100;
        int expectedActiveThreads;
        ThreadPool pool = newPool(coreSize, maxSize, growthThreshold, growthRate);
        List<Task> tasks = new ArrayList<>();

        // Add coreSize blocked tasks to consume all threads

        addTasks(coreSize, pool, true, tasks);
        expectedActiveThreads = coreSize;
        assertActiveThreads(pool, expectedActiveThreads);
        assertThat(pool.getPoolSize(), is(coreSize));
        assertThat(pool.getQueueSize(), is(0));

        // Add growthThreshold tasks to fill the queue to the threshold

        addTasks(growthThreshold, pool, true, tasks);
        assertActiveThreads(pool, expectedActiveThreads);
        assertThat(pool.getPoolSize(), is(coreSize));
        assertThat(pool.getQueueSize(), is(growthThreshold));

        // Add 1 task at a time and the pool should grow by one thread each time since
        // we have a 100% rate. Repeat to grow to max size.

        for (int i = coreSize; i < maxSize; i++) {
            addTasks(1, pool, true, tasks);
            expectedActiveThreads++;
            assertActiveThreads(pool, expectedActiveThreads);
            assertThat(pool.getPoolSize(), is(expectedActiveThreads));
        }

        // Ensure that pool size is maxed out and that the queue is still at the threshold

        assertThat(pool.getPoolSize(), is(maxSize));
        assertThat(pool.getQueueSize(), is(growthThreshold));

        // Unblock all tasks and shutdown

        tasks.forEach(Task::unblock);
        assertActiveThreads(pool, 0);
        pool.shutdown();
    }

    @Test
    void testRejection() throws Exception {
        List<Task> tasks = new ArrayList<>();
        ThreadPool.RejectionPolicy rejectionPolicy = new ThreadPool.RejectionPolicy();
        ThreadPool pool = ThreadPool.create("test",
                                            1,
                                            1,
                                            10,
                                            20,
                                            1,
                                            1,
                                            "reject",
                                            true,
                                            rejectionPolicy);

        // Consume the one thread in the pool and fill the queue

        addTasks(2, pool, true, tasks);

        // Ensure that another task is rejected with the expected exception

        try {
            assertThat(rejectionPolicy.getRejectionCount(), is(0));
            pool.submit(new Task(true));
            fail("should have failed");
        } catch (RejectedExecutionException e) {
            assertThat(rejectionPolicy.getRejectionCount(), is(1));
        }

        // Unblock all tasks and shutdown

        tasks.forEach(Task::unblock);
        assertActiveThreads(pool, 0);
        pool.shutdown();
    }

    private static void assertActiveThreads(ThreadPool pool, int expectedActive) throws Exception {
        final long startTime = System.currentTimeMillis();
        final long endTime = startTime + (MAX_WAIT_SECONDS * 1000L);
        do {
            if (pool.getActiveThreads() == expectedActive) {
                return;
            }
            Thread.sleep(200L);
        } while (System.currentTimeMillis() <= endTime);
        fail("expected " + expectedActive + " active threads, but only " + pool.getActiveThreads() + " reached in "
             + MAX_WAIT_SECONDS + " seconds");
    }

    private static void addTasks(int count, ThreadPool pool, boolean blocked, List<Task> tasks) {
        IntStream.range(0, count).forEach(n -> {
            Task task = new Task(blocked);
            pool.execute(task);
            tasks.add(task);
        });
    }

    private static ThreadPool newPool(int coreSize, int maxSize, int growthThreshold, int growthRate) {
        return ThreadPool.create("test",
                                 coreSize,
                                 maxSize,
                                 growthThreshold,
                                 growthRate,
                                 1,
                                 100,
                                 "helidon",
                                 true,
                                 new ThreadPool.RejectionPolicy()
        );
    }


    static class Task implements Runnable {
        private final CountDownLatch latch;

        Task(final boolean block) {
            this.latch = new CountDownLatch(block ? 1 : 0);
        }

        void unblock() {
            latch.countDown();
        }

        @Override
        public void run() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }


    static void assertIllegalArgument(Executable executable, String expectedFailureMessage) {
        try {
            executable.execute();
            fail("should have failed");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString(expectedFailureMessage));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void main() {
        final int sampleSize = 10000000;
        for (int rate = 2; rate < 100; rate += 5) {
            collect("random", rate, sampleSize, new Random(rate));
            collect("counter", rate, sampleSize, new Counter(rate));
            collect("random1", rate, sampleSize, RANDOM_INT);
            collect("nanoTime", rate, sampleSize, NANO_TIME);
        }
    }

    private static void collect(String algorithm, int growthRate, int sampleSize, Function<Integer, Boolean> function) {
        final long startTime = System.nanoTime();
        int growthCount = 0;
        for (int i = 0; i < sampleSize; i++) {
            if (function.apply(growthRate)) {
                growthCount++;
            }
        }
        final long elapsedTime = System.nanoTime() - startTime;
        final float growthPercent = ((float) growthCount / (float) sampleSize) * 100;
        System.out.println(String.format("%8s: requested %d, actual %.2f in %d ns", algorithm, growthRate, growthPercent,
                                         elapsedTime));
    }

    private static Function<Integer, Boolean> RANDOM_INT = rate -> ThreadLocalRandom.current().nextInt(100) < rate;
    private static Function<Integer, Boolean> NANO_TIME = rate -> (System.nanoTime() % 100) < rate;

    private static class Random implements Function<Integer, Boolean> {
        private final float rate;

        Random(int growthRate) {
            rate = growthRate / 100f;
        }

        @Override
        public Boolean apply(Integer growthRate) {
            return ThreadLocalRandom.current().nextFloat() < rate;
        }
    }

    private static class Counter implements Function<Integer, Boolean> {
        private static final int SCALING = 10;
        private final AtomicInteger counter;
        private final int scaledRate;

        Counter(int growthRate) {
            scaledRate = Math.round((float) growthRate / (float) SCALING);
            counter = new AtomicInteger(SCALING);
        }

        @Override
        public Boolean apply(Integer growthRate) {
            final int next = counter.decrementAndGet();
            if (next == 0) {
                counter.set(SCALING);
                return true;
            } else {
                return next < scaledRate;
            }
        }
    }
}
