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
package io.helidon.webserver;

import java.time.Duration;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.http.RequestException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@ResourceLock("webserver-error-log-summary")
class ErrorLogLimiterTest {
    private static final long WINDOW = 100;
    private static final String SITE = "Unexpected routing failure";

    private final RecordingLogger details = new RecordingLogger("routing");
    private final List<LogRecord> summaries = new CopyOnWriteArrayList<>();
    private final Logger summaryLogger = Logger.getLogger(ErrorLogLimiter.class.getName());
    private final Handler summaryHandler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            summaries.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @BeforeEach
    void captureSummaries() {
        summaryLogger.addHandler(summaryHandler);
    }

    @AfterEach
    void removeCapture() {
        summaryLogger.removeHandler(summaryHandler);
    }

    @Test
    void repeatedMessagesRetainFirstDetailsAndReportCountsOnce() {
        ErrorLogLimiter limiter = limiter(1, 10, 10, 0);
        Throwable first = failure("first request data", 12);
        log(limiter, first, 0);
        log(limiter, failure("different request data", 12), 1);
        log(limiter, failure("third request data", 12), 2);
        assertThat(details.entries, hasSize(1));
        assertThat(details.entries.getFirst().failure(), sameInstance(first));
        assertThat(details.entries.getFirst().message(), containsString(SITE + " [error-id="));
        limiter.report(WINDOW - 1);
        assertThat(summaries, hasSize(0));
        limiter.report(WINDOW);
        assertSummary(0, 2, 2, 0, 0);
        String identity = details.entries.getFirst().message().split("error-id=")[1].replace("]", "");
        assertThat(summaries.getFirst().getMessage(), containsString("error-id=" + identity + ": 2"));
        limiter.report(WINDOW * 2);
        limiter.flush();
        assertThat(summaries, hasSize(1));
    }

    @Test
    void rolloverAtExactBoundaryRestoresAllowance() {
        ErrorLogLimiter limiter = limiter(1, 10, 10, 5);
        log(limiter, failure("first", 12), 5);
        log(limiter, failure("last old", 12), WINDOW + 4);
        assertThat(details.entries, hasSize(1));
        log(limiter, failure("first new", 12), WINDOW + 5);
        assertThat(details.entries, hasSize(2));
        assertSummary(0, 1, 1, 0, 0);
    }

    @Test
    void typeStackSiteAndLoggerDistinguishErrors() {
        ErrorLogLimiter limiter = limiter(1, 10, 10, 0);
        log(limiter, failure("first", 12), 0);
        log(limiter, failure("other stack", 13), 1);
        IllegalArgumentException otherType = new IllegalArgumentException("other type");
        otherType.setStackTrace(failure("", 12).getStackTrace());
        log(limiter, otherType, 2);
        limiter.log(details, System.Logger.Level.ERROR, "Other fixed diagnostic", failure("", 12), 3);
        RecordingLogger otherLogger = new RecordingLogger("other-routing");
        limiter.log(otherLogger, System.Logger.Level.ERROR, SITE, failure("", 12), 4);
        assertThat(details.entries, hasSize(4));
        assertThat(otherLogger.entries, hasSize(1));
        limiter.flush();
        assertThat(summaries, hasSize(0));
    }

    @Test
    void requestWrapperDoesNotChangeUnderlyingErrorIdentity() {
        ErrorLogLimiter limiter = limiter(1, 10, 10, 0);
        log(limiter, failure("direct", 12), 0);
        log(limiter, RequestException.builder().cause(failure("wrapped", 12)).build(), 1);
        assertThat(details.entries, hasSize(1));
        limiter.flush();
        assertSummary(0, 1, 1, 0, 0);
    }

    @Test
    void overallLimitBoundsDistinctErrorFlood() {
        ErrorLogLimiter limiter = limiter(2, 3, 10, 0);
        for (int i = 0; i < 100; i++) {
            log(limiter, failure("distinct", i), 0);
        }
        assertThat(details.entries, hasSize(3));
        limiter.report(WINDOW);
        assertSummary(0, 97, 0, 97, 0);
        log(limiter, failure("new window", 100), WINDOW);
        assertThat(details.entries, hasSize(4));
    }

