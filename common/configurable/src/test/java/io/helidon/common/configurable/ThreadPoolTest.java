/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * Unit test for class {@link ThreadPool}.
 */
class ThreadPoolTest {

    private static int MAX_WAIT_SECONDS = Integer.parseInt(System.getProperty("thread.pool.test.max.wait", "10"));
    private final List<Task> tasks = new ArrayList<>();
    private ThreadPool pool;

    @AfterEach
    void cleanup() throws Exception {
        if (pool != null) {

            // Unblock all tasks and shutdown

            tasks.forEach(Task::finish);
            tasks.clear();
            pool.shutdown();
            assertThat(pool.awaitTermination(MAX_WAIT_SECONDS, SECONDS), is(true));
            pool = null;
        }
    }

    @Test
    void testNullName() {
        assertIllegalArgument(() -> ThreadPool.create(null,
                                                      5,
                                                      10,
                                                      10,
                                                      20,
                                                      2,
                                                      TimeUnit.MINUTES,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionHandler()
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
                                                      TimeUnit.MINUTES,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionHandler()
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
                                                      TimeUnit.MINUTES,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionHandler()
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
                                                      TimeUnit.MINUTES,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionHandler()
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
                                                      TimeUnit.MINUTES,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionHandler()
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
                                                      TimeUnit.MINUTES,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionHandler()
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
                                                      TimeUnit.MINUTES,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionHandler()
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
                                                      TimeUnit.MINUTES,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionHandler()
        ), "growthRate > 100");
    }

    @Test
    void testKeepAliveTimeLessThanOne() {
        assertIllegalArgument(() -> ThreadPool.create("test",
                                                      10,
                                                      10,
                                                      10,
                                                      20,
                                                      0,
                                                      TimeUnit.MINUTES,
                                                      1000,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionHandler()
        ), "keepAliveTime < 1");
    }

    @Test
    void testQueueCapacityLessThanOne() {
        assertIllegalArgument(() -> ThreadPool.create("test",
                                                      10,
                                                      10,
                                                      10,
                                                      20,
                                                      1,
                                                      TimeUnit.MINUTES,
                                                      0,
                                                      "test-thread",
                                                      true,
                                                      new ThreadPool.RejectionHandler()
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
                                                      TimeUnit.MINUTES,
                                                      1000,
                                                      null,
                                                      true,
                                                      new ThreadPool.RejectionHandler()
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
                                                      TimeUnit.MINUTES,
                                                      1000,
                                                      "",
                                                      true,
                                                      new ThreadPool.RejectionHandler()
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
                                                      TimeUnit.MINUTES,
                                                      1000,
                                                      "helidon",
                                                      true,
                                                      null
        ), "rejectionPolicy is null");
    }

    @Test
    void testCannotChangeMaxPoolSize() {
        pool = newPool(2, 2, 100, 25);
        Logger log = Logger.getLogger(ThreadPool.class.getName());
        assertThat(log.getHandlers().length, is(0));
        assertThat(log.getUseParentHandlers(), is(true));
        List<LogRecord> logRecords = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logRecords.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        };

