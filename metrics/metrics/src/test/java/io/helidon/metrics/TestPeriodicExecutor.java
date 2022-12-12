/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
package io.helidon.metrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

class TestPeriodicExecutor {

    private static final int APPROX_TEST_DURATION_MS = 1500;
    private static final int MAX_TEST_WAIT_TIME_MS = APPROX_TEST_DURATION_MS * 15 / 10; // 1.5 * approx test duration

    private static final int FAST_INTERVAL = 250;
    private static final int SLOW_INTERVAL = 400;

    private static final int MIN_FAST_COUNT = APPROX_TEST_DURATION_MS / FAST_INTERVAL;
    private static final int MIN_SLOW_COUNT = APPROX_TEST_DURATION_MS / SLOW_INTERVAL;

    private static final Logger PERIODIC_EXECUTOR_LOGGER = Logger.getLogger(PeriodicExecutor.class.getName());

    @Test
    void testWithNoDeferrals() throws InterruptedException {

        PeriodicExecutor exec = PeriodicExecutor.create();
        try {
            exec.startExecutor();
            AtomicInteger countA = new AtomicInteger();
            AtomicInteger countB = new AtomicInteger();

            CountDownLatch latchA = new CountDownLatch(MIN_FAST_COUNT);
            CountDownLatch latchB = new CountDownLatch(MIN_SLOW_COUNT);

            long startTime = System.nanoTime();
            exec.enrollRunner(() -> {
                countA.incrementAndGet();
                latchA.countDown();
            }, Duration.ofMillis(FAST_INTERVAL));

            exec.enrollRunner(() -> {
                countB.incrementAndGet();
                latchB.countDown();
            }, Duration.ofMillis(SLOW_INTERVAL));

            assertThat("Wait latch for fast interval", latchA.await(MAX_TEST_WAIT_TIME_MS, TimeUnit.MILLISECONDS), is(true));
            assertThat("Wait latch for slow interval", latchB.await(MAX_TEST_WAIT_TIME_MS, TimeUnit.MILLISECONDS), is(true));

            Duration elapsedTime = Duration.ofNanos(System.nanoTime() - startTime);

            assertThat("CountA", countA.get(), is(greaterThanOrEqualTo(MIN_FAST_COUNT)));
            assertThat("CountB", countB.get(), is(greaterThanOrEqualTo(MIN_SLOW_COUNT)));
            assertThat("Wait duration", elapsedTime, greaterThanOrEqualTo(Duration.ofMillis(1500)));
        } finally {
            if (exec.executorState() == PeriodicExecutor.State.STARTED) {
                exec.stopExecutor();
            }
        }
    }

    @Test
    void testWithDeferredEnrollments() throws InterruptedException {
        PeriodicExecutor exec = PeriodicExecutor.create();
        try {
            AtomicInteger countA = new AtomicInteger();
            AtomicInteger countB = new AtomicInteger();

            CountDownLatch latchA = new CountDownLatch(MIN_FAST_COUNT);
            CountDownLatch latchB = new CountDownLatch(MIN_SLOW_COUNT);

            exec.enrollRunner(() -> {
                countA.incrementAndGet();
                latchA.countDown();
            }, Duration.ofMillis(FAST_INTERVAL));

            exec.startExecutor();

            exec.enrollRunner(() -> {
                countB.incrementAndGet();
                latchB.countDown();
            }, Duration.ofMillis(SLOW_INTERVAL));

            assertThat("Wait latch for fast interval", latchA.await(MAX_TEST_WAIT_TIME_MS, TimeUnit.MILLISECONDS), is(true));
            assertThat("Wait latch for slow interval", latchB.await(MAX_TEST_WAIT_TIME_MS, TimeUnit.MILLISECONDS), is(true));
        } finally {
            if (exec.executorState() == PeriodicExecutor.State.STARTED) {
                exec.stopExecutor();
            }
        }
    }

    @Test
    void testWithLateEnrollment() throws InterruptedException {
        PeriodicExecutor exec = PeriodicExecutor.create();
        try {
            AtomicInteger countA = new AtomicInteger();
            AtomicInteger countB = new AtomicInteger();

            CountDownLatch latchA = new CountDownLatch(MIN_FAST_COUNT);

            exec.enrollRunner(() -> {
                countA.incrementAndGet();
                latchA.countDown();
            }, Duration.ofMillis(FAST_INTERVAL));

            exec.startExecutor();
            assertThat("Wait latch", latchA.await(MAX_TEST_WAIT_TIME_MS, TimeUnit.MILLISECONDS), is(true));

            exec.stopExecutor();

            exec.enrollRunner(() -> countB.incrementAndGet(), Duration.ofMillis(SLOW_INTERVAL));

            // The executor is no longer running, so we cannot use a countdown latch to know when to check countB. Use time.
            Thread.sleep(MAX_TEST_WAIT_TIME_MS);

            assertThat("CountA", countA.get(), is(greaterThanOrEqualTo(MIN_FAST_COUNT))); // should be 8
            assertThat("CountB", countB.get(), is(0));
        } finally {
            if (exec.executorState() == PeriodicExecutor.State.STARTED) {
                exec.stopExecutor();
            }
        }
    }

