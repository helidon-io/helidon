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
