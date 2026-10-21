/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.testing.junit5.InMemoryLoggingHandler;
import io.helidon.common.testing.junit5.LogRecordMatcher;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Check that all threads created by timeout instances are properly terminated.
 */
class TimeoutThreadTest {

    @Test
    void testTimeout() {
        // create executor with our thread factory
        TestThreadFactory threadFactory = new TestThreadFactory();
        try (ExecutorService executor = Executors.newThreadPerTaskExecutor(threadFactory)) {
            // run 10 tasks with long timeouts that return immediately
            for (int i = 0; i < 10; i++) {
                String status = Timeout.builder()
                        .timeout(Duration.ofSeconds(60))        // long timeout
                        .currentThread(true)
                        .executor(executor)
                        .build()
                        .invoke(() -> "done");
                assertThat(status, is("done"));
            }

            List<Thread> threads = threadFactory.threads();
            assertThat(threads.size(), is(10));
            for (Thread thread : threads) {
                awaitThreadTerminated(thread);
            }
        }
    }

    @Test
    void testCurrentThreadCompletionDoesNotLogInterruptedMonitor() {
        Logger logger = Logger.getLogger(FaultTolerance.class.getName());
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.ALL);

        TestThreadFactory threadFactory = new TestThreadFactory();
        try (ExecutorService executor = Executors.newThreadPerTaskExecutor(threadFactory);
                InMemoryLoggingHandler handler = InMemoryLoggingHandler.create(logger)) {
            String status = Timeout.builder()
                    .timeout(Duration.ofSeconds(10))
                    .currentThread(true)
                    .executor(executor)
                    .build()
                    .invoke(() -> {
                        Thread monitorThread = awaitMonitorThread(threadFactory);
                        awaitMonitorWaiting(monitorThread);
                        return "done";
                    });
            assertThat(status, is("done"));
            assertThat(threadFactory.threads().size(), is(1));
            awaitThreadTerminated(threadFactory.threads().get(0));
            assertThat(handler.logRecords(),
                       not(hasItem(LogRecordMatcher.withMessage(
                               containsString("Delayed runnable was unexpectedly interrupted")))));
        } finally {
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void testDelayedRunnableRequiresRunnable() {
        assertThrows(NullPointerException.class, () -> FaultTolerance.toDelayedRunnable(null, 1));
    }

    private static Thread awaitMonitorThread(TestThreadFactory threadFactory) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            List<Thread> threads = threadFactory.threads();
            if (!threads.isEmpty()) {
                return threads.get(0);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        throw new AssertionError("Timed out waiting for the timeout monitor thread to start");
    }

    private static void awaitMonitorWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            // Sleeping virtual threads report WAITING on early JDK 21 builds and TIMED_WAITING on newer JDKs.
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        throw new AssertionError("Timed out waiting for the timeout monitor to sleep, last state was " + thread.getState());
    }

    private static void awaitThreadTerminated(Thread thread) {
        try {
            boolean terminated = thread.join(Duration.ofSeconds(5));
            assertThat("Timeout monitor did not terminate, last state was " + thread.getState(), terminated, is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the timeout monitor to terminate", e);
        }
    }

    static class TestThreadFactory implements ThreadFactory {

        private final ThreadFactory delegate = Thread.ofVirtual().factory();
        private final List<Thread> threads = new CopyOnWriteArrayList<>();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = delegate.newThread(r);
            threads.add(t);
            return t;
        }

        List<Thread> threads() {
            return threads;
        }
    }
}