    @Test
    void testNoWarningOnStopWhenStopped() throws InterruptedException {

        MyHandler handler = new MyHandler();
        PERIODIC_EXECUTOR_LOGGER.addHandler(handler);
        try {
            PeriodicExecutor executor = PeriodicExecutor.create();
            executor.stopExecutor();

            waitForExecutorState(executor, PeriodicExecutor.State.STOPPED);
            handler.clear();

            executor.stopExecutor();
            List<LogRecord> logRecords = handler.logRecords();
            assertThat("Expected log records", logRecords.size(), is(0));
        } finally {
            PERIODIC_EXECUTOR_LOGGER.removeHandler(handler);
        }
    }

    @Test
    void testFineMessageOnStopWhenStopped() throws InterruptedException {

        MyHandler handler = new MyHandler();
        Level originalLevel = PERIODIC_EXECUTOR_LOGGER.getLevel();

        PERIODIC_EXECUTOR_LOGGER.addHandler(handler);
        try {
            PERIODIC_EXECUTOR_LOGGER.setLevel(Level.FINE);
            PeriodicExecutor executor = PeriodicExecutor.create();
            executor.stopExecutor();
            waitForExecutorState(executor, PeriodicExecutor.State.STOPPED);

            executor.stopExecutor();
            handler.clear();

            executor.stopExecutor();
            List<LogRecord> logRecords = handler.logRecords();
            assertThat("Expected log records", logRecords.size(), is(2));
            assertThat("Log record level", logRecords.get(1)
                    .getLevel(), is(equalTo(Level.FINE)));
            assertThat("Log record content", logRecords.get(1)
                               .getMessage(),
                       containsString("Unexpected attempt to stop"));
        } finally {
            PERIODIC_EXECUTOR_LOGGER.removeHandler(handler);
            PERIODIC_EXECUTOR_LOGGER.setLevel(originalLevel);
        }
    }

    @ParameterizedTest
    @EnumSource(names = { "DORMANT", "STARTED"})
    void testFineLoggingOnExpectedStop(PeriodicExecutor.State testState) throws InterruptedException {
        MyHandler handler = new MyHandler();
        Level originalLevel = PERIODIC_EXECUTOR_LOGGER.getLevel();

        PERIODIC_EXECUTOR_LOGGER.addHandler(handler);
        try {
            PERIODIC_EXECUTOR_LOGGER.setLevel(Level.FINE);
            PeriodicExecutor executor = PeriodicExecutor.create();

            if (testState == PeriodicExecutor.State.STARTED) {
                executor.startExecutor();
                waitForExecutorState(executor, PeriodicExecutor.State.STARTED);
            }

            handler.clear();

            executor.stopExecutor();
            List<LogRecord> logRecords = handler.logRecords();
            assertThat("Expected log records", logRecords.size(), is(1));
            assertThat("Log record level", logRecords.get(0)
                    .getLevel(), is(equalTo(Level.FINE)));
            assertThat("Log record content", logRecords.get(0)
                            .getMessage(),
                    containsString("Received stop request in state"));
            assertThat("Log record parameter (current state)", logRecords.get(0).getParameters(),
                    arrayContaining(testState));
        } finally {
            PERIODIC_EXECUTOR_LOGGER.removeHandler(handler);
            PERIODIC_EXECUTOR_LOGGER.setLevel(originalLevel);
        }
    }

    private void waitForExecutorState(PeriodicExecutor executor, PeriodicExecutor.State expectedState) {
        for (int i = MAX_TEST_WAIT_TIME_MS; i > 0; i -= 500) {
            if (executor.executorState() == expectedState) {
                return;
            }
        }
        fail("Timed out waiting for executor in state " + executor.executorState() + " to enter state " + expectedState.name());
    }

    private static class MyHandler extends Handler {

        private List<LogRecord> logRecords = new ArrayList<>();

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

        void clear() {
            logRecords.clear();
        }
        List<LogRecord> logRecords() {
            return logRecords;
        }
    }
}
