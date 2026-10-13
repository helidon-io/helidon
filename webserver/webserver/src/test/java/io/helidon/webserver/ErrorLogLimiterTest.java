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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertConcurrentSummary(0, 1597, true);
    }

    @Test
    void concurrentDistinctErrorsRespectOverallCapAndRolloverReportsOnce() throws Exception {
        ErrorLogLimiter limiter = limiter(100, 7, 100, 0);
        runConcurrent(16, () -> {
            for (int i = 0; i < 10; i++) {
                limiter.log(details, System.Logger.Level.ERROR, Thread.currentThread().getName(), failure("", i), 0);
            }
        });
        int firstWindowLogged = details.entries.size();
        assertThat(firstWindowLogged, greaterThanOrEqualTo(1));
        assertThat(firstWindowLogged, lessThanOrEqualTo(7));
        runConcurrent(16, () -> {
            limiter.report(WINDOW);
            log(limiter, failure("new window", 1), WINDOW);
        });
        assertConcurrentSummary(0, 160 - firstWindowLogged, false);
        assertThat(summaries, hasSize(1));
        int secondWindowLogged = details.entries.size() - firstWindowLogged;
        assertThat(secondWindowLogged, greaterThanOrEqualTo(1));
        assertThat(secondWindowLogged, lessThanOrEqualTo(7));
        limiter.flush();
        assertConcurrentSummary(1, 16 - secondWindowLogged, false);
    }

    @Test
    void blockedIdentityDoesNotPreventReportsAndRechecksCurrentWindowBudget() throws Exception {
        ErrorLogLimiter limiter = limiter(1, 1, 10, 0);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> blocked = executor.submit(() -> log(limiter, new BlockingFailure(1, entered, release), 0));
            assertThat("Identity calculation started", entered.await(10, TimeUnit.SECONDS), is(true));
            executor.submit(() -> limiter.report(WINDOW)).get(10, TimeUnit.SECONDS);
            log(limiter, failure("current window", 2), WINDOW);
            release.countDown();
            blocked.get(10, TimeUnit.SECONDS);
            assertThat(details.entries, hasSize(1));
            limiter.report(WINDOW * 2);
            assertSummary(0, 1, 0, 1, 0);
            assertThat(summaries, hasSize(1));
        } finally {
            release.countDown();
            stop(executor);
        }
    }

    @Test
    void concurrentIdentityCompletionRechecksOverallCap() throws Exception {
        ErrorLogLimiter limiter = limiter(10, 1, 10, 0);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> log(limiter, new BlockingFailure(1, entered, release), 0));
            Future<?> second = executor.submit(() -> log(limiter, new BlockingFailure(2, entered, release), 0));
            assertThat("Both identities passed the initial budget check", entered.await(10, TimeUnit.SECONDS), is(true));
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(details.entries, hasSize(1));
            limiter.flush();
            assertSummary(0, 1, 0, 1, 0);
        } finally {
            release.countDown();
            stop(executor);
        }
    }

    @Test
    void concurrentIdentityCompletionRechecksTrackedIdentityCap() throws Exception {
        ErrorLogLimiter limiter = limiter(10, 10, 1, 0);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> log(limiter, new BlockingFailure(1, entered, release), 0));
            Future<?> second = executor.submit(() -> log(limiter, new BlockingFailure(2, entered, release), 0));
            assertThat("Both identities passed the initial budget check", entered.await(10, TimeUnit.SECONDS), is(true));
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(details.entries, hasSize(1));
            limiter.flush();
            assertSummary(0, 1, 0, 0, 1);
        } finally {
            release.countDown();
            stop(executor);
        }
    }

    @Test
    void concurrentIdentityCompletionRechecksPerErrorCap() throws Exception {
        ErrorLogLimiter limiter = limiter(1, 10, 10, 0);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> log(limiter, new BlockingFailure(1, entered, release), 0));
            Future<?> second = executor.submit(() -> log(limiter, new BlockingFailure(1, entered, release), 0));
            assertThat("Both matching identities are being calculated", entered.await(10, TimeUnit.SECONDS), is(true));
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(details.entries, hasSize(1));
            limiter.flush();
            assertSummary(0, 1, 1, 0, 0);
        } finally {
            release.countDown();
            stop(executor);
        }
    }

    @Test
    void identityWorkBoundSuppressesWithoutCalculatingAndReleasesSlots() throws Exception {
        ErrorLogLimiter limiter = limiter(1, 100, 100, 0);
        CountDownLatch entered = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<?>> tasks = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            for (int i = 0; i < 4; i++) {
                int line = i;
                tasks.add(executor.submit(() -> log(limiter, new BlockingFailure(line, entered, release), 0)));
            }
            assertThat("Four identity calculations started", entered.await(10, TimeUnit.SECONDS), is(true));
            AtomicInteger unexpectedStackCalls = new AtomicInteger();
            Throwable excess = new IllegalStateException() {
                @Override
                public StackTraceElement[] getStackTrace() {
                    unexpectedStackCalls.incrementAndGet();
                    return super.getStackTrace();
                }
            };
            executor.submit(() -> log(limiter, excess, 0)).get(10, TimeUnit.SECONDS);
            assertThat("Excess work must not copy a stack", unexpectedStackCalls.get(), is(0));
            assertThat(details.entries, hasSize(0));
            limiter.flush();
            assertSummary(0, 1, 0, 0, 0, 1);
            executor.submit(() -> log(limiter, excess, 0)).get(10, TimeUnit.SECONDS);
            assertThat("Flushing counters must not release active work slots", unexpectedStackCalls.get(), is(0));
            release.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
            assertThat(details.entries, hasSize(4));
            log(limiter, failure("slot reused", 10), 0);
            assertThat(details.entries, hasSize(5));
            limiter.flush();
            assertSummary(1, 1, 0, 0, 0, 1);
            assertThat(summaries, hasSize(2));
        } finally {
            release.countDown();
            stop(executor);
        }
    }

    @Test
    void failingIdentityCalculationReleasesWorkSlots() {
        ErrorLogLimiter limiter = limiter(1, 100, 100, 0);
        Throwable failure = new IllegalStateException() {
            @Override
            public StackTraceElement[] getStackTrace() {
                throw new IllegalArgumentException("Cannot obtain stack");
            }
        };
        for (int i = 0; i < 8; i++) {
            assertThrows(IllegalArgumentException.class, () -> log(limiter, failure, 0));
        }
        log(limiter, failure("subsequent error", 1), 0);
        assertThat(details.entries, hasSize(1));
        limiter.flush();
        assertThat(summaries, hasSize(0));
    }

    @Test
    void failingIdentityCalculationPreservesDueSuppressionReport() {
        ErrorLogLimiter limiter = limiter(1, 100, 100, 0, 25);
        log(limiter, failure("first", 1), 0);
        log(limiter, failure("suppressed repeat", 1), 1);
        IllegalArgumentException extractionFailure = new IllegalArgumentException("Cannot obtain stack");
        Throwable failure = new IllegalStateException() {
            @Override
            public StackTraceElement[] getStackTrace() {
                throw extractionFailure;
            }
        };

        assertThat(assertThrows(IllegalArgumentException.class, () -> log(limiter, failure, 25)),
                   sameInstance(extractionFailure));
        assertThat("The due report survives the failed identity calculation", summaries, hasSize(1));
        assertSummary(0, 1, 1, 0, 0);
        assertThat(details.entries, hasSize(1));
        log(limiter, failure("subsequent error", 2), 26);
        log(limiter, failure("next suppressed repeat", 1), 27);
        assertThat("Identity work remains available", details.entries, hasSize(2));
        limiter.report(50);
        assertThat("The next report contains only new suppressions", summaries, hasSize(2));
        assertSummary(1, 1, 1, 0, 0);
        limiter.flush();
        assertThat("Shutdown must not duplicate either report", summaries, hasSize(2));
    }

    @Test
    void multipleReportsDoNotRenewPerErrorAllowanceWithinCollectionWindow() {
        ErrorLogLimiter limiter = limiter(1, 10, 10, 0, 25);
        log(limiter, failure("first", 1), 0);
        log(limiter, failure("first repeat", 1), 1);
        limiter.report(24);
        assertThat(summaries, hasSize(0));
        limiter.report(25);
        assertSummary(0, 1, 1, 0, 0);
        log(limiter, failure("second report period", 1), 26);
        limiter.report(50);
        assertSummary(1, 1, 1, 0, 0);
        assertThat(details.entries, hasSize(1));
        log(limiter, failure("third report period", 1), 51);
        limiter.report(75);
        assertSummary(2, 1, 1, 0, 0);
        log(limiter, failure("next collection", 1), WINDOW);
        assertThat(details.entries, hasSize(2));
        limiter.flush();
        assertThat(summaries, hasSize(3));
    }

    @Test
    void overallAndTrackedIdentityAllowancesPersistAcrossReports() {
        ErrorLogLimiter limiter = limiter(10, 2, 1, 0, 25);
        log(limiter, failure("first identity", 1), 0);
        log(limiter, failure("untracked identity", 2), 1);
        limiter.report(25);
        assertSummary(0, 1, 0, 0, 1);
        log(limiter, failure("still untracked", 2), 26);
        log(limiter, failure("retained identity second allowance", 1), 27);
        log(limiter, failure("overall limit reached", 3), 28);
        limiter.report(50);
        assertSummary(1, 2, 0, 1, 1);
        log(limiter, failure("overall still exhausted", 1), 51);
        limiter.report(75);
        assertSummary(2, 1, 0, 1, 0);
        assertThat(details.entries, hasSize(2));
        log(limiter, failure("new collection new identity", 3), WINDOW);
        assertThat(details.entries, hasSize(3));
    }

    @Test
    void longerNonDivisorReportIntervalRetainsCountsAndIdentitiesAcrossCollectionResets() {
        ErrorLogLimiter limiter = limiter(1, 10, 10, 0, 250);
        for (int i = 0; i < 3; i++) {
            log(limiter, failure("first in collection", i), i * WINDOW);
            log(limiter, failure("repeat in collection", i), i * WINDOW + 1);
        }
        limiter.report(249);
        assertThat(details.entries, hasSize(3));
        assertThat(summaries, hasSize(0));
        limiter.report(250);
        assertSummary(0, 3, 3, 0, 0);
        for (Entry entry : details.entries) {
            String identity = entry.message().split("error-id=")[1].replace("]", "");
            assertThat(summaries.getFirst().getMessage(), containsString("error-id=" + identity + ": 1"));
        }
        log(limiter, failure("still same collection", 2), 251);
        assertThat(details.entries, hasSize(3));
        log(limiter, failure("next collection", 2), 300);
        assertThat(details.entries, hasSize(4));
        limiter.report(500);
        assertSummary(1, 1, 1, 0, 0);
        assertThat(summaries, hasSize(2));
    }

    @Test
    void logReportsOnIndependentNonDivisorIntervalWithoutRenewingAllowance() {
        ErrorLogLimiter limiter = limiter(1, 10, 10, 0, 30);
        log(limiter, failure("first", 1), 0);
        log(limiter, failure("repeat", 1), 1);
        log(limiter, failure("report boundary", 1), 30);
        assertSummary(0, 1, 1, 0, 0);
        log(limiter, failure("second report boundary", 1), 60);
        assertSummary(1, 1, 1, 0, 0);
        log(limiter, failure("collection boundary", 1), WINDOW);
        assertSummary(2, 1, 1, 0, 0);
        assertThat(details.entries, hasSize(2));
        limiter.report(120);
        limiter.flush();
        assertThat(summaries, hasSize(3));
    }

    @Test
    void reportSamplesRemainBoundedAcrossManyCollectionWindows() {
        ErrorLogLimiter limiter = limiter(1, 10, 1, 0, 2500);
        for (int i = 0; i < 20; i++) {
            log(limiter, failure("first in collection", i), i * WINDOW);
            log(limiter, failure("repeat in collection", i), i * WINDOW + 1);
        }
        assertThat(details.entries, hasSize(20));
        assertThat(summaries, hasSize(0));
        limiter.report(2500);
        assertSummary(0, 20, 20, 0, 0);
        assertThat(summaries.getFirst().getMessage().split("error-id=").length - 1, is(8));
        limiter.flush();
        assertThat(summaries, hasSize(1));
    }

    @Test
    void shutdownFlushReportsOnlyPendingCountsAndDoesNotRenewAllowances() {
        ErrorLogLimiter limiter = limiter(1, 10, 10, 0, 25);
        log(limiter, failure("first", 1), 0);
        log(limiter, failure("first report", 1), 1);
        limiter.report(25);
        assertSummary(0, 1, 1, 0, 0);
        log(limiter, failure("pending at shutdown", 1), 26);
        limiter.flush();
        assertSummary(1, 1, 1, 0, 0);
        limiter.flush();
        assertThat(summaries, hasSize(2));
        log(limiter, failure("allowance remains exhausted after flush", 1), 27);
        assertThat(details.entries, hasSize(1));
        limiter.flush();
        assertSummary(2, 1, 1, 0, 0);
    }

    @Test
    void independentCollectionAndReportClocksSurviveNanoTimeWrap() {
        long start = Long.MAX_VALUE - 50;
        ErrorLogLimiter limiter = limiter(1, 10, 10, start, 25);
        log(limiter, failure("first", 1), start);
        log(limiter, failure("repeat", 1), start + 1);
        limiter.report(start + 25);
        assertSummary(0, 1, 1, 0, 0);
        log(limiter, failure("wrapped repeat", 1), start + 60);
        limiter.report(start + 75);
        assertSummary(1, 1, 1, 0, 0);
        log(limiter, failure("wrapped collection boundary", 1), start + WINDOW);
        assertThat(details.entries, hasSize(2));
        limiter.flush();
        assertThat(summaries, hasSize(2));
    }

    @Test
    void concurrentAttemptsRemainAccountedAcrossIndependentReportAndCollectionBoundaries() throws Exception {
        ErrorLogLimiter limiter = limiter(1, 100, 10, 0, 25);
        runConcurrent(8, () -> {
            for (int i = 0; i < 10; i++) {
                log(limiter, failure("first period", 1), 0);
            }
        });
        assertThat(details.entries, hasSize(1));
        runConcurrent(8, () -> limiter.report(25));
        assertConcurrentSummary(0, 79, true);
        runConcurrent(8, () -> {
            for (int i = 0; i < 10; i++) {
                log(limiter, failure("second period", 1), 26);
            }
        });
        runConcurrent(8, () -> limiter.report(50));
        assertConcurrentSummary(1, 80, true);
        assertThat(details.entries, hasSize(1));
        runConcurrent(8, () -> {
            limiter.report(WINDOW);
            log(limiter, failure("next collection", 1), WINDOW);
        });
        assertThat(details.entries, hasSize(2));
        limiter.flush();
        assertConcurrentSummary(2, 7, true);
        assertThat(summaries, hasSize(3));
    }

    private static ErrorLogLimiter limiter(int perError, int total, int identities, long now) {
        return limiter(perError, total, identities, now, WINDOW);
    }

    private static ErrorLogLimiter limiter(int perError, int total, int identities, long now, long reportInterval) {
        return new ErrorLogLimiter(ErrorLogConfig.builder()
                                           .window(Duration.ofNanos(WINDOW))
                                           .reportInterval(Duration.ofNanos(reportInterval))
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
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    action.run();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            stop(executor);
        }
    }

    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat("Test executor terminated", executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
    }

    private void log(ErrorLogLimiter limiter, Throwable failure, long now) {
        limiter.log(details, System.Logger.Level.ERROR, SITE, failure, now);
    }

    private void assertSummary(int index, long total, long repeated, long overall, long untracked) {
        assertSummary(index, total, repeated, overall, untracked, 0);
    }

    private void assertSummary(int index, long total, long repeated, long overall, long untracked, long workLimited) {
        assertThat(summaries.get(index).getMessage(), containsString("Suppressed " + total
                + " internal server error log entries (per-error limit: " + repeated
                + ", overall limit: " + overall + ", identity limit: " + untracked
                + ", identity work limit: " + workLimited + ")."));
    }

    private void assertConcurrentSummary(int index, long total, boolean repeatedErrors) {
        String message = summaries.get(index).getMessage();
        assertThat(message, containsString("Suppressed " + total + " internal server error log entries"));
        String counts = message.substring(message.indexOf('(') + 1, message.indexOf(')'));
        long sum = 0;
        for (String category : counts.split(", ")) {
            sum += Long.parseLong(category.substring(category.indexOf(": ") + 2));
        }
        assertThat("Suppression categories account for every attempt", sum, is(total));
        assertThat(message, containsString(", identity limit: 0,"));
        if (repeatedErrors) {
            assertThat(message, containsString(", overall limit: 0,"));
        } else {
            assertThat(message, containsString("(per-error limit: 0,"));
        }
    }

    private record Entry(String message, Throwable failure) {
    }

    private static final class BlockingFailure extends IllegalStateException {
        private final CountDownLatch entered;
        private final CountDownLatch release;

        private BlockingFailure(int line, CountDownLatch entered, CountDownLatch release) {
            this.entered = entered;
            this.release = release;
            setStackTrace(new StackTraceElement[] {
                    new StackTraceElement("Application", "handle", "Application.java", line)
            });
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test did not release identity calculation");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Identity calculation interrupted", e);
            }
            return super.getStackTrace();
        }
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
