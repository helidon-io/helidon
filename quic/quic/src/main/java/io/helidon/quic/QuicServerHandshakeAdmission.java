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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class QuicServerHandshakeAdmission {
    static final long NO_RESERVATION = 0;

    private static final VarHandle CONNECTION;

    static {
        try {
            CONNECTION = MethodHandles.lookup().findVarHandle(Permit.class, "connection", QuicConnection.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final int limit;
    private final long timeoutNanos;
    private final Consumer<Permit> timeoutDispatcher;
    private final AtomicInteger pending = new AtomicInteger();

    QuicServerHandshakeAdmission(int limit, Duration timeout, Consumer<Permit> timeoutDispatcher) {
        if (limit < 1) {
            throw new IllegalArgumentException("maxPendingHandshakes must be greater than 0: " + limit);
        }
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("handshakeTimeout must be positive: " + timeout);
        }
        this.limit = limit;
        try {
            this.timeoutNanos = timeout.toNanos();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("handshakeTimeout must fit in nanoseconds: " + timeout, e);
        }
        this.timeoutDispatcher = Objects.requireNonNull(timeoutDispatcher, "timeoutDispatcher");
    }

    long tryReserve() {
        int current = pending.get();
        for (;;) {
            if (current >= limit) {
                return NO_RESERVATION;
            }
            long reservationNanos = System.nanoTime();
            if (reservationNanos == NO_RESERVATION) {
                // Preserve elapsed-time ordering if the next coarse clock sample is also zero.
                reservationNanos = -1;
            }
            if (pending.compareAndSet(current, current + 1)) {
                return reservationNanos;
            }
            current = pending.get();
        }
    }

    void releaseReservation(long reservationNanos) {
        if (reservationNanos == NO_RESERVATION) {
            throw new IllegalArgumentException("QUIC server handshake reservation is absent");
        }
        pending.decrementAndGet();
    }

    Permit materializePermit(long reservationNanos, QuicServerRuntime.ConnectionPermit connectionPermit) {
        if (reservationNanos == NO_RESERVATION) {
            throw new IllegalArgumentException("QUIC server handshake reservation is absent");
        }
        try {
            QuicServerRuntime.ConnectionPermit acceptedPermit =
                    Objects.requireNonNull(connectionPermit, "connectionPermit");
            long currentNanos = System.nanoTime();
            Deadline current = TimeSource.source().instant(currentNanos);
            long elapsedNanos = currentNanos - reservationNanos;
            long remainingNanos = elapsedNanos >= 0 && elapsedNanos < timeoutNanos
                    ? timeoutNanos - elapsedNanos
                    : 0;
            return new Permit(current.plusNanos(remainingNanos), acceptedPermit);
        } catch (RuntimeException | Error failure) {
            releaseReservation(reservationNanos);
            if (connectionPermit != null) {
                try {
                    connectionPermit.releaseBeforeEstablished();
                } catch (Throwable releaseFailure) {
                    if (releaseFailure != failure) {
                        failure.addSuppressed(releaseFailure);
                    }
                }
            }
            throw failure;
        }
    }

    final class Permit implements QuicTimedEvent {
        private final long eventId = QuicTimerQueue.newEventId();
        private final Deadline deadline;
        private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
        private final AtomicReference<Object> timerRegistration = new AtomicReference<>();
        private final QuicServerRuntime.ConnectionPermit connectionPermit;
        private volatile QuicConnection connection;

        private Permit(Deadline deadline, QuicServerRuntime.ConnectionPermit connectionPermit) {
            this.deadline = deadline;
            this.connectionPermit = connectionPermit;
        }

        void connection(QuicConnection connection) {
            Objects.requireNonNull(connection, "connection");
            if (state.get() != State.PENDING || !CONNECTION.compareAndSet(this, null, connection)) {
                throw new IllegalStateException("QUIC server handshake connection is already set");
            }
            if (state.get() != State.PENDING && CONNECTION.compareAndSet(this, connection, null)) {
                throw new IllegalStateException("QUIC server handshake connection is already detached");
            }
        }

        QuicConnection takeConnection() {
            return (QuicConnection) CONNECTION.getAndSet(this, null);
        }

        boolean arm(QuicTimerQueue timerQueue) {
            Objects.requireNonNull(timerQueue, "timerQueue");
            if (!timerRegistration.compareAndSet(null, TimerRegistration.ARMING)) {
                if (timerRegistration.compareAndSet(TimerRegistration.CANCELLED_BEFORE_ARM,
                                                    TimerRegistration.CANCELLED)) {
                    return false;
                }
                throw new IllegalStateException("QUIC server handshake deadline is already armed");
            }
            if (state.get() != State.PENDING) {
                timerRegistration.compareAndSet(TimerRegistration.ARMING, TimerRegistration.CANCELLED);
                return false;
            }
            try {
                timerQueue.offer(this);
            } catch (RuntimeException | Error failure) {
                boolean cancelOffered;
                for (;;) {
                    Object current = timerRegistration.get();
                    if (current == TimerRegistration.HANDLED) {
                        cancelOffered = false;
                        break;
                    }
                    if (current == TimerRegistration.CANCELLED) {
                        cancelOffered = true;
                        break;
                    }
                    if (timerRegistration.compareAndSet(current, TimerRegistration.CANCELLED)) {
                        cancelOffered = true;
                        break;
                    }
                }
                if (cancelOffered) {
                    try {
                        timerQueue.cancel(this);
                    } catch (RuntimeException | Error cancellationFailure) {
                        failure.addSuppressed(cancellationFailure);
                    }
                }
                throw failure;
            }
            for (;;) {
                Object current = timerRegistration.get();
                if (current == TimerRegistration.ARMING) {
                    if (!timerRegistration.compareAndSet(TimerRegistration.ARMING, timerQueue)) {
                        continue;
                    }
                    if (state.get() != State.PENDING) {
                        cancelDeadline();
                        return false;
                    }
                    return true;
                }
                if (current == TimerRegistration.HANDLED) {
                    return false;
                }
                if (current == TimerRegistration.CANCELLED) {
                    timerQueue.cancel(this);
                    return false;
                }
                throw new IllegalStateException("Invalid QUIC server handshake deadline registration " + current);
            }
        }

        Establishment establish() {
            Establishment establishment = claimEstablishment();
            completeEstablishment(establishment);
            return establishment;
        }

        Establishment claimEstablishment() {
            State next = deadline.compareTo(TimeSource.now()) <= 0 ? State.EXPIRING : State.ESTABLISHED;
            if (!state.compareAndSet(State.PENDING, next)) {
                return Establishment.LOST;
            }
            if (next == State.EXPIRING) {
                return Establishment.EXPIRED;
            }
            return Establishment.ESTABLISHED;
        }

        void completeEstablishment(Establishment establishment) {
            if (establishment == Establishment.EXPIRED) {
                cancelDeadline();
                return;
            }
            if (establishment != Establishment.ESTABLISHED) {
                return;
            }
            try {
                cancelDeadline();
            } finally {
                pending.decrementAndGet();
            }
        }

        ReleaseClaim claimRelease() {
            State previous;
            do {
                previous = state.get();
                if (previous == State.RELEASE_CLAIMED || previous == State.RELEASED) {
                    return null;
                }
            } while (!state.compareAndSet(previous, State.RELEASE_CLAIMED));
            takeConnection();
            return new ReleaseClaim(this, previous);
        }

        void completeRelease(ReleaseClaim claim) {
            Objects.requireNonNull(claim, "claim");
            if (claim.permit != this) {
                throw new IllegalArgumentException("QUIC server handshake release claim belongs to another permit");
            }
            if (!state.compareAndSet(State.RELEASE_CLAIMED, State.RELEASED)) {
                return;
            }
            try {
                cancelDeadline();
            } finally {
                if (claim.previous != State.ESTABLISHED) {
                    pending.decrementAndGet();
                }
            }
            if (claim.previous == State.ESTABLISHED) {
                connectionPermit.releaseEstablished();
            } else {
                connectionPermit.releaseBeforeEstablished();
            }
        }

        void release() {
            ReleaseClaim claim = claimRelease();
            if (claim != null) {
                completeRelease(claim);
            }
        }

        @Override
        public Deadline deadline() {
            return deadline;
        }

        @Override
        public Deadline handle() {
            for (;;) {
                Object current = timerRegistration.get();
                if (current == TimerRegistration.CANCELLED
                        || current == TimerRegistration.CANCELLED_BEFORE_ARM) {
                    return Deadline.MAX;
                }
                if (current == TimerRegistration.HANDLED) {
                    break;
                }
                if (timerRegistration.compareAndSet(current, TimerRegistration.HANDLED)) {
                    break;
                }
            }
            if (state.compareAndSet(State.PENDING, State.EXPIRING)) {
                timeoutDispatcher.accept(this);
            }
            return Deadline.MAX;
        }

        @Override
        public long eventId() {
            return eventId;
        }

        @Override
        public Deadline refreshDeadline() {
            return state.get() == State.PENDING ? deadline : Deadline.MAX;
        }

        private void cancelDeadline() {
            for (;;) {
                Object current = timerRegistration.get();
                if (current == TimerRegistration.HANDLED
                        || current == TimerRegistration.CANCELLED
                        || current == TimerRegistration.CANCELLED_BEFORE_ARM) {
                    return;
                }
                TimerRegistration next = current == null
                        ? TimerRegistration.CANCELLED_BEFORE_ARM
                        : TimerRegistration.CANCELLED;
                if (!timerRegistration.compareAndSet(current, next)) {
                    continue;
                }
                if (current instanceof QuicTimerQueue currentTimerQueue) {
                    currentTimerQueue.cancel(this);
                }
                return;
            }
        }

        private enum TimerRegistration {
            ARMING,
            HANDLED,
            CANCELLED_BEFORE_ARM,
            CANCELLED
        }

        private enum State {
            PENDING,
            EXPIRING,
            ESTABLISHED,
            RELEASE_CLAIMED,
            RELEASED
        }
    }

    static final class ReleaseClaim {
        private final Permit permit;
        private final Permit.State previous;

        private ReleaseClaim(Permit permit, Permit.State previous) {
            this.permit = permit;
            this.previous = previous;
        }

        boolean established() {
            return previous == Permit.State.ESTABLISHED;
        }
    }

    enum Establishment {
        ESTABLISHED,
        EXPIRED,
        LOST
    }
}
