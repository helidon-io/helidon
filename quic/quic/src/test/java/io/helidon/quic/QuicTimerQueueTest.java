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

package io.helidon.quic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@Isolated("Changes JUL logger levels")
@ResourceLock("java.util.logging")
class QuicTimerQueueTest {
    private static final QuicServerRuntime.ConnectionPermit NOOP_CONNECTION_PERMIT =
            QuicServerRuntime.ConnectionPermit.accepted(() -> { }, () -> { });

    @Test
    void rejectsNullProcessingInputsBeforeChangingQueueState() {
        QuicTimerQueue queue = new QuicTimerQueue(() -> {
        }, () -> "test");

        assertThrows(NullPointerException.class,
                     () -> queue.processEventsAndReturnNextDeadline(null, Runnable::run));
        assertThrows(NullPointerException.class,
                     () -> queue.processEventsAndReturnNextDeadline(Deadline.MAX, (Executor) null));

        assertThat(queue.processEventsAndReturnNextDeadline(Deadline.MAX, Runnable::run),
                   sameInstance(Deadline.MAX));
    }

    @Test
    void directlyRemovesAHandshakeDeadline() {
        QuicTimerQueue queue = new QuicTimerQueue(() -> { }, () -> "test");
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(1, Duration.ofHours(1), _ -> { });
        QuicServerHandshakeAdmission.Permit permit = acquire(admission);

        assertThat(permit.arm(queue), is(true));
        assertThat(queue.nextDeadline(), sameInstance(permit.deadline()));
        assertThat(queue.cancel(permit), is(true));
        assertThat(queue.nextDeadline(), sameInstance(Deadline.MAX));

        permit.release();
        queue.stop();
    }

    @Test
    void cancellationCanRemoveAnEventAlreadyQueuedForExecution() {
        AtomicInteger invocations = new AtomicInteger();
        AtomicReference<Runnable> processor = new AtomicReference<>();
        QuicTimerQueue queue = new QuicTimerQueue(() -> { }, () -> "test");
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(1, Duration.ofNanos(1), _ -> invocations.incrementAndGet());
        QuicServerHandshakeAdmission.Permit permit = acquire(admission);
        assertThat(permit.arm(queue), is(true));

        queue.processEventsAndReturnNextDeadline(Deadline.MAX, processor::set);
        assertThat(queue.cancel(permit), is(true));
        processor.get().run();

        assertThat(invocations.get(), is(0));
        permit.release();
        queue.stop();
    }

