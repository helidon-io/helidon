/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

 /**
 * Keeps track of activity on a {@code QuicConnectionImpl} and manages
 * the idle timeout of the QUIC connection.
 */
final class IdleTimeoutManager {

    private static final long NO_IDLE_TIMEOUT = 0;
    private static final System.Logger LOGGER = System.getLogger(IdleTimeoutManager.class.getName());

    private final QuicConnectionImpl connection;
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final AtomicBoolean outboundRestartAvailable = new AtomicBoolean();
    private final AtomicLong idleTimeoutDurationMs = new AtomicLong();
    private final ReentrantLock stateLock = new ReentrantLock();
    private final ReentrantLock idleTerminationLock = new ReentrantLock();
    private final AtomicReference<Supplier<Boolean>> trafficGenerationCheck = new AtomicReference<>();
    // must be accessed only when holding stateLock
    private IdleTimeoutEvent idleTimeoutEvent;
    // must be accessed only when holding stateLock
    private StreamDataBlockedEvent streamDataBlockedEvent;
    // the time (in nanos) at which an incoming packet was processed or the first
    // subsequent ACK-eliciting packet was sent
    private volatile long lastPacketActivityNanos;
    // true if it has been decided to terminate the connection due to being idle,
    // false otherwise. should be accessed only when holding the idleTerminationLock
    private boolean chosenForIdleTermination;
    // the time (in nanos) at which the connection was last reserved for use.
    // should be accessed only when holding the idleTerminationLock
    private long lastUsageReservationNanos;
    // must be accessed only when holding stateLock
    private PingEvent pingEvent;

