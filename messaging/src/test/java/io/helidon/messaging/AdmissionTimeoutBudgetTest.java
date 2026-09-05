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

package io.helidon.messaging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdmissionTimeoutBudgetTest {
    @Test
    void sharesFiniteTimeoutAcrossAttempts() {
        long timeout = Duration.ofMillis(150).toNanos();
        AtomicLong nanoTime = new AtomicLong();
        AdmissionTimeoutBudget budget = new AdmissionTimeoutBudget("orders", nanoTime::get);
        List<Long> remainingTimeouts = new ArrayList<>();

        assertThat(budget.attempt(() -> timeout, remaining -> {
            remainingTimeouts.add(remaining);
            return Optional.empty();
        }).isEmpty(), is(true));
        nanoTime.set(Duration.ofMillis(60).toNanos());
        assertThat(budget.attempt(() -> timeout, remaining -> {
            remainingTimeouts.add(remaining);
            return Optional.empty();
        }).isEmpty(), is(true));
        nanoTime.set(timeout);

        MessagingRejectedException failure = assertThrows(
                MessagingRejectedException.class,
                () -> budget.attempt(() -> timeout, remaining -> {
                    remainingTimeouts.add(remaining);
                    return Optional.empty();
                }));
        MessagingRejectedException stickyFailure = assertThrows(
                MessagingRejectedException.class,
                () -> budget.attempt(() -> timeout, remaining -> {
                    remainingTimeouts.add(remaining);
                    return Optional.empty();
                }));
        budget.reset();
        assertThat(attempt(budget, timeout, remainingTimeouts, Optional.empty()).isEmpty(), is(true));

        assertThat(failure.channel(), is("orders"));
        assertThat(failure.reason(), is(MessagingRejectedException.Reason.TIMEOUT));
        assertThat(stickyFailure.reason(), is(MessagingRejectedException.Reason.TIMEOUT));
        assertThat(remainingTimeouts,
                   is(List.of(timeout,
                              Duration.ofMillis(90).toNanos(),
                              timeout)));
    }

    @Test
    void successfulAttemptRestartsFiniteBudget() {
        long timeout = Duration.ofMillis(150).toNanos();
        AtomicLong nanoTime = new AtomicLong();
        AdmissionTimeoutBudget budget = new AdmissionTimeoutBudget("orders", nanoTime::get);
        List<Long> remainingTimeouts = new ArrayList<>();

        assertThat(attempt(budget, timeout, remainingTimeouts, Optional.empty()).isEmpty(), is(true));
        nanoTime.set(Duration.ofMillis(60).toNanos());
        assertThat(attempt(budget, timeout, remainingTimeouts, Optional.of("reservation")),
                   is(Optional.of("reservation")));
        nanoTime.set(Duration.ofMillis(150).toNanos());
        assertThat(attempt(budget, timeout, remainingTimeouts, Optional.empty()).isEmpty(), is(true));
        nanoTime.set(Duration.ofMillis(300).toNanos());

        MessagingRejectedException failure = assertThrows(
                MessagingRejectedException.class,
                () -> attempt(budget, timeout, remainingTimeouts, Optional.empty()));

        assertThat(failure.reason(), is(MessagingRejectedException.Reason.TIMEOUT));
        assertThat(remainingTimeouts,
                   is(List.of(timeout,
                              Duration.ofMillis(90).toNanos(),
                              timeout)));
    }

    @Test
    void timeoutLookupIsChargedToInitialEmptyAttempt() {
        long timeout = Duration.ofMillis(150).toNanos();
        AtomicLong nanoTime = new AtomicLong();
        AdmissionTimeoutBudget budget = new AdmissionTimeoutBudget("orders", nanoTime::get);
        List<Long> remainingTimeouts = new ArrayList<>();

        MessagingRejectedException failure = assertThrows(
                MessagingRejectedException.class,
                () -> budget.attempt(() -> {
                    assertThat(nanoTime.getAndSet(timeout), is(0L));
                    return timeout;
                }, remaining -> {
                    remainingTimeouts.add(remaining);
                    return Optional.empty();
                }));

        assertThat(failure.reason(), is(MessagingRejectedException.Reason.TIMEOUT));
        assertThat(nanoTime.get(), is(timeout));
        assertThat(remainingTimeouts, is(List.of(timeout)));
    }

    @Test
    void unlimitedTimeoutNeverStartsADeadline() {
        AtomicLong nanoTime = new AtomicLong();
        AdmissionTimeoutBudget budget = new AdmissionTimeoutBudget("orders", nanoTime::get);
        List<Long> remainingTimeouts = new ArrayList<>();

        assertThat(attempt(budget, Long.MAX_VALUE, remainingTimeouts, Optional.empty()).isEmpty(), is(true));
        nanoTime.set(Long.MAX_VALUE);
        assertThat(attempt(budget, Long.MAX_VALUE, remainingTimeouts, Optional.empty()).isEmpty(), is(true));

        assertThat(remainingTimeouts, is(List.of(Long.MAX_VALUE, Long.MAX_VALUE)));
    }

    @Test
    void finiteDeadlineSaturatesOnOverflow() {
        long started = Long.MAX_VALUE - 10;
        long timeout = 20;
        AtomicLong nanoTime = new AtomicLong(started);
        AdmissionTimeoutBudget budget = new AdmissionTimeoutBudget("orders", nanoTime::get);
        List<Long> remainingTimeouts = new ArrayList<>();

        assertThat(attempt(budget, timeout, remainingTimeouts, Optional.empty()).isEmpty(), is(true));
        nanoTime.set(Long.MAX_VALUE - 1);
        assertThat(attempt(budget, timeout, remainingTimeouts, Optional.empty()).isEmpty(), is(true));
        nanoTime.set(Long.MAX_VALUE);

        MessagingRejectedException failure = assertThrows(
                MessagingRejectedException.class,
                () -> attempt(budget, timeout, remainingTimeouts, Optional.empty()));

        assertThat(failure.reason(), is(MessagingRejectedException.Reason.TIMEOUT));
        assertThat(remainingTimeouts, is(List.of(timeout, 1L)));
    }

    @Test
    void concurrentEmptyAttemptsShareTheFirstInstalledDeadline() throws Exception {
        long timeout = Duration.ofMillis(100).toNanos();
        AtomicLong nanoTime = new AtomicLong();
        CountDownLatch delayedOperationEntered = new CountDownLatch(1);
        CountDownLatch releaseDelayedOperation = new CountDownLatch(1);
        AdmissionTimeoutBudget budget = new AdmissionTimeoutBudget("orders", nanoTime::get);
        FutureTask<Optional<String>> delayedAttempt = new FutureTask<>(() ->
                budget.attempt(() -> timeout, remaining -> {
                    assertThat(remaining, is(timeout));
                    delayedOperationEntered.countDown();
                    await(releaseDelayedOperation, "release delayed admission attempt");
                    return Optional.empty();
                }));
        Thread delayedThread = Thread.ofVirtual().name("messaging-delayed-admission-attempt").unstarted(delayedAttempt);

        try {
            delayedThread.start();
            await(delayedOperationEntered, "delayed admission operation");
            nanoTime.set(Duration.ofMillis(50).toNanos());
            assertThat(budget.attempt(() -> timeout, ignored -> Optional.empty()).isEmpty(), is(true));
            releaseDelayedOperation.countDown();
            assertThat(delayedAttempt.get(5, TimeUnit.SECONDS).isEmpty(), is(true));

            nanoTime.set(Duration.ofMillis(149).toNanos());
            AtomicLong remainingTimeout = new AtomicLong();
            assertThat(budget.attempt(() -> timeout, remaining -> {
                remainingTimeout.set(remaining);
                return Optional.empty();
            }).isEmpty(), is(true));
            assertThat(remainingTimeout.get(), is(Duration.ofMillis(1).toNanos()));
            nanoTime.set(Duration.ofMillis(150).toNanos());

            MessagingRejectedException failure = assertThrows(
                    MessagingRejectedException.class,
                    () -> budget.attempt(() -> timeout, ignored -> {
                        throw new AssertionError("An expired budget must reject before admission");
                    }));
            assertThat(failure.reason(), is(MessagingRejectedException.Reason.TIMEOUT));
        } finally {
            releaseDelayedOperation.countDown();
            delayedAttempt.cancel(true);
            delayedThread.interrupt();
            delayedThread.join(TimeUnit.SECONDS.toMillis(5));
            assertThat("delayed admission thread did not terminate", delayedThread.isAlive(), is(false));
        }
    }

    @Test
    void concurrentSuccessfulAttemptResetsTheSharedDeadline() throws Exception {
        long timeout = Duration.ofMillis(100).toNanos();
        AtomicLong nanoTime = new AtomicLong();
        CountDownLatch emptyOperationEntered = new CountDownLatch(1);
        CountDownLatch releaseEmptyOperation = new CountDownLatch(1);
        AdmissionTimeoutBudget budget = new AdmissionTimeoutBudget("orders", nanoTime::get);

        assertThat(budget.attempt(() -> timeout, ignored -> Optional.empty()).isEmpty(), is(true));
        nanoTime.set(Duration.ofMillis(20).toNanos());
        FutureTask<Optional<String>> emptyAttempt = new FutureTask<>(() ->
                budget.attempt(() -> timeout, remaining -> {
                    assertThat(remaining, is(Duration.ofMillis(80).toNanos()));
                    emptyOperationEntered.countDown();
                    await(releaseEmptyOperation, "release empty admission attempt");
                    return Optional.empty();
                }));
        Thread emptyThread = Thread.ofVirtual().name("messaging-empty-admission-attempt").unstarted(emptyAttempt);

        try {
            emptyThread.start();
            await(emptyOperationEntered, "empty admission operation");
            assertThat(budget.attempt(() -> timeout, ignored -> Optional.of("reservation")),
                       is(Optional.of("reservation")));
            releaseEmptyOperation.countDown();
            assertThat(emptyAttempt.get(5, TimeUnit.SECONDS).isEmpty(), is(true));

            nanoTime.set(timeout);
            AtomicLong remainingTimeout = new AtomicLong();
            assertThat(budget.attempt(() -> timeout, remaining -> {
                remainingTimeout.set(remaining);
                return Optional.empty();
            }).isEmpty(), is(true));
            assertThat(remainingTimeout.get(), is(timeout));
        } finally {
            releaseEmptyOperation.countDown();
            emptyAttempt.cancel(true);
            emptyThread.interrupt();
            emptyThread.join(TimeUnit.SECONDS.toMillis(5));
            assertThat("empty admission thread did not terminate", emptyThread.isAlive(), is(false));
        }
    }

    @Test
    void doesNotUseExpiredDeadlineResetDuringAttemptObservation() throws Exception {
        long timeout = Duration.ofMillis(150).toNanos();
        AtomicLong nanoTime = new AtomicLong();
        AtomicInteger readsBeforePause = new AtomicInteger();
        CountDownLatch timeRead = new CountDownLatch(1);
        CountDownLatch releaseTimeRead = new CountDownLatch(1);
        AdmissionTimeoutBudget budget = new AdmissionTimeoutBudget("orders", () -> {
            long result = nanoTime.get();
            int remainingReads = readsBeforePause.get();
            if (remainingReads > 0 && readsBeforePause.decrementAndGet() == 0) {
                timeRead.countDown();
                await(releaseTimeRead, "release deadline observation");
            }
            return result;
        });

        assertThat(attempt(budget, timeout, new ArrayList<>(), Optional.empty()).isEmpty(), is(true));
        nanoTime.set(timeout);
        // Pause the active-deadline clock read, after the attempt has already observed that deadline.
        readsBeforePause.set(2);
        AtomicLong remainingTimeout = new AtomicLong();
        FutureTask<Optional<String>> checkTask = new FutureTask<>(() ->
                budget.attempt(() -> timeout, remaining -> {
                    remainingTimeout.set(remaining);
                    return Optional.empty();
                }));
        Thread checkThread = Thread.ofVirtual().name("messaging-admission-deadline-check").unstarted(checkTask);
        try {
            checkThread.start();
            await(timeRead, "deadline clock read");
            budget.reset();
            releaseTimeRead.countDown();

            assertThat(checkTask.get(5, TimeUnit.SECONDS).isEmpty(), is(true));
            assertThat(remainingTimeout.get(), is(timeout));
        } finally {
            releaseTimeRead.countDown();
            checkTask.cancel(true);
            checkThread.interrupt();
            checkThread.join(TimeUnit.SECONDS.toMillis(5));
            assertThat("deadline-check thread did not terminate", checkThread.isAlive(), is(false));
        }
    }

    @Test
    void doesNotRejectFromDeadlineResetAfterEmptyAttemptInstallsIt() throws Exception {
        long timeout = Duration.ofMillis(150).toNanos();
        AtomicLong nanoTime = new AtomicLong();
        AtomicInteger timeReads = new AtomicInteger();
        CountDownLatch deadlineInstalled = new CountDownLatch(1);
        CountDownLatch releaseTimeRead = new CountDownLatch(1);
        AdmissionTimeoutBudget budget = new AdmissionTimeoutBudget("orders", () -> {
            if (timeReads.incrementAndGet() == 2) {
                deadlineInstalled.countDown();
                await(releaseTimeRead, "release post-install clock read");
            }
            return nanoTime.get();
        });
        FutureTask<Optional<String>> startTask = new FutureTask<>(() ->
                budget.attempt(() -> timeout, ignored -> Optional.empty()));
        Thread startThread = Thread.ofVirtual().name("messaging-admission-deadline-start").unstarted(startTask);

        try {
            startThread.start();
            await(deadlineInstalled, "deadline installation");
            budget.reset();
            nanoTime.set(timeout);
            releaseTimeRead.countDown();

            assertThat(startTask.get(5, TimeUnit.SECONDS).isEmpty(), is(true));
            AtomicLong remainingTimeout = new AtomicLong();
            assertThat(budget.attempt(() -> timeout, remaining -> {
                remainingTimeout.set(remaining);
                return Optional.empty();
            }).isEmpty(), is(true));
            assertThat(remainingTimeout.get(), is(timeout));
        } finally {
            releaseTimeRead.countDown();
            startTask.cancel(true);
            startThread.interrupt();
            startThread.join(TimeUnit.SECONDS.toMillis(5));
            assertThat("deadline-start thread did not terminate", startThread.isAlive(), is(false));
        }
    }

    private static void await(CountDownLatch latch, String operation) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for " + operation);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for " + operation, e);
        }
    }

    private static <T> Optional<T> attempt(AdmissionTimeoutBudget budget,
                                           long timeout,
                                           List<Long> remainingTimeouts,
                                           Optional<T> result) {
        return budget.attempt(() -> timeout, remaining -> {
            remainingTimeouts.add(remaining);
            return result;
        });
    }
}