        try {
            log.addHandler(handler);
            assertThat(pool.getMaximumPoolSize(), is(2));
            pool.setMaximumPoolSize(4);
            assertThat(pool.getMaximumPoolSize(), is(2));
            assertThat(logRecords.size(), is(1));
            assertThat(logRecords.get(0).getMessage(), containsString("cannot be changed"));
        } finally {
            log.removeHandler(handler);
        }
    }

    @Test
    void testSetRejectionHandlerRequiresRejectionPolicy() {
        pool = newPool(2, 2, 100, 25);
        assertThrows(IllegalArgumentException.class, () -> pool.setRejectedExecutionHandler((r, executor) -> {

        }));
    }

    @Test
    void testFixedPoolQueueSelection() {
        pool = newPool(2, 2, 100, 25);
        assertThat(pool.getQueue().getClass() == ThreadPool.WorkQueue.class, is(true));
    }

    @Test
    void testZeroGrowthRateQueueSelection() {
        pool = newPool(2, 8, 100, 0);
        assertThat(pool.getQueue().getClass() == ThreadPool.WorkQueue.class, is(true));
    }

    @Test
    void testNonZeroGrowthRateQueueSelection() {
        pool = newPool(2, 8, 100, 10);
        assertThat(pool.getQueue().getClass() == ThreadPool.DynamicPoolWorkQueue.class, is(true));
    }

    @Test
    void testRateFunction() {
        final int sampleSize = 1000000;
        for (int growthRate = 1; growthRate <= 100; growthRate++) {
            float average = sampleGrowthRateFunction(growthRate, sampleSize, new LocalRandomFloat(growthRate));
            float minAcceptable = growthRate * 0.9F;
            float maxAcceptable = growthRate * 1.1F;
            assertThat(average, is(greaterThanOrEqualTo(minAcceptable)));
            assertThat(average, is(lessThanOrEqualTo(maxAcceptable)));
        }
    }

    @Test
    void testGrowth() throws Exception {
        int coreSize = 2;
        int maxSize = 8;
        int growthThreshold = 10;
        int growthRate = 100;
        int expectedActiveThreads;
        CountDownLatch awaitRunning;
        pool = newPool(coreSize, maxSize, growthThreshold, growthRate);

        // Add coreSize blocked tasks to consume all threads

        awaitRunning = addTasks(coreSize);
        expectedActiveThreads = coreSize;
        waitUntilActiveThreadsIs(expectedActiveThreads, awaitRunning);
        assertThat(pool.getPoolSize(), is(coreSize));
        assertThat(pool.getQueueSize(), is(0));

        // Add growthThreshold + 1 tasks to fill the queue to just above the threshold
        // and make sure the pool has not grown

        addTasks(growthThreshold + 1);
        assertThat(pool.getPoolSize(), is(coreSize));
        assertThat(pool.getQueueSize(), is(growthThreshold + 1));

        // Add 1 task at a time and the pool should grow by one thread each time since
        // we have a 100% rate. Repeat to grow to max size.

        for (int i = coreSize; i < maxSize; i++) {
            addTasks(1);
            expectedActiveThreads++;
            waitUntilActiveThreadsIs(expectedActiveThreads);
        }

        // Ensure that pool size is at max and that the queue is still just above the threshold

        assertThat(pool.getPoolSize(), is(maxSize));
        assertThat(pool.getQueueSize(), is(growthThreshold + 1));

        // Let tasks run until we empty the queue

        while (!tasks.isEmpty()) {
            tasks.remove(0).finish();
        }
        waitUntil(() -> pool.getQueueSize() == 0);

        // Wait for idle threads to be reaped until we reach coreSize again

        waitUntil(() -> pool.getPoolSize() == coreSize);

        // Consume the core threads

        awaitRunning = addTasks(coreSize);
        waitUntilActiveThreadsIs(coreSize, awaitRunning);

        // Fill the queue again

        addTasks(growthThreshold + 1);
        assertThat(pool.getPoolSize(), is(coreSize));
        assertThat(pool.getQueueSize(), is(growthThreshold + 1));

        // Add 1 task and ensure that we grow by 1 thread

        addTasks(1);
        waitUntilActiveThreadsIs(coreSize + 1);
    }

    @Test
    void testRejection() {
        ThreadPool.RejectionHandler rejectionHandler = new ThreadPool.RejectionHandler();
        pool = ThreadPool.create("test",
                                 1,
                                 1,
                                 10,
                                 20,
                                 1,
                                 TimeUnit.MINUTES,
                                 1,
                                 "reject",
                                 true,
                                 rejectionHandler);

        // Consume the one thread in the pool and fill the queue

        addTasks(2);

        // Ensure that another task is rejected with the expected exception

        try {
            assertThat(rejectionHandler.getRejectionCount(), is(0));
            pool.submit(new Task());
            fail("should have failed");
        } catch (RejectedExecutionException e) {
            assertThat(rejectionHandler.getRejectionCount(), is(1));
        }
    }

    @Test
    void testSetCustomRejectionPolicy() {

        ThreadPool.RejectionHandler defaultPolicy = new ThreadPool.RejectionHandler();
        ThreadPool.RejectionHandler customPolicy = new ThreadPool.RejectionHandler() {
            @Override
            protected void throwException(ThreadPoolExecutor executor) {
                throw new IllegalStateException("queue is full");
            }
        };

        pool = ThreadPool.create("test",
                                 1,
                                 1,
                                 10,
                                 20,
                                 1,
                                 TimeUnit.MINUTES,
                                 1,
                                 "reject",
                                 true,
                                 defaultPolicy);

        // Consume the one thread in the pool and fill the queue

        addTasks(2);

        // Ensure that another task is rejected with the expected exception

        try {
            assertThat(defaultPolicy.getRejectionCount(), is(0));
            pool.submit(new Task());
            fail("should have failed");
        } catch (RejectedExecutionException e) {
            assertThat(defaultPolicy.getRejectionCount(), is(1));
        }

        // Reset the handler with with our custom policy

        pool.setRejectedExecutionHandler(customPolicy);

        // Ensure that another task is rejected with the new expected exception

        try {
            assertThat(customPolicy.getRejectionCount(), is(0));
            pool.submit(new Task());
            fail("should have failed");
        } catch (IllegalStateException e) {
            assertThat(customPolicy.getRejectionCount(), is(1));
        }
    }

    private CountDownLatch addTasks(int count) {
        final CountDownLatch awaitRunning = new CountDownLatch(count);
        IntStream.range(0, count).forEach(n -> {
            Task task = new Task(awaitRunning);
            pool.execute(task);
            tasks.add(task);
        });
        return awaitRunning;
    }

    private void waitUntil(Callable<Boolean> condition) {
        await().atMost(MAX_WAIT_SECONDS, SECONDS).until(condition);
    }

    private void waitUntil(Callable<Boolean> condition, Supplier<String> failureMessage) {
        try {
            waitUntil(condition);
        } catch (ConditionTimeoutException e) {
            fail(failureMessage);
        }
    }

    private void waitUntilActiveThreadsIs(int expectedActive) {
        waitUntil(() -> pool.getPoolSize() == expectedActive,
                  () -> "expected " + expectedActive + " active threads, but only " + pool.getActiveThreads()
                        + " after " + MAX_WAIT_SECONDS + " seconds");
    }

    private void waitUntilActiveThreadsIs(int expectedActive, CountDownLatch awaitRunning) throws Exception {
        awaitRunning.await(MAX_WAIT_SECONDS, SECONDS);
        if (pool.getActiveThreads() != expectedActive) {
            fail("expected " + expectedActive + " active threads, but only " + pool.getActiveThreads() + " after "
                 + MAX_WAIT_SECONDS + " seconds");
        }
    }

    private static ThreadPool newPool(int coreSize, int maxSize, int growthThreshold, int growthRate) {
        return ThreadPool.create("test",
                                 coreSize,
                                 maxSize,
                                 growthThreshold,
                                 growthRate,
                                 1,
                                 SECONDS,
                                 100,
                                 "helidon",
                                 true,
                                 new ThreadPool.RejectionHandler()
        );
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

    static class Task implements Runnable {
        private final CountDownLatch running;
        private final CountDownLatch finish;

        Task() {
            this(new CountDownLatch(0));
        }

        Task(final CountDownLatch running) {
            this.running = running;
            this.finish = new CountDownLatch(1);
        }

        void finish() {
            finish.countDown();
        }

        @Override
        public void run() {
            try {
                running.countDown();
                finish.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Test different growth rate functions.
     */
    public static void main() {
        final int sampleSize = 10000000;
        for (int rate = 2; rate < 100; rate += 5) {
            sampleGrowthRateFunction("random", rate, sampleSize, new LocalRandomFloat(rate));
            sampleGrowthRateFunction("counter", rate, sampleSize, new Counter(rate));
            sampleGrowthRateFunction("random1", rate, sampleSize, LOCAL_RANDOM_INT);
            sampleGrowthRateFunction("nanoTime", rate, sampleSize, NANO_TIME);
        }
    }

    private static float sampleGrowthRateFunction(int growthRate, int sampleSize, Function<Integer, Boolean> function) {
        int growthCount = 0;
        for (int i = 0; i < sampleSize; i++) {
            if (function.apply(growthRate)) {
                growthCount++;
            }
        }
        return ((float) growthCount / (float) sampleSize) * 100;
    }

    private static void sampleGrowthRateFunction(String algorithm,
                                                 int growthRate,
                                                 int sampleSize,
                                                 Function<Integer, Boolean> function) {
        final long startTime = System.nanoTime();
        final float growthPercent = sampleGrowthRateFunction(growthRate, sampleSize, function);
        final long elapsedTime = System.nanoTime() - startTime;
//        System.out.println(String.format("%8s: requested %d, actual %.2f in %d ns", algorithm, growthRate, growthPercent,
//                                         elapsedTime));
    }

    private static Function<Integer, Boolean> LOCAL_RANDOM_INT = rate -> ThreadLocalRandom.current().nextInt(100) < rate;
    private static Function<Integer, Boolean> NANO_TIME = rate -> (System.nanoTime() % 100) < rate;

    private static class LocalRandomFloat implements Function<Integer, Boolean> {
        private final float rate;

        LocalRandomFloat(int growthRate) {
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
