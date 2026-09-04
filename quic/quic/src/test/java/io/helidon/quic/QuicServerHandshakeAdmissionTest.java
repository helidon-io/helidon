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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class QuicServerHandshakeAdmissionTest {
    private static final QuicServerRuntime.ConnectionPermit NOOP_CONNECTION_PERMIT =
            QuicServerRuntime.ConnectionPermit.accepted(() -> { }, () -> { });

    @Test
    void enforcesTheConfiguredReservationBoundAndRecoversCapacity() {
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(2, Duration.ofSeconds(10), _ -> { });

        long first = admission.tryReserve();
        long second = admission.tryReserve();

        assertThat(first, is(not(QuicServerHandshakeAdmission.NO_RESERVATION)));
        assertThat(second, is(not(QuicServerHandshakeAdmission.NO_RESERVATION)));
        assertThat(admission.tryReserve(), is(QuicServerHandshakeAdmission.NO_RESERVATION));

        admission.releaseReservation(first);
        long replacement = admission.tryReserve();
        assertThat(replacement, is(not(QuicServerHandshakeAdmission.NO_RESERVATION)));
        assertThat(admission.tryReserve(), is(QuicServerHandshakeAdmission.NO_RESERVATION));

        admission.releaseReservation(second);
        admission.releaseReservation(replacement);
        long recovered = admission.tryReserve();
        assertThat(recovered, is(not(QuicServerHandshakeAdmission.NO_RESERVATION)));
        admission.releaseReservation(recovered);
    }

    @Test
    void dispatchesWholeDueBatchBeforeCleanupReturnsCapacity() {
        int limit = 4;
        List<QuicServerHandshakeAdmission.Permit> dispatched = new ArrayList<>();
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(limit, Duration.ofMinutes(1), dispatched::add);
        QuicTimerQueue timerQueue = new QuicTimerQueue(() -> { }, () -> "handshake-dispatch-test");
        List<QuicServerHandshakeAdmission.Permit> permits = new ArrayList<>();
        try {
            for (int i = 0; i < limit; i++) {
                QuicServerHandshakeAdmission.Permit permit = acquire(admission);
                assertThat(permit, notNullValue());
                assertThat(permit.arm(timerQueue), is(true));
                permits.add(permit);
            }

            timerQueue.processEventsAndReturnNextDeadline(Deadline.MAX, Runnable::run);

            assertThat(dispatched, is(permits));
            assertThat(acquire(admission), nullValue());

            dispatched.forEach(QuicServerHandshakeAdmission.Permit::release);
            List<QuicServerHandshakeAdmission.Permit> recovered = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                QuicServerHandshakeAdmission.Permit permit = acquire(admission);
                assertThat(permit, notNullValue());
                recovered.add(permit);
            }
            assertThat(acquire(admission), nullValue());
            recovered.forEach(QuicServerHandshakeAdmission.Permit::release);
        } finally {
            permits.forEach(QuicServerHandshakeAdmission.Permit::release);
            timerQueue.stop();
        }
    }

    @Test
    void selectsTheExactExternalPermitReleasePath() {
        AtomicInteger beforeEstablished = new AtomicInteger();
        AtomicInteger established = new AtomicInteger();
        AtomicInteger timedOut = new AtomicInteger();
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(2, Duration.ofSeconds(10), _ -> timedOut.incrementAndGet());
        QuicServerHandshakeAdmission.Permit successful = acquire(
                admission,
                QuicServerRuntime.ConnectionPermit.accepted(beforeEstablished::incrementAndGet,
                                                             established::incrementAndGet));
        assertThat(successful.establish(), is(QuicServerHandshakeAdmission.Establishment.ESTABLISHED));
        successful.release();
        successful.release();

        QuicServerHandshakeAdmission.Permit expired = acquire(
                admission,
                QuicServerRuntime.ConnectionPermit.accepted(beforeEstablished::incrementAndGet,
                                                             established::incrementAndGet));
        assertThat(expired.handle(), is(Deadline.MAX));
        assertThat(expired.establish(), is(QuicServerHandshakeAdmission.Establishment.LOST));
        expired.release();
        expired.release();

        assertThat(timedOut.get(), is(1));
        assertThat(beforeEstablished.get(), is(1));
        assertThat(established.get(), is(1));
        QuicServerHandshakeAdmission.Permit recovered = acquire(admission);
        assertThat(recovered, notNullValue());
        recovered.release();
    }

    @Test
    void serializesEstablishmentAgainstExpiry() throws Exception {
        for (int i = 0; i < 100; i++) {
            AtomicBoolean timedOut = new AtomicBoolean();
            QuicServerHandshakeAdmission admission =
                    new QuicServerHandshakeAdmission(1, Duration.ofSeconds(10), _ -> timedOut.set(true));
            QuicServerHandshakeAdmission.Permit permit = acquire(admission);
            CountDownLatch start = new CountDownLatch(1);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var established = executor.submit(() -> {
                    start.await();
                    return permit.establish() == QuicServerHandshakeAdmission.Establishment.ESTABLISHED;
                });
                var expired = executor.submit(() -> {
                    start.await();
                    permit.handle();
                    return timedOut.get();
                });

                start.countDown();
                assertThat(established.get(10, TimeUnit.SECONDS), is(!expired.get(10, TimeUnit.SECONDS)));
            }
            permit.release();
            QuicServerHandshakeAdmission.Permit recovered = acquire(admission);
            assertThat(recovered, notNullValue());
            recovered.release();
        }
    }

    @Test
    void preservesAbsoluteDeadlineAcrossDelayedMaterialization() {
        Duration timeout = Duration.ofSeconds(10);
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(1, timeout, _ -> { });
        long reservation = admission.tryReserve();
        assertThat(reservation, is(not(QuicServerHandshakeAdmission.NO_RESERVATION)));
        long delayedReservation = reservation - Duration.ofSeconds(1).toNanos();
        Deadline expectedDeadline = TimeSource.source().instant(delayedReservation).plusNanos(timeout.toNanos());

        QuicServerHandshakeAdmission.Permit permit =
                admission.materializePermit(delayedReservation, NOOP_CONNECTION_PERMIT);

        assertThat(permit.deadline(), is(expectedDeadline));
        permit.release();
    }

    @Test
    void expiresPermitWhenMaterializationOutlivesTimeout() {
        QuicTimerQueue timerQueue = mock(QuicTimerQueue.class);
        AtomicBoolean timedOut = new AtomicBoolean();
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(1, Duration.ofMillis(100), _ -> timedOut.set(true));
        long reservation = admission.tryReserve();
        assertThat(reservation, is(not(QuicServerHandshakeAdmission.NO_RESERVATION)));
        long expiredReservation = reservation - Duration.ofSeconds(1).toNanos();
        QuicServerHandshakeAdmission.Permit permit =
                admission.materializePermit(expiredReservation, NOOP_CONNECTION_PERMIT);
        assertThat(permit.arm(timerQueue), is(true));

        assertThat(permit.establish(), is(QuicServerHandshakeAdmission.Establishment.EXPIRED));
        assertThat(timedOut.get(), is(false));
        permit.handle();
        assertThat(timedOut.get(), is(false));
        permit.release();

        verify(timerQueue, times(1)).offer(permit);
        verify(timerQueue, times(1)).cancel(permit);
        QuicServerHandshakeAdmission.Permit recovered = acquire(admission);
        assertThat(recovered, notNullValue());
        recovered.release();
    }

    @Test
    void releaseClaimBlocksEstablishmentUntilCleanupReturnsCapacity() {
        AtomicInteger beforeEstablished = new AtomicInteger();
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(1, Duration.ofSeconds(10), _ -> { });
        QuicServerHandshakeAdmission.Permit permit = acquire(
                admission,
                QuicServerRuntime.ConnectionPermit.accepted(beforeEstablished::incrementAndGet, () -> { }));

        QuicServerHandshakeAdmission.ReleaseClaim release = permit.claimRelease();

        assertThat(release, notNullValue());
        assertThat(permit.establish(), is(QuicServerHandshakeAdmission.Establishment.LOST));
        assertThat(acquire(admission), nullValue());

        permit.completeRelease(release);
        permit.completeRelease(release);
        assertThat(beforeEstablished.get(), is(1));

        QuicServerHandshakeAdmission.Permit recovered = acquire(admission);
        assertThat(recovered, notNullValue());
        recovered.release();
    }

    @Test
    void releaseClaimDetachesTheConnectionAndRejectsReassignment() {
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(1, Duration.ofSeconds(10), _ -> { });
        QuicServerHandshakeAdmission.Permit permit = acquire(admission);
        QuicConnection connection = mock(QuicConnection.class);
        permit.connection(connection);

        QuicServerHandshakeAdmission.ReleaseClaim release = permit.claimRelease();

        assertThat(release, notNullValue());
        assertThat(permit.takeConnection(), nullValue());
        assertThrows(IllegalStateException.class, () -> permit.connection(connection));
        permit.completeRelease(release);
    }

    @Test
    void timeoutDispatcherTakesConnectionOwnershipOnlyOnce() {
        AtomicReference<QuicConnection> dispatched = new AtomicReference<>();
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(1,
                                                 Duration.ofSeconds(10),
                                                 permit -> dispatched.set(permit.takeConnection()));
        QuicServerHandshakeAdmission.Permit permit = acquire(admission);
        QuicConnection connection = mock(QuicConnection.class);
        permit.connection(connection);

        assertThat(permit.handle(), is(Deadline.MAX));

        assertThat(dispatched.get(), sameInstance(connection));
        assertThat(permit.takeConnection(), nullValue());
        permit.release();
    }

    @Test
    void claimedExpiryDetachesBeforeCleanup() {
        QuicTimerQueue timerQueue = mock(QuicTimerQueue.class);
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(1,
                                                 Duration.ofSeconds(10),
                                                 QuicServerHandshakeAdmission.Permit::release);
        QuicServerHandshakeAdmission.Permit permit = acquire(admission);

        assertThat(permit.arm(timerQueue), is(true));
        assertThat(permit.handle(), is(Deadline.MAX));

        verify(timerQueue, times(1)).offer(permit);
        verify(timerQueue, never()).cancel(permit);
        QuicServerHandshakeAdmission.Permit recovered = acquire(admission);
        assertThat(recovered, notNullValue());
        recovered.release();
    }

    @Test
    void successfulHandshakeCancelsDeadlineOnlyOnce() {
        QuicTimerQueue timerQueue = mock(QuicTimerQueue.class);
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(1, Duration.ofSeconds(10), _ -> { });
        QuicServerHandshakeAdmission.Permit permit = acquire(admission);

        assertThat(permit.arm(timerQueue), is(true));
        assertThat(permit.establish(), is(QuicServerHandshakeAdmission.Establishment.ESTABLISHED));
        permit.release();
        permit.release();

        verify(timerQueue, times(1)).offer(permit);
        verify(timerQueue, times(1)).cancel(permit);
    }

    @Test
    void establishmentClaimDefersTimerCancellationAndCapacityRelease() {
        QuicTimerQueue timerQueue = mock(QuicTimerQueue.class);
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(1, Duration.ofSeconds(10), _ -> { });
        QuicServerHandshakeAdmission.Permit permit = acquire(admission);
        assertThat(permit.arm(timerQueue), is(true));

        QuicServerHandshakeAdmission.Establishment establishment = permit.claimEstablishment();

        assertThat(establishment, is(QuicServerHandshakeAdmission.Establishment.ESTABLISHED));
        assertThat(acquire(admission), nullValue());
        verify(timerQueue, never()).cancel(permit);

        permit.completeEstablishment(establishment);

        verify(timerQueue, times(1)).cancel(permit);
        QuicServerHandshakeAdmission.Permit recovered = acquire(admission);
        assertThat(recovered, notNullValue());
        permit.release();
        recovered.release();
    }

    @Test
    void releaseDuringOfferIsRemovedByArmingOwner() throws Exception {
        QuicTimerQueue timerQueue = mock(QuicTimerQueue.class);
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(1, Duration.ofSeconds(10), _ -> { });
        QuicServerHandshakeAdmission.Permit permit = acquire(admission);
        CountDownLatch offerEntered = new CountDownLatch(1);
        CountDownLatch allowOffer = new CountDownLatch(1);
        doAnswer(_ -> {
            offerEntered.countDown();
            assertThat(allowOffer.await(5, TimeUnit.SECONDS), is(true));
            return null;
        }).when(timerQueue).offer(permit);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var armed = executor.submit(() -> permit.arm(timerQueue));
            assertThat(offerEntered.await(5, TimeUnit.SECONDS), is(true));
            permit.release();
            allowOffer.countDown();

            assertThat(armed.get(5, TimeUnit.SECONDS), is(false));
        }
        verify(timerQueue, times(1)).cancel(permit);
    }

    @Test
    void claimedDuringOfferDoesNotCancel() throws Exception {
        QuicTimerQueue timerQueue = mock(QuicTimerQueue.class);
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(1,
                                                 Duration.ofSeconds(10),
                                                 QuicServerHandshakeAdmission.Permit::release);
        QuicServerHandshakeAdmission.Permit permit = acquire(admission);
        CountDownLatch offerEntered = new CountDownLatch(1);
        CountDownLatch allowOffer = new CountDownLatch(1);
        doAnswer(_ -> {
            offerEntered.countDown();
            assertThat(allowOffer.await(5, TimeUnit.SECONDS), is(true));
            return null;
        }).when(timerQueue).offer(permit);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var armed = executor.submit(() -> permit.arm(timerQueue));
            assertThat(offerEntered.await(5, TimeUnit.SECONDS), is(true));
            assertThat(permit.handle(), is(Deadline.MAX));
            allowOffer.countDown();

            assertThat(armed.get(5, TimeUnit.SECONDS), is(false));
        }
        verify(timerQueue, never()).cancel(permit);
    }

    @Test
    void offerFailureRemovesPartiallyRegisteredDeadline() {
        RuntimeException failure = new IllegalStateException("timer notification failed");
        AtomicInteger timeouts = new AtomicInteger();
        QuicTimerQueue timerQueue = new QuicTimerQueue(() -> {
            throw failure;
        }, () -> "handshake-admission-test");
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(1, Duration.ofSeconds(10), _ -> timeouts.incrementAndGet());
        QuicServerHandshakeAdmission.Permit permit = acquire(admission);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> permit.arm(timerQueue));

        assertThat(thrown, sameInstance(failure));
        assertThat(timerQueue.nextDeadline(), sameInstance(Deadline.MAX));
        assertThat(permit.handle(), sameInstance(Deadline.MAX));
        assertThat(timeouts.get(), is(0));
        permit.release();
        timerQueue.stop();
    }

    @Test
    void releaseBeforeArmRejectsOnlyTheFirstArmWithoutOffering() {
        QuicTimerQueue timerQueue = mock(QuicTimerQueue.class);
        QuicServerHandshakeAdmission admission =
                new QuicServerHandshakeAdmission(1, Duration.ofSeconds(10), _ -> { });
        QuicServerHandshakeAdmission.Permit permit = acquire(admission);

        permit.release();

        assertThat(permit.arm(timerQueue), is(false));
        assertThrows(IllegalStateException.class, () -> permit.arm(timerQueue));
        verify(timerQueue, never()).offer(permit);
        verify(timerQueue, never()).cancel(permit);
    }

    private static QuicServerHandshakeAdmission.Permit acquire(QuicServerHandshakeAdmission admission) {
        return acquire(admission, NOOP_CONNECTION_PERMIT);
    }

    private static QuicServerHandshakeAdmission.Permit acquire(
            QuicServerHandshakeAdmission admission,
            QuicServerRuntime.ConnectionPermit connectionPermit) {
        long reservation = admission.tryReserve();
        if (reservation == QuicServerHandshakeAdmission.NO_RESERVATION) {
            return null;
        }
        return admission.materializePermit(reservation, connectionPermit);
    }
}
