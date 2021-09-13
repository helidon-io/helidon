/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class TestPeriodicExecutor {

    private static final int SLEEP_TIME_MS = 1500;
    private static final int SLEEP_TIME_NO_DATA_MS = 100;

    private static final int FAST_INTERVAL = 250;
    private static final int SLOW_INTERVAL = 400;

    private static final double SLOWDOWN_FACTOR = 0.80; // for slow pipelines!

    private static final double MIN_FAST_COUNT = 1500 / FAST_INTERVAL * SLOWDOWN_FACTOR;
    private static final double MIN_SLOW_COUNT = 1500 / SLOW_INTERVAL * SLOWDOWN_FACTOR;

    private static final Logger PERIODIC_EXECUTOR_LOGGER = Logger.getLogger(PeriodicExecutor.class.getName());

    @Test
    void testWithNoDeferrals() throws InterruptedException {

        PeriodicExecutor exec = PeriodicExecutor.create();
        try {
            exec.startExecutor();
            AtomicInteger countA = new AtomicInteger();
            AtomicInteger countB = new AtomicInteger();

            exec.enrollRunner(() -> countA.incrementAndGet(), Duration.ofMillis(FAST_INTERVAL));
            exec.enrollRunner(() -> countB.incrementAndGet(), Duration.ofMillis(SLOW_INTERVAL));

            Thread.sleep(SLEEP_TIME_MS);

            assertThat("CountA", (double) countA.get(), is(greaterThan(MIN_FAST_COUNT)));
            assertThat("CountB", (double) countB.get(), is(greaterThan(MIN_SLOW_COUNT)));
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

            exec.enrollRunner(() -> countA.incrementAndGet(), Duration.ofMillis(FAST_INTERVAL));

            exec.startExecutor();

            exec.enrollRunner(() -> countB.incrementAndGet(), Duration.ofMillis(SLOW_INTERVAL));

            Thread.sleep(SLEEP_TIME_MS);

            assertThat("CountA", (double) countA.get(), is(greaterThan(MIN_FAST_COUNT)));
            assertThat("CountB", (double) countB.get(), is(greaterThan(MIN_SLOW_COUNT)));
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

            exec.enrollRunner(() -> countA.incrementAndGet(), Duration.ofMillis(FAST_INTERVAL));

            exec.startExecutor();
            Thread.sleep(SLEEP_TIME_MS);

            exec.stopExecutor();

            exec.enrollRunner(() -> countB.incrementAndGet(), Duration.ofMillis(SLOW_INTERVAL));

            assertThat("CountA", (double) countA.get(), is(greaterThan(MIN_FAST_COUNT))); // should be 8
            assertThat("CountB", (double) countB.get(), is(0.0));
        } finally {
            if (exec.executorState() == PeriodicExecutor.State.STARTED) {
                exec.stopExecutor();
            }
        }
    }

    @Test
    void testWarningOnStopWhenStopped() throws InterruptedException {

        MyHandler handler = new MyHandler();
        PERIODIC_EXECUTOR_LOGGER.addHandler(handler);
        try {
            PeriodicExecutor executor = PeriodicExecutor.create();
            executor.stopExecutor();
            Thread.sleep(SLEEP_TIME_NO_DATA_MS);
            handler.clear();

            executor.stopExecutor();
            List<LogRecord> logRecords = handler.logRecords();
            assertThat("Expected log records", logRecords.size(), is(1));
            assertThat("Log record level", logRecords.get(0)
                    .getLevel(), is(equalTo(Level.WARNING)));
            assertThat("Log record content", logRecords.get(0)
                            .getMessage(),
                    containsString("Unexpected attempt to stop"));
        } finally {
            PERIODIC_EXECUTOR_LOGGER.removeHandler(handler);
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
                Thread.sleep(SLEEP_TIME_NO_DATA_MS);
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