    @Test
    void identityLimitPreventsChurnAndRecoversNextWindow() {
        ErrorLogLimiter limiter = limiter(2, 100, 2, 0);
        log(limiter, failure("retained one", 1), 0);
        log(limiter, failure("retained two", 2), 0);
        for (int i = 3; i <= 100; i++) {
            log(limiter, failure("untracked", i), 0);
        }
        log(limiter, failure("retained one again", 1), 0);
        assertThat(details.entries, hasSize(3));
        limiter.report(WINDOW);
        assertSummary(0, 98, 0, 0, 98);
        log(limiter, failure("now admitted", 3), WINDOW);
        assertThat(details.entries, hasSize(4));
    }

    @Test
    void summaryBoundsIdentityDetailsAndKeepsAggregateCount() {
        ErrorLogLimiter limiter = limiter(1, 100, 100, 0);
        for (int i = 0; i < 20; i++) {
            log(limiter, failure("first", i), 0);
            log(limiter, failure("repeat", i), 0);
        }
        limiter.flush();
        assertSummary(0, 20, 20, 0, 0);
        assertThat(summaries.getFirst().getMessage().split("error-id=").length - 1, is(8));
        limiter.flush();
        assertThat(summaries, hasSize(1));
    }

    @Test
    void summarySeparatesRepeatedAndUntrackedSuppression() {
        ErrorLogLimiter limiter = limiter(1, 3, 2, 0);
        log(limiter, failure("first", 1), 0);
        log(limiter, failure("repeated", 1), 0);
        log(limiter, failure("second", 2), 0);
        log(limiter, failure("untracked", 3), 0);
        limiter.flush();
        assertSummary(0, 2, 1, 0, 1);
        assertThat(details.entries, hasSize(2));
    }

    @Test
    void disabledPreservesOriginalLogsAndNonLoggableDoesNotConsumeBudget() {
        ErrorLogLimiter disabled = new ErrorLogLimiter(ErrorLogConfig.builder().enabled(false).build(), 0);
        Throwable failure = failure("full detail", 1);
        for (int i = 0; i < 5; i++) {
            log(disabled, failure, 0);
        }
        assertThat(details.entries, hasSize(5));
        assertThat(details.entries.getFirst().message(), is(SITE));
        details.entries.clear();
        ErrorLogLimiter limiter = limiter(1, 1, 1, 0);
        details.loggable = false;
        log(limiter, failure, 0);
        details.loggable = true;
        log(limiter, failure, 0);
        assertThat(details.entries, hasSize(1));
        limiter.flush();
        assertThat(summaries, hasSize(0));
    }

    @Test
    void causalCyclesAndDeepStacksHaveBoundedIdentity() {
        ErrorLogLimiter limiter = limiter(1, 10, 10, 0);
        Throwable cycle = failure("cycle", 1);
        Throwable second = failure("second", 2);
        cycle.initCause(second);
        second.initCause(cycle);
        log(limiter, cycle, 0);
        log(limiter, cycle, 0);
        Throwable firstDeep = deepFailure(20);
        Throwable otherDeep = deepFailure(21);
        log(limiter, firstDeep, 0);
        log(limiter, otherDeep, 0);
        assertThat(details.entries, hasSize(2));
        limiter.flush();
        assertSummary(0, 2, 2, 0, 0);
    }

    @Test
    void nullFailureStillHasAnIdentity() {
        ErrorLogLimiter limiter = limiter(1, 10, 10, 0);
        log(limiter, null, 0);
        log(limiter, null, 0);
        assertThat(details.entries, hasSize(1));
        limiter.flush();
        assertSummary(0, 1, 1, 0, 0);
    }

