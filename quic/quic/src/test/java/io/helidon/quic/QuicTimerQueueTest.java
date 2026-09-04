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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

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

    private static QuicServerHandshakeAdmission.Permit acquire(QuicServerHandshakeAdmission admission) {
        long reservation = admission.tryReserve();
        if (reservation == QuicServerHandshakeAdmission.NO_RESERVATION) {
            return null;
        }
        return admission.materializePermit(reservation, NOOP_CONNECTION_PERMIT);
    }
}