    IdleTimeoutManager(QuicConnectionImpl connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    /**
     * Starts the idle timeout management for the connection. This should be called
     * after the handshake is complete for the connection.
     *
     * @throws IllegalStateException if handshake hasn't yet completed or if the handshake
     *                              has failed for the connection
     */
    void start() {
        // start idle management only for successfully completed handshake
        requireSuccessfulHandshake();
        if (shutdown.get()) {
            return;
        }
        this.stateLock.lock();
        try {
            if (shutdown.get()) {
                return;
            }
            startIdleTerminationTimer();
            startStreamDataBlockedTimer();
        } finally {
            this.stateLock.unlock();
        }
    }

    // set up a PING timer if the application layer's max idle duration for the connection
    // is larger than that of the negotiated QUIC idle timeout for that connection
    void appLayerMaxIdle(Duration maxIdle, Supplier<Boolean> trafficGenerationCheck) {
        Objects.requireNonNull(maxIdle, "maxIdle");
        Objects.requireNonNull(trafficGenerationCheck, "trafficGenerationCheck");
        if (maxIdle.isZero() || maxIdle.isNegative()) {
            throw new IllegalArgumentException("invalid maxIdle duration: " + maxIdle);
        }
        // the application layer must not configure its max idle duration
        // until the QUIC connection's handshake has successfully completed
        requireSuccessfulHandshake();

        if (!this.trafficGenerationCheck.compareAndSet(null, trafficGenerationCheck)) {
            throw new IllegalStateException("app layer max inactivity already set");
        }
        Optional<Long> quicIdleTimeout = idleTimeout();
        if (quicIdleTimeout.isEmpty()) {
            // the QUIC connection will never idle timeout, nothing more to do
            return;
        }
        // we start the PING sending timer event only if the QUIC layer idle timeout
        // is lesser than the app layer's desired idle time
        if (Duration.ofMillis(quicIdleTimeout.get()).compareTo(maxIdle) < 0) {
            this.stateLock.lock();
            try {
                if (shutdown.get()) {
                    return;
                }
                // QUIC connection has a lower idle timeout than the app layer. start a timer
                // which checks with the app layer at regular intervals to decide whether to
                // send a PING to keep the QUIC connection active.
                startPingTimer();
            } finally {
                this.stateLock.unlock();
            }
        }
    }

    /**
     * Attempts to notify the idle connection management that this connection should
     * be considered "in use". This way the idle connection management doesn't close
     * this connection during the time the connection is handed out from the pool and any
     * new stream created on that connection.
     *
     * @return true if the connection has been successfully reserved. false
     *        otherwise; in which case the connection must not be handed out from the pool.
     */
    boolean tryReserveForUse() {
        this.idleTerminationLock.lock();
        try {
            if (chosenForIdleTermination) {
                // idle termination has been decided for this connection, don't use it
                return false;
            }
            // if the connection is nearing idle timeout due to lack of traffic then
            // don't use it
            long lastPktActivity = lastPacketActivityNanos;
            long currentNanos = System.nanoTime();
            long inactivityMs = MILLISECONDS.convert((currentNanos - lastPktActivity),
                                                     NANOSECONDS);
            boolean nearingIdleTimeout = idleTimeout()
                    .map((timeoutMillis) -> inactivityMs >= (0.8 * timeoutMillis)) // 80% of idle timeout
                    .orElse(false);
            if (nearingIdleTimeout) {
                return false;
            }
            // express interest in using the connection
            this.lastUsageReservationNanos = System.nanoTime();
            return true;
        } finally {
            this.idleTerminationLock.unlock();
        }
    }

    /**
     * Returns the idle timeout duration, in milliseconds, negotiated for the connection represented
     * by this {@code IdleTimeoutManager}. The negotiated idle timeout of a connection
     * is the minimum of the idle connection timeout that is advertised by the
     * endpoint represented by this {@code IdleTimeoutManager} and the idle
     * connection timeout advertised by the peer. If neither endpoints have advertised
     * any idle connection timeout then this method returns an
     * {@linkplain Optional#empty() empty} value.
     *
     * @return the idle timeout in milliseconds or {@linkplain Optional#empty() empty}
     */
    Optional<Long> idleTimeout() {
        long val = this.idleTimeoutDurationMs.get();
        return val == NO_IDLE_TIMEOUT ? Optional.empty() : Optional.of(val);
    }

    void peerPacketProcessed() {
        lastPacketActivityNanos = System.nanoTime();
        outboundRestartAvailable.set(true);
    }

    boolean ackElicitingPacketSent() {
        if (!outboundRestartAvailable.compareAndSet(true, false)) {
            return false;
        }
        lastPacketActivityNanos = System.nanoTime();
        return true;
    }

    void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            // already shutdown
            return;
        }
        this.stateLock.lock();
        try {
            // unregister the timeout events from the QuicTimerQueue
            stopIdleTerminationTimer();
            stopStreamDataBlockedTimer();
            stopPingTimer();
        } finally {
            this.stateLock.unlock();
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "idle timeout manager shutdown");
        }
    }

    void localIdleTimeout(long timeoutMillis) {
        checkUpdateIdleTimeout(timeoutMillis);
    }

    void peerIdleTimeout(long timeoutMillis) {
        checkUpdateIdleTimeout(timeoutMillis);
    }

    private static void disableTimedEvent(QuicTimerQueue timer, TimedEvent te) {
        // disable the event (refreshDeadline() of TimedEvent will return Deadline.MAX)
        Deadline nextDeadline = te.nextDeadline();
        if (!nextDeadline.equals(Deadline.MAX)) {
            te.nextDeadline(Deadline.MAX);
            timer.reschedule(te, Deadline.MIN);
        }
    }

    private void requireSuccessfulHandshake() {
        CompletableFuture<QuicTLSEngine.HandshakeState> handshakeCF =
                this.connection.handshakeFlow().handshakeCF();
        if (!handshakeCF.isDone()) {
            throw new IllegalStateException("handshake isn't yet complete,"
                                                    + " cannot use idle connection management");
        }
        if (handshakeCF.isCompletedExceptionally()) {
            throw new IllegalStateException("cannot use idle connection management for a failed"
                                                    + " connection");
        }
    }

    private void startIdleTerminationTimer() {
        Optional<Long> idleTimeoutMillis = idleTimeout();
        if (idleTimeoutMillis.isEmpty()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "idle connection management disabled for connection");
            }
            return;
        }
        QuicTimerQueue timerQueue = connection.endpoint().timer();
        Deadline deadline = timeLine().instant().plusMillis(idleTimeoutMillis.get());
        // we don't expect idle timeout management to be started more than once
        // create the idle timeout event and register with the QuicTimerQueue.
        this.idleTimeoutEvent = new IdleTimeoutEvent(deadline);
        timerQueue.offer(this.idleTimeoutEvent);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG,
                      "started QUIC idle timeout management for connection, idle timeout event: %s deadline: %s",
                      this.idleTimeoutEvent, deadline);
        }
    }

    private void stopIdleTerminationTimer() {
        if (this.idleTimeoutEvent == null) {
            return;
        }
        QuicEndpoint endpoint = this.connection.endpoint();
        // disable the idle timeout timer event
        disableTimedEvent(endpoint.timer(), this.idleTimeoutEvent);
        this.idleTimeoutEvent = null;
    }

    private void startStreamDataBlockedTimer() {
        // 75% of QUIC idle timeout or if QUIC idle timeout is not configured, then 30 seconds
        long timeoutMillis = idleTimeout()
                .map((v) -> (long) (0.75 * v))
                .orElse(30000L);
        QuicTimerQueue timerQueue = connection.endpoint().timer();
        Deadline deadline = timeLine().instant().plusMillis(timeoutMillis);
        // we don't expect the timer to be started more than once
        // create the timeout event and register with the QuicTimerQueue.
        this.streamDataBlockedEvent = new StreamDataBlockedEvent(deadline, timeoutMillis);
        timerQueue.offer(this.streamDataBlockedEvent);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG,
                      "started STREAM_DATA_BLOCKED timer for connection, event: %s deadline: %s",
                      this.streamDataBlockedEvent, deadline);
        }
    }

    private void stopStreamDataBlockedTimer() {
        if (this.streamDataBlockedEvent == null) {
            return;
        }
        QuicEndpoint endpoint = this.connection.endpoint();
        // disable the stream data blocked timer event
        disableTimedEvent(endpoint.timer(), this.streamDataBlockedEvent);
        this.streamDataBlockedEvent = null;
    }

    private void startPingTimer() {
        // we don't expect the timer to be started more than once
        Optional<Long> quicIdleTimeout = idleTimeout();
        // potential PING generation every 75% of QUIC idle timeout
        long pingFrequencyMillis = (long) (0.75 * quicIdleTimeout.get());
        QuicTimerQueue timerQueue = connection.endpoint().timer();
        Deadline deadline = timeLine().instant().plusMillis(pingFrequencyMillis);
        // create the timeout event and register with the QuicTimerQueue.
        this.pingEvent = new PingEvent(deadline, pingFrequencyMillis);
        timerQueue.offer(this.pingEvent);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG,
                      "started periodic PING for connection, ping event: %s deadline: %s",
                      this.pingEvent, deadline);
        }
    }

    private void stopPingTimer() {
        if (this.pingEvent == null) {
            return;
        }
        QuicEndpoint endpoint = this.connection.endpoint();
        // disable the ping timer event
        disableTimedEvent(endpoint.timer(), this.pingEvent);
        this.pingEvent = null;
        this.trafficGenerationCheck.set(null);
    }

    private void checkUpdateIdleTimeout(long newIdleTimeoutMillis) {
        if (newIdleTimeoutMillis <= 0) {
            // idle timeout should be non-zero value, we disregard other values
            return;
        }
        long current;
        boolean updated = false;
        // update the idle timeout if the new timeout is lesser
        // than the previously set value
        while ((current = this.idleTimeoutDurationMs.get()) == NO_IDLE_TIMEOUT
                || current > newIdleTimeoutMillis) {
            updated = this.idleTimeoutDurationMs.compareAndSet(current, newIdleTimeoutMillis);
            if (updated) {
                break;
            }
        }
        if (!updated) {
            return;
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "idle connection timeout updated to %s milli seconds",
                      newIdleTimeoutMillis);
        }
    }

    private TimeLine timeLine() {
        return TimeSource.source();
    }

    private void log(System.Logger.Level level, String format, Object... arguments) {
        if (arguments.length == 0) {
            connection.log(LOGGER, level, "%s", format);
        } else {
            connection.log(LOGGER, level, format, arguments);
        }
    }

    // called when the connection has been idle past its idle timeout duration
    private void idleTimedOut() {
        if (shutdown.get()) {
            return; // nothing to do - the idle timeout manager has been shutdown
        }
        Optional<Long> timeoutVal = idleTimeout();
        long timeoutMillis = timeoutVal.get();
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            // log idle timeout, with packet space statistics
            String msg = "silently terminating connection due to idle timeout ("
                    + timeoutMillis + " milli seconds)";
            StringBuilder sb = new StringBuilder();
            for (PacketNumberSpace sp : PacketNumberSpace.values()) {
                if (sp == PacketNumberSpace.NONE) {
                    continue;
                }
                if (connection.packetNumberSpaces().get(sp) instanceof PacketSpaceManager m) {
                    sb.append("\n  PacketSpace: ").append(sp).append('\n');
                    m.debugState("    ", sb);
                }
            }
            log(System.Logger.Level.DEBUG, "%s: %s", msg, sb);
        }
        // silently close the connection and discard all its state
        var type = connection.isClientConnection() ? "client" : "server";
        var label = "quic:" + connection.uniqueId();
        QuicCloseCommand command = QuicCloseCommand.silent(type + " connection idle timed out ("
                                                                   + timeoutMillis + " milli seconds) on " + label);
        connection.terminator().terminate(command);
    }

    private long computeInactivityMillis() {
        long currentNanos = System.nanoTime();
        long lastActiveNanos = Math.max(lastPacketActivityNanos, lastUsageReservationNanos);
        return MILLISECONDS.convert((currentNanos - lastActiveNanos), NANOSECONDS);
    }

    abstract sealed class TimedEvent implements QuicTimedEvent {
        private final long eventId;
        private volatile Deadline deadline;
        private volatile Deadline nextDeadline;

        private TimedEvent(Deadline deadline) {
            this.deadline = deadline;
            this.nextDeadline = deadline;
            this.eventId = QuicTimerQueue.newEventId();
        }

        @Override
        public final Deadline deadline() {
            return this.deadline;
        }

        @Override
        public final Deadline refreshDeadline() {
            if (shutdown.get()) {
                return deadlineAndNext(Deadline.MAX);
            }
            return deadlineAndNext(this.nextDeadline);
        }

        @Override
        public final long eventId() {
            return this.eventId;
        }

        @Override
        public abstract Deadline handle();

        /**
         * Returns the next deadline.
         *
         * @return next deadline
         */
        final Deadline nextDeadline() {
            return nextDeadline;
        }

        /**
         * Updates the next deadline.
         *
         * @param nextDeadline next deadline
         * @return updated deadline
         */
        final Deadline nextDeadline(Deadline nextDeadline) {
            this.nextDeadline = nextDeadline;
            return nextDeadline;
        }

        /**
         * Updates the current deadline and next deadline.
         *
         * @param deadline deadline to set
         * @return updated deadline
         */
        final Deadline deadlineAndNext(Deadline deadline) {
            this.deadline = deadline;
            this.nextDeadline = deadline;
            return deadline;
        }
    }

    final class IdleTimeoutEvent extends TimedEvent {

        private IdleTimeoutEvent(Deadline deadline) {
            super(deadline);
        }

        @Override
        public Deadline handle() {
            if (shutdown.get()) {
                // timeout manager is shutdown, nothing more to do
                return nextDeadline(Deadline.MAX);
            }
            Optional<Long> idleTimeout = idleTimeout();
            if (idleTimeout.isEmpty()) {
                // nothing to do, don't reschedule
                return Deadline.MAX;
            }
            long idleTimeoutMillis = idleTimeout.get();
            // check whether the connection has indeed been idle for the idle timeout duration
            idleTerminationLock.lock();
            try {
                Deadline postponed = maybePostponeDeadline(idleTimeoutMillis);
                if (postponed != null) {
                    // not idle long enough, reschedule
                    return nextDeadline(postponed);
                }
                chosenForIdleTermination = true;
            } finally {
                idleTerminationLock.unlock();
            }
            // the connection has been idle for the idle timeout duration, go
            // ahead and terminate it.
            terminateNow();
            return nextDeadline(Deadline.MAX);
        }

        @Override
        public String toString() {
            return "QuicIdleTimeoutEvent-" + eventId();
        }

        private Deadline maybePostponeDeadline(long expectedIdleDurationMs) {
            long inactivityMs = computeInactivityMillis();
            if (inactivityMs >= expectedIdleDurationMs) {
                // the connection has been idle long enough, don't postpone the timeout.
                return null;
            }
            // not idle long enough, compute the deadline when it's expected to reach
            // idle timeout
            long remainingMs = expectedIdleDurationMs - inactivityMs;
            Deadline next = timeLine().instant().plusMillis(remainingMs);
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "postponing timeout event: " + this + " to fire"
                        + " in " + remainingMs + " milli seconds, deadline: " + next);
            }
            return next;
        }

        private void terminateNow() {
            try {
                idleTimedOut();
            } finally {
                shutdown();
            }
        }
    }

    final class StreamDataBlockedEvent extends TimedEvent {
        private final long timeoutMillis;

        private StreamDataBlockedEvent(Deadline deadline, long timeoutMillis) {
            super(deadline);
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public Deadline handle() {
            if (shutdown.get()) {
                // timeout manager is shutdown, nothing more to do
                return nextDeadline(Deadline.MAX);
            }
            // check whether the connection has indeed been idle for the idle timeout duration
            idleTerminationLock.lock();
            try {
                if (chosenForIdleTermination) {
                    // connection is already chosen for termination, no need to send
                    // a STREAM_DATA_BLOCKED
                    return nextDeadline(Deadline.MAX);
                }
                long inactivityMs = computeInactivityMillis();
                if (inactivityMs >= timeoutMillis && connection.streams().hasBlockedStreams()) {
                    // has been idle long enough, but there are streams that are blocked due to
                    // flow control limits and that could have lead to the idleness.
                    // trigger sending a STREAM_DATA_BLOCKED frame for the streams
                    // to try and have their limits increased by the peer.
                    connection.streams().enqueueStreamDataBlocked();
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "enqueued a STREAM_DATA_BLOCKED frame since connection"
                                + " has been idle due to blocked stream(s)");
                    }
                }
                return nextDeadline(timeLine().instant().plusMillis(timeoutMillis));
            } finally {
                idleTerminationLock.unlock();
            }
        }

        @Override
        public String toString() {
            return "StreamDataBlockedEvent-" + eventId();
        }
    }

    final class PingEvent extends TimedEvent {
        private final long pingFrequencyNanos;
        private final long idleTimeoutNanos;

        private PingEvent(Deadline deadline, long pingFrequencyMillis) {
            super(deadline);
            this.pingFrequencyNanos = MILLISECONDS.toNanos(pingFrequencyMillis);
            if (this.pingFrequencyNanos <= 0) {
                throw new IllegalArgumentException("ping frequency is too small: "
                                                           + pingFrequencyMillis + " milliseconds");
            }
            this.idleTimeoutNanos = MILLISECONDS.toNanos(idleTimeout().get());
        }

        @Override
        public Deadline handle() {
            if (shutdown.get()) {
                // timeout manager is shutdown, nothing more to do
                return nextDeadline(Deadline.MAX);
            }
            if (!shouldInitiateAppLayerCheck()) {
                // reschedule for next round
                return nextDeadline(timeLine().instant().plusNanos(this.pingFrequencyNanos));
            }
            // check with the app layer if traffic generation is required
            Supplier<Boolean> check = trafficGenerationCheck.get();
            if (check == null) {
                // generateTrafficCheck can be null if the timeout manager was shutdown
                // when this event handling was in progress. don't send a PING frame
                // in that case.
                return nextDeadline(Deadline.MAX);
            }
            if (check.get()) {
                // app layer OKed sending a PING
                connection.requestSendPing();
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "enqueued a PING frame");
                }
            } else {
                // app layer told us not to send a PING.
                // we skip the PING generation only for the current round, no need
                // to disable future PING checks
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "skipping PING generation");
                }
            }
            return nextDeadline(timeLine().instant().plusNanos(this.pingFrequencyNanos));
        }

        @Override
        public String toString() {
            return "PingEvent-" + eventId();
        }

        // returns true if the app layer traffic generation check needs to be invoked,
        // false otherwise.
        private boolean shouldInitiateAppLayerCheck() {
            long lastPktAt = lastPacketActivityNanos;
            long now = System.nanoTime();
            if ((now - lastPktAt) >= this.pingFrequencyNanos) {
                // no traffic during the ping interval, initiate a app layer check
                // to see if explicit traffic needs to be generated
                return true;
            }
            // check if the connection will potentially idle terminate before the next
            // ping check is scheduled, if yes, then initiate a app layer traffic
            // generation check now
            long idleTerminationAt = lastPktAt + this.idleTimeoutNanos;
            long nextPingCheck = now + this.pingFrequencyNanos;
            if (idleTerminationAt - nextPingCheck <= 0) {
                return true;
            }
            // connection appears to be receiving traffic, no need to initiate app layer
            // traffic generation check
            return false;
        }
    }
}