    @Test
    void nanoTimeWrapPreservesElapsedWindow() {
        long start = Long.MAX_VALUE - 50;
        ErrorLogLimiter limiter = limiter(1, 10, 10, start);
        log(limiter, failure("first", 1), start);
        log(limiter, failure("before", 1), start + WINDOW - 1);
        assertThat(details.entries, hasSize(1));
        log(limiter, failure("boundary", 1), start + WINDOW);
        assertThat(details.entries, hasSize(2));
        assertSummary(0, 1, 1, 0, 0);
    }

    @Test
    void concurrentRepeatedErrorsAccountForEveryAttempt() throws Exception {
        ErrorLogLimiter limiter = limiter(3, 100, 10, 0);
        runConcurrent(16, () -> {
            for (int i = 0; i < 100; i++) {
                log(limiter, failure("concurrent", 1), 0);
            }
        });
        assertThat(details.entries, hasSize(3));
        limiter.flush();
        assertSummary(0, 1597, 1597, 0, 0);
    }

    @Test
    void concurrentDistinctErrorsRespectOverallCapAndRolloverReportsOnce() throws Exception {
        ErrorLogLimiter limiter = limiter(100, 7, 100, 0);
        runConcurrent(16, () -> {
            for (int i = 0; i < 10; i++) {
                limiter.log(details, System.Logger.Level.ERROR, Thread.currentThread().getName(), failure("", i), 0);
            }
        });
        assertThat(details.entries, hasSize(7));
        runConcurrent(16, () -> {
            limiter.report(WINDOW);
            log(limiter, failure("new window", 1), WINDOW);
        });
        assertSummary(0, 153, 0, 153, 0);
        assertThat(summaries, hasSize(1));
        assertThat(details.entries, hasSize(14));
        limiter.flush();
        assertSummary(1, 9, 0, 9, 0);
    }

    private static ErrorLogLimiter limiter(int perError, int total, int identities, long now) {
        return new ErrorLogLimiter(ErrorLogConfig.builder()
                                           .window(Duration.ofNanos(WINDOW))
                                           .maxPerError(perError)
                                           .maxTotal(total)
                                           .maxErrors(identities)
                                           .build(), now);
    }

    private static Throwable failure(String message, int line) {
        Throwable failure = new IllegalStateException(message);
        failure.setStackTrace(new StackTraceElement[] {new StackTraceElement("Application", "handle", "Application.java", line)});
        return failure;
    }

    private static Throwable deepFailure(int lastLine) {
        Throwable result = failure("deep", 3);
        StackTraceElement[] stack = new StackTraceElement[1000];
        for (int i = 0; i < stack.length; i++) {
            stack[i] = new StackTraceElement("Deep", "handle", "Deep.java", i < 8 ? i : lastLine);
        }
        result.setStackTrace(stack);
        Throwable current = result;
        for (int i = 0; i < 100; i++) {
            Throwable next = failure("cause", i < 3 ? i : lastLine);
            current.initCause(next);
            current = next;
        }
        return result;
    }

    private static void runConcurrent(int threads, Runnable action) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new CopyOnWriteArrayList<>();
        try (var executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    action.run();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get();
            }
        }
    }

    private void log(ErrorLogLimiter limiter, Throwable failure, long now) {
        limiter.log(details, System.Logger.Level.ERROR, SITE, failure, now);
    }

    private void assertSummary(int index, long total, long repeated, long overall, long untracked) {
        assertThat(summaries.get(index).getMessage(), containsString("Suppressed " + total
                + " internal server error log entries (per-error limit: " + repeated
                + ", overall limit: " + overall + ", identity limit: " + untracked + ")."));
    }

    private record Entry(String message, Throwable failure) {
    }

    private static final class RecordingLogger implements System.Logger {
        private final String name;
        private final List<Entry> entries = new CopyOnWriteArrayList<>();
        private boolean loggable = true;

        private RecordingLogger(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isLoggable(Level level) {
            return loggable;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String message, Throwable failure) {
            entries.add(new Entry(message, failure));
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            entries.add(new Entry(format, null));
        }
    }
}