    @Test
    void batchExpiryDoesNotCancelEventsAlreadyClaimedForExecution() {
        int batchSize = 32;
        AtomicInteger invocations = new AtomicInteger();
        AtomicReference<Runnable> processor = new AtomicReference<>();
        QuicTimerQueue queue = spy(new QuicTimerQueue(() -> { }, () -> "test"));
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(batchSize,
                                                 Duration.ofHours(1),
                                                 permit -> {
                                                     invocations.incrementAndGet();
                                                     permit.release();
                                                 });
        for (int i = 0; i < batchSize; i++) {
            acquire(admission).arm(queue);
        }

        queue.processEventsAndReturnNextDeadline(Deadline.MAX, processor::set);
        processor.get().run();

        assertThat(invocations.get(), is(batchSize));
        assertThat(queue.nextDeadline(), sameInstance(Deadline.MAX));
        verify(queue, never()).cancel(any());
        List<QuicServerHandshakeAdmission.Permit> recovered = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            recovered.add(acquire(admission));
        }
        recovered.forEach(QuicServerHandshakeAdmission.Permit::release);
        queue.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INFO", "FINE", "FINER"})
    void earlierDeadlineNotifiesTimerAndCanceledEventDoesNotRun(String level) {
        AtomicInteger notifications = new AtomicInteger();
        AtomicInteger logTags = new AtomicInteger();
        List<String> expired = new ArrayList<>();
        List<Runnable> processors = new ArrayList<>();
        QuicTimerQueue queue = new QuicTimerQueue(notifications::incrementAndGet,
                                                () -> "timer-test-" + logTags.incrementAndGet());
        var first = acquire(new QuicServerHandshakeAdmission(1, Duration.ofHours(2), _ -> expired.add("first")));
        var later = acquire(new QuicServerHandshakeAdmission(1, Duration.ofHours(3), _ -> expired.add("later")));
        var earlier = acquire(new QuicServerHandshakeAdmission(1, Duration.ofHours(1), _ -> expired.add("earlier")));

        try (var _ = new TestLogLevel(Level.parse(level))) {
            assertThat(first.arm(queue), is(true));
            assertThat(notifications.get(), is(1));
            assertThat(queue.processEventsAndReturnNextDeadline(Deadline.MIN, processors::add),
                       sameInstance(first.deadline()));
            assertThat(processors, empty());

            assertThat(later.arm(queue), is(true));
            assertThat(notifications.get(), is(1));
            assertThat(earlier.arm(queue), is(true));
            assertThat(notifications.get(), is(2));
            assertThat(queue.nextDeadline(), sameInstance(earlier.deadline()));

            assertThat(queue.processEventsAndReturnNextDeadline(earlier.deadline().plusNanos(-1), processors::add),
                       sameInstance(earlier.deadline()));
            assertThat(processors, empty());
            assertThat(queue.processEventsAndReturnNextDeadline(earlier.deadline(), processors::add),
                       sameInstance(first.deadline()));
            assertThat(processors.size(), is(1));
            assertThat(queue.cancel(earlier), is(true));
            processors.removeFirst().run();
            assertThat(expired, empty());

            assertThat(queue.processEventsAndReturnNextDeadline(first.deadline(), Runnable::run),
                       sameInstance(later.deadline()));
            assertThat(expired, contains("first"));
            assertThat(queue.cancel(later), is(true));
            assertThat(queue.processEventsAndReturnNextDeadline(Deadline.MAX, Runnable::run),
                       sameInstance(Deadline.MAX));
            assertThat(expired, contains("first"));
            assertThat(notifications.get(), is(2));
            if (level.equals("INFO")) {
                assertThat(logTags.get(), is(0));
            } else {
                assertThat(logTags.get(), greaterThan(0));
            }
        } finally {
            first.release();
            later.release();
            earlier.release();
            queue.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"INFO", "FINE", "FINER"})
    void reschedulingAndCancelingPreserveHandshakeExpiry(String level) {
        AtomicInteger notifications = new AtomicInteger();
        AtomicInteger expirations = new AtomicInteger();
        QuicTimerQueue queue = new QuicTimerQueue(notifications::incrementAndGet, () -> "timer-test");
        var permit = acquire(new QuicServerHandshakeAdmission(1,
                                                            Duration.ofHours(1),
                                                            _ -> expirations.incrementAndGet()));

        try (var _ = new TestLogLevel(Level.parse(level))) {
            assertThat(permit.arm(queue), is(true));
            assertThat(queue.processEventsAndReturnNextDeadline(Deadline.MIN, Runnable::run),
                       sameInstance(permit.deadline()));

            queue.reschedule(permit, permit.deadline());
            assertThat(notifications.get(), is(1));
            assertThat(queue.pendingScheduledDeadline(), sameInstance(permit.deadline()));
            queue.reschedule(permit);
            assertThat(notifications.get(), is(2));
            assertThat(queue.cancel(permit), is(true));
            assertThat(queue.processEventsAndReturnNextDeadline(Deadline.MAX, Runnable::run),
                       sameInstance(Deadline.MAX));
            assertThat(expirations.get(), is(0));

            queue.reschedule(permit, permit.deadline());
            assertThat(notifications.get(), is(3));
            assertThat(queue.processEventsAndReturnNextDeadline(Deadline.MIN, Runnable::run),
                       sameInstance(permit.deadline()));
            assertThat(queue.processEventsAndReturnNextDeadline(permit.deadline(), Runnable::run),
                       sameInstance(Deadline.MAX));
            assertThat(expirations.get(), is(1));
            assertThat(queue.processEventsAndReturnNextDeadline(Deadline.MAX, Runnable::run),
                       sameInstance(Deadline.MAX));
            assertThat(expirations.get(), is(1));
        } finally {
            permit.release();
            queue.stop();
        }
    }

    private static QuicServerHandshakeAdmission.Permit acquire(QuicServerHandshakeAdmission admission) {
        long reservation = admission.tryReserve();
        if (reservation == QuicServerHandshakeAdmission.NO_RESERVATION) {
            return null;
        }
        return admission.materializePermit(reservation, NOOP_CONNECTION_PERMIT);
    }

    private static final class TestLogLevel implements AutoCloseable {
        private final Logger logger = Logger.getLogger(QuicTimerQueue.class.getName());
        private final Level previousLevel = logger.getLevel();
        private final boolean previousUseParentHandlers = logger.getUseParentHandlers();

        private TestLogLevel(Level level) {
            logger.setUseParentHandlers(false);
            logger.setLevel(level);
        }

        @Override
        public void close() {
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }
    }
}
