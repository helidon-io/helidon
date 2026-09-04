/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.Api;
import io.helidon.quic.QuicTLSEngine.KeySpace;
import io.helidon.quic.frame.AckFrame;
import io.helidon.quic.frame.AckFrame.AckFrameBuilder;
import io.helidon.quic.frame.ConnectionCloseFrame;
import io.helidon.quic.frame.PaddingFrame;
import io.helidon.quic.frame.PathChallengeFrame;
import io.helidon.quic.frame.PathResponseFrame;
import io.helidon.quic.frame.PingFrame;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.packet.PacketSpace;
import io.helidon.quic.packet.QuicPacket;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;
import io.helidon.quic.packet.QuicPacket.PacketType;

/**
 * A {@code PacketSpaceManager} takes care of acknowledgement and
 * retransmission of packets for a given {@link io.helidon.quic.packet.QuicPacket.PacketNumberSpace}.
 *
 * <p>Specification: <a href="https://www.rfc-editor.org/info/rfc9000">...</a>
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * <p>Specification: <a href="https://www.rfc-editor.org/info/rfc9002">...</a>
 *        RFC 9002: QUIC Loss Detection and Congestion Control
 */

// See also: RFC 9000, https://www.rfc-editor.org/rfc/rfc9000#name-sending-ack-frames
//   Every packet SHOULD be acknowledged at least once, and
//   ack-eliciting packets MUST be acknowledged at least once within
//   the maximum delay an endpoint communicated using the max_ack_delay
//   transport parameter [...];
//   [...]
//   In order to assist loss detection at the sender, an endpoint
//   SHOULD generate and send an ACK frame without delay when it
//   receives an ack-eliciting packet either:
//      - when the received packet has a packet number less
//        than another ack-eliciting packet that has been received, or
//      - when the packet has a packet number larger than the
//        highest-numbered ack-eliciting packet that has been received
//        and there are missing packets between that packet and this
//        packet. [...]
@Api.Internal
public sealed class PacketSpaceManager implements PacketSpace
        permits PacketSpaceManager.OneRttPacketSpaceManager,
                PacketSpaceManager.HandshakePacketSpaceManager {
    private static final System.Logger LOGGER = System.getLogger(PacketSpaceManager.class.getName());

    /**
     * Threshold of ACK ranges after which a PING is piggybacked on the next ACK.
     */
    public static final int MAX_ACKRANGE_COUNT_BEFORE_PING = 10;
    private static final int ACK_TIMER_SCHEDULING_SLACK_MILLIS = 16;
    // packet threshold for loss detection; RFC 9002 suggests 3
    private static final long PACKET_THRESHOLD = 3;
    // Multiplier for persistent congestion; RFC 9002 suggests 3
    private static final int PERSISTENT_CONGESTION_THRESHOLD = 3;
    // Occasionally skip an application-space packet number so a peer that acknowledges it
    // proves it is sending optimistic ACKs for packets it could not have received.
    private static final int OPTIMISTIC_ACK_SKIP_INTERVAL_MIN = 128;
    private static final int OPTIMISTIC_ACK_SKIP_INTERVAL_MAX = 512;
    private static final SecureRandom OPTIMISTIC_ACK_RANDOM = new SecureRandom();

    // These two numbers control whether an PING frame will be
    // sent with the next ACK frame, to turn the packet that
    // contains the ACK frame into an ACK-eliciting packet.
    // These numbers are *not* defined in RFC 9000, but are used
    // to implement a strategy for sending occasional PING frames
    // in order to prevent ACK frames from growing too big.
    // See RFC 9000 section 13.2.4
    // https://www.rfc-editor.org/rfc/rfc9000#name-limiting-ranges-by-tracking
    private final QuicCongestionController congestionController;
    private final Supplier<String> debugDescriptionSupplier;
    private final PacketNumberSpace packetNumberSpace;
    private final PacketEmitter packetEmitter;
    private final ReentrantLock stateLock = new ReentrantLock();
    private final ReentrantLock transferLock = new ReentrantLock();
    // The next packet number to use in this space
    private final AtomicLong nextPN = new AtomicLong();
    private final AtomicLong packetsUntilOptimisticAckSkip = new AtomicLong(Long.MAX_VALUE);
    private final ConcurrentSkipListSet<Long> skippedPacketNumbers = new ConcurrentSkipListSet<>();
    private final TimeLine instantSource;
    private final QuicRttEstimator rttEstimator;
    private final PathRecoveryState pathRecoveryState;
    // A priority queue containing a record for each unacknowledged PingRequest.
    // PingRequest are removed from this queue when they are acknowledged, that
    // is when any packet whose number is greater than the request packet
    // is acknowledged.
    // Note: this is used to implement {@link #requestSendPing()} which is
    //       used to implement out of band ping requests triggered by the
    //       application.
    private final ConcurrentLinkedQueue<PingRequest> pendingPingRequests =
            new ConcurrentLinkedQueue<>();
    // A priority queue containing a record for each unacknowledged packet.
    // Packets are removed from this queue when they are acknowledged, or when they
    // are being retransmitted. In which case, they will be in the pendingRetransmission
    // queue
    private final ConcurrentLinkedQueue<PendingAcknowledgement> pendingAcknowledgements =
            new ConcurrentLinkedQueue<>();
    // Probing packets use the selected path's congestion controller and pacer,
    // but have a separate non-retransmitting lifecycle owned by path validation.
    // All access is protected by transferLock.
    private final NavigableMap<Long, PathControlFlight> pathControlFlights = new TreeMap<>();
    // A priority queue containing a record for each unacknowledged packet whose deadline
    // is due, and which is currently being retransmitted.
    // Packets are removed from this queue when they have been scheduled for retransmission
    // with the quic endpoint
    private final ConcurrentLinkedQueue<PendingAcknowledgement> pendingRetransmission =
            new ConcurrentLinkedQueue<>();
    // A priority queue containing a record for each unacknowledged packet whose deadline
    // is due, and which should be retransmitted.
    // Packets are removed from this queue when they have been scheduled for encryption.
    private final ConcurrentLinkedQueue<PendingAcknowledgement> triggeredForRetransmission =
            new ConcurrentLinkedQueue<>();
    // lost packets
    // All access is protected by transferLock. Stable insertion order keeps
    // recovery diagnostics deterministic while allowing retransmission-chain
    // tips to be replaced in constant time.
    private final Set<PendingAcknowledgement> lostPackets = new LinkedHashSet<>();
    // Reused ACK/recovery state. All access is protected by transferLock.
    private final AcknowledgementScan acknowledgementScan = new AcknowledgementScan();
    private final AckProcessingState ackProcessingState = new AckProcessingState();
    private long acknowledgementScanEpoch;
    // A task invoked by the QuicTimerQueue when some packet retransmission are
    // due. This task will move packets from the pendingAcknowledgement queue
    // into the triggeredForRetransmission queue (and pendingRetransmission queue)
    private final PacketTransmissionTask packetTransmissionTask;
    // Used to synchronize transmission with handshake restarts
    private final ReentrantLock transmitLock = new ReentrantLock();
    private final QuicTLSEngine quicTLSEngine;
    private final Consumer<KeySpace> keyDiscarded;
    private final EmittedAckTracker emittedAckTracker;
    private final int localAckDelayExponent;
    // max ACK delay; zero on initial and handshake, configured policy on application
    private final long maxAckDelay; // ms
    private volatile boolean closed;
    private volatile boolean blockedByCC;
    private volatile boolean blockedByPacer;
    // true if transmit timer should fire now
    private volatile boolean transmitNow;
    // first packet number sent after handshake confirmed
    private long handshakeConfirmedPN;
    private volatile boolean fastRetransmitDone;
    private volatile boolean fastRetransmit;
    private volatile NextAckFrame nextAckFrame; // assigned through VarHandle
    // exponent for incoming packets
    private volatile long peerAckDelayExponent;
    // max peer ack delay; zero on initial and handshake,
    // initialized from transport parameters on application
    private volatile long peerMaxAckDelayMillis; // ms
    // The last time an ACK eliciting packet was sent.
    // May be null before any such packet is sent...
    private volatile Deadline lastAckElicitingTime;
    // A local retransmission can fail before a packet is emitted, for example while
    // a path or connection ID is temporarily unavailable. Keep an absolute fallback
    // deadline and a local backoff so unrelated timer refreshes cannot postpone the
    // retry indefinitely or turn an unavailable path into a tight retry loop.
    private volatile Deadline failedRetransmissionDeadline = Deadline.MAX;
    // All access is protected by transferLock.
    private long failedRetransmissionBackoff = 1;
    private boolean retransmissionAttemptActive;
    // not null if sending ping has been requested.
    private volatile CompletableFuture<Long> pingRequested;
    // The largest packet number successfully processed in this space.
    // Needed to decode received packet numbers, see RFC 9000 appendix A.3
    private volatile long largestProcessedPN; // assigned through VarHandle
    // The largest ACK-eliciting packet number received in this space.
    // Needed to determine if we should send ACK without delay, see RFC 9000 section 13.2.1
    private volatile long largestAckElicitingReceivedPN; // assigned through VarHandle
    // The largest ACK-eliciting packet number sent in this space.
    // Needed to determine if we should arm PTO timer
    private volatile long largestAckElicitingSentPN;
    // The largest packet number acknowledged by peer.
    // Needed to determine packet number length, see RFC 9000 appendix A.2
    private volatile long largestReceivedAckedPN; // assigned through VarHandle
    // The largest packet number acknowledged in this space
    // This is the largest packet number we have acknowledged.
    // This should be less or equal to the largestProcessedPN always.
    // Not used.
    private volatile long largestSentAckedPN; // assigned through VarHandle
    // The largest packet number that this instance has included
    // in an AckFrame sent to the peer, and of which the peer has
    // acknowledged reception.
    // Used to limit ack ranges, see RFC 9000 section 13.2.4
    private volatile long largestAckedPNReceivedByPeer; // assigned through VarHandle

    /**
     * Creates a new {@code PacketSpaceManager} for the given
     * packet number space.
     *
     * @param connection        The connection for which this manager
     *                         is created.
     * @param packetNumberSpace The packet number space.
     */
    PacketSpaceManager(QuicConnectionImpl connection,
                       PacketNumberSpace packetNumberSpace) {
        this(packetNumberSpace, connection.emitter(), TimeSource.source(),
             connection.rttEstimator(), connection.congestionController(), connection.tlsEngine(),
             connection::logTag, connection.pathRecoveryState(),
             connection.runtimeConfig().transportParameters().ackDelayExponent(),
             connection.runtimeConfig().transportParameters().maxAckDelay().toMillis(),
             connection::discardPeerCryptoFlow);
    }
    PacketSpaceManager(PacketNumberSpace packetNumberSpace,
                       PacketEmitter packetEmitter,
                       TimeLine instantSource,
                       QuicRttEstimator rttEstimator,
                       QuicCongestionController congestionController,
                       QuicTLSEngine quicTLSEngine,
                       Supplier<String> debugTagSupplier,
                       PathRecoveryState pathRecoveryState,
                       int localAckDelayExponent,
                       long advertisedMaxAckDelayMillis,
                       Consumer<KeySpace> keyDiscarded) {
        largestProcessedPN = -1L;
        largestReceivedAckedPN = -1L;
        largestAckElicitingReceivedPN = -1L;
        largestAckElicitingSentPN = -1L;
        largestSentAckedPN = -1L;
        largestAckedPNReceivedByPeer = -1L;
        this.debugDescriptionSupplier = () -> debugTagSupplier.get() + " " + packetNumberSpace.name();
        this.instantSource = instantSource;
        this.rttEstimator = rttEstimator;
        this.pathRecoveryState = pathRecoveryState;
        this.congestionController = congestionController;
        this.packetNumberSpace = packetNumberSpace;
        this.packetEmitter = packetEmitter;
        this.emittedAckTracker = new EmittedAckTracker();
        this.packetTransmissionTask = new PacketTransmissionTask();
        this.quicTLSEngine = quicTLSEngine;
        this.keyDiscarded = keyDiscarded;
        this.localAckDelayExponent = localAckDelayExponent;
        maxAckDelay = (packetNumberSpace == PacketNumberSpace.APPLICATION)
                ? Math.max(0, advertisedMaxAckDelayMillis - ACK_TIMER_SCHEDULING_SLACK_MILLIS) : 0;
        if (optimisticAckDetectionEnabled()) {
            packetsUntilOptimisticAckSkip.set(nextOptimisticAckSkipInterval());
        }
    }

    /**
     * Returns the max delay before emitting a non ACK-eliciting packet to
     * acknowledge a received ACK-eliciting packet, in milliseconds.
     *
     * @return the max delay before emitting a non ACK-eliciting packet to
     *         acknowledge a received ACK-eliciting packet, in milliseconds
     */
    public long maxAckDelay() {
        return maxAckDelay;
    }

    /**
     * Returns the max ACK delay of the peer, in milliseconds.
     *
     * @return the max ACK delay of the peer, in milliseconds.
     */
    public long peerMaxAckDelayMillis() {
        return peerMaxAckDelayMillis;
    }

    /**
     * Changes the value of the {@linkplain #peerMaxAckDelayMillis()
     * peer max ACK delay} and ack delay exponent.
     *
     * @param peerDelay        the new delay, in milliseconds
     * @param ackDelayExponent the new ack delay exponent
     */
    @Override
    public void updatePeerTransportParameters(long peerDelay, long ackDelayExponent) {
        this.peerAckDelayExponent = ackDelayExponent;
        this.peerMaxAckDelayMillis = peerDelay;
    }

    @Override
    public PacketNumberSpace packetNumberSpace() {
        return packetNumberSpace;
    }

    @Override
    public long allocateNextPN() {
        long packetNumber = nextPN.getAndIncrement();
        if (!shouldSkipPacketNumber()) {
            return packetNumber;
        }

        recordSkippedPacketNumber(packetNumber);
        long actualPacketNumber = nextPN.getAndIncrement();
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG,
                      "Skipping application packet number %d for optimistic-ACK detection; using %d",
                      packetNumber,
                      actualPacketNumber);
        }
        return actualPacketNumber;
    }

    @Override
    public long largestPeerAcknowledgedPacketNumber() {
        return largestReceivedAckedPN;
    }

    @Override
    public long largestProcessedPacketNumber() {
        return largestProcessedPN;
    }

    @Override
    public long minimumPacketNumberThreshold() {
        return largestAckedPNReceivedByPeer;
    }

    @Override
    public long largestSentAcknowledgedPacketNumber() {
        return largestSentAckedPN;
    }

    /**
     * This method is called by {@link QuicConnectionImpl} upon reception of
     * and successful negotiation of a new version.
     * In that case we should stop retransmitting packet that have the
     * "wrong" version: they will never be acknowledged.
     */
    public void versionChanged() {
        // don't retransmit packet with "bad" version
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "version changed - clearing pending acks");
        }
        clearAll();
    }

    /**
     * Clears pending packet-space state after receiving a Retry packet.
     */
    public void retry() {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "retry received - resetting Initial recovery state");
        }
        clearAll();
        rttEstimator.resetForPath();
        congestionController.resetForPath(Math.toIntExact(congestionController.maxDatagramSize()));
        blockedByCC = false;
        blockedByPacer = false;
        packetEmitter.reschedule(packetTransmissionTask, Deadline.MAX);
    }

    @Override
    public ReentrantLock transmitLock() {
        return transmitLock;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        stateLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
        } finally {
            stateLock.unlock();
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "closing packet space");
        }
        // stop the internal scheduler
        packetTransmissionTask.handleScheduler.stop();
        // make sure the task gets eventually removed from the timer
        packetEmitter.reschedule(packetTransmissionTask);
        // clear pending acks, retransmissions
        KeySpace keySpace = tlsEncryptionLevel();
        try {
            transferLock.lock();
            try {
                clearAll();
                // discard the (TLS) keys
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "discarding TLS keys");
                }
                this.quicTLSEngine.discardKeys(keySpace);
            } finally {
                transferLock.unlock();
            }
        } finally {
            keyDiscarded.accept(keySpace);
        }
        rttEstimator.resetPtoBackoff();
        // complete any ping request that hasn't been completed
        IllegalStateException failure = null;
        try {
            for (var pr : pendingPingRequests) {
                if (failure == null) {
                    failure = new IllegalStateException("Not sending ping because "
                                                                + this.packetNumberSpace
                                                                + " packet space is being closed");
                }
                pr.response().completeExceptionally(failure);
            }
        } finally {
            pendingPingRequests.clear();
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void runTransmitter() {
        transmitNow = true;
        // run the handle loop
        packetTransmissionTask.handle();
    }

    @Override
    public void packetReceived(PacketType packet, long packetNumber, boolean isAckEliciting) {

        if (closed) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "closed, ignoring %s(pn: %s)", packet, packetNumber);
            }
            return;
        }

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "packetReceived %s(pn:%d, needsAck:%s)",
                      packet, packetNumber, isAckEliciting);
        }

        // whether the packet is ack eliciting or not, we need to add its packet
        // number to the ack frame.
        packetProcessed(packetNumber);
        addToAckFrame(packetNumber, isAckEliciting);
    }

    /**
     * Returns the packet-number allocator used by this packet space.
     *
     * @return the packet-number allocator used by this packet space.
     */
    public AtomicLong nextPacketNumber() {
        return nextPN;
    }

    @Override
    public void packetSent(QuicPacket packet, long previousPacketNumber, long packetNumber) {
        packetSent0(packet, previousPacketNumber, packetNumber, pathRecoveryState.generation());
    }

    @Override
    public void packetSent(QuicPacket packet,
                           long previousPacketNumber,
                           long packetNumber,
                           long pathGeneration) {
        packetSent0(packet, previousPacketNumber, packetNumber, pathGeneration);
    }

    private void packetSent0(QuicPacket packet,
                             long previousPacketNumber,
                             long packetNumber,
                             long pathGeneration) {
        if (packetNumber < 0) {
            throw new IllegalArgumentException("Invalid packet number: " + packetNumber);
        }
        largestAckSent(AckFrame.largestAcknowledgedInPacket(packet));
        boolean pathControl = false;
        boolean applicationRecovery = false;
        for (QuicFrame frame : packet.frames()) {
            if (frame instanceof PathChallengeFrame || frame instanceof PathResponseFrame) {
                pathControl = true;
            } else if (!(frame instanceof AckFrame)
                    && !(frame instanceof PaddingFrame)
                    && !(frame instanceof PingFrame)) {
                applicationRecovery = true;
                break;
            }
        }
        if (pathControl && !applicationRecovery) {
            Deadline sent = now();
            long accountingGeneration = pathRecoveryState.generation();
            transferLock.lock();
            try {
                if (!isOpenForTransmission()) {
                    return;
                }
                pathRecoveryState.runIfCurrent(accountingGeneration, () -> {
                    pathControlFlights.put(packetNumber,
                                           new PathControlFlight(packet,
                                                                 sent,
                                                                 packetNumber,
                                                                 pathGeneration,
                                                                 accountingGeneration));
                    congestionController.packetSent(packet.size());
                });
            } finally {
                transferLock.unlock();
            }
            return;
        }
        if (previousPacketNumber >= 0) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "retransmitted packet %s(%d) as %d",
                          packet.packetType(), previousPacketNumber, packetNumber);
            }

            boolean found = false;
            transferLock.lock();
            try {
                // check for close and addAcknowledgement in the same lock
                // to avoid races with close / clearAll
                var closed = !this.isOpenForTransmission();
                if (closed) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "already closed: ignoring packet pn:%s",
                                  packet.packetNumber());
                    }
                    return;
                }
                // Pending retransmissions are expected to be short; keep the linear scan
                // so the queue representation remains simple.
                var iterator = pendingRetransmission.iterator();
                PendingAcknowledgement replacement;
                while (iterator.hasNext()) {
                    PendingAcknowledgement pending = iterator.next();
                    if (pending.hasPreviousNumber(previousPacketNumber)) {
                        // no need to retransmit twice, but can this happen?
                        iterator.remove();
                    } else if (!found && pending.hasExactNumber(previousPacketNumber)) {
                        PreviousNumbers previous = new PreviousNumbers(
                                previousPacketNumber,
                                pending.sent,
                                pending.largestAcknowledged,
                                pending.pathGeneration,
                                pending.previousNumbers);
                        replacement =
                                new PendingAcknowledgement(packet, now(), packetNumber, previous, pathGeneration);
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            log(System.Logger.Level.DEBUG, "Packet %s(pn:%s) previous %s(pn:%s) is pending acknowledgement",
                                      packet.packetType(), packetNumber, packet.packetType(), previousPacketNumber);
                        }
                        if (lostPackets.remove(pending)) {
                            lostPackets.add(replacement);
                        }
                        addAcknowledgement(replacement);
                        iterator.remove();
                        found = true;
                    }
                }
            } finally {
                transferLock.unlock();
            }
            if (found) {
                packetTransmissionTask.reschedule();
            }
            if (!found) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "packetRetransmitted: packet not found - previous: %s for %s(%s)",
                              previousPacketNumber, packet.packetType(), packetNumber);
                }
            }
        } else {
            if (packet.isAckEliciting()) {
                // This method works with the following assumption:
                // - Non ACK eliciting packet do not need to be retransmitted because:
                //       - they only contain ack frames - which may/will we be retransmitted
                //         anyway with the next ack eliciting packet
                //       - they will not be acknowledged directly - we don't want to
                //         resend them constantly
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Packet %s(pn:%s) is pending acknowledgement",
                              packet.packetType(), packetNumber);
                }
                PendingAcknowledgement pending = new PendingAcknowledgement(packet,
                                                                            now(), packetNumber, null, pathGeneration);
                transferLock.lock();
                try {
                    // check for close and addAcknowledgement in the same lock
                    // to avoid races with close / clearAll
                    var closed = !this.isOpenForTransmission();
                    if (closed) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            log(System.Logger.Level.DEBUG, "already closed: ignoring packet pn:%s",
                                      packet.packetNumber());
                        }
                        return;
                    }
                    addAcknowledgement(pending);
                    packetTransmissionTask.reschedule();
                } finally {
                    transferLock.unlock();
                }
            }
        }

    }

    /**
     * Computes the next deadline for generating a non ACK eliciting
     * packet containing the next ACK frame, or for retransmitting
     * unacknowledged packets for which retransmission is due.
     * This may be different to the {@link #nextScheduledDeadline()}
     * if newer changes have not been taken into account yet.
     *
     * @return the deadline at which the scheduler's task for this packet
     *        space should be scheduled to wake up
     */
    public Deadline computeNextDeadline() {
        return computeNextDeadline(true);
    }

    /**
     * Compute the next timer deadline for this packet space.
     *
     * @param verbose whether to emit verbose deadline diagnostics
     * @return next deadline, or {@link Deadline#MAX} when nothing is scheduled
     */
    public Deadline computeNextDeadline(boolean verbose) {

        if (closed) {
            logTraceIf(verbose, "closed - no deadline");
            return Deadline.MAX;
        }
        if (transmitNow) {
            logTraceIf(verbose, "transmit now");
            return Deadline.MIN;
        }
        if (pingRequested != null) {
            logTraceIf(verbose, "ping requested");
            return Deadline.MIN;
        }
        var ack = nextAckFrame;

        Deadline ackDeadline = (ack == null || ack.sent() != null)
                ? Deadline.MAX // if the ack frame has already been sent, nextAck() returns null
                : ack.deadline();
        if (blockedByPacer) {
            Deadline pacerDeadline = congestionController.pacerDeadline();
            logTraceIf(verbose, "pacer deadline: %s, ackDeadline: %s, deadline in %s",
                       pacerDeadline, ackDeadline, Utils.debugDeadline(now(), min(ackDeadline, pacerDeadline)));
            return min(ackDeadline, pacerDeadline);
        }
        Deadline retryDeadline = blockedByCC ? Deadline.MAX : failedRetransmissionDeadline();
        Deadline pendingDeadline = min(ackDeadline, retryDeadline);
        Deadline lossDeadline = lossTimer();
        // if both loss deadline and PTO timer are set, loss deadline is always earlier
        if (verbose && LOGGER.isLoggable(System.Logger.Level.TRACE) && lossDeadline != Deadline.MIN) {
            log(System.Logger.Level.TRACE, "lossDeadline is: " + lossDeadline);
        }
        if (lossDeadline != null) {
            if (verbose && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                if (lossDeadline == Deadline.MIN) {
                    log(System.Logger.Level.TRACE, "lossDeadline is immediate");
                } else if (!pendingDeadline.isBefore(lossDeadline)) {
                    log(System.Logger.Level.TRACE, "lossDeadline in %s ms",
                              Deadline.between(now(), lossDeadline).toMillis());
                } else {
                    log(System.Logger.Level.TRACE, "pending deadline before lossDeadline in %s ms",
                              Deadline.between(now(), pendingDeadline).toMillis());
                }
            }
            logTraceIf(verbose, "loss deadline: %s, pending deadline: %s, deadline in %s",
                       lossDeadline,
                       pendingDeadline,
                       Utils.debugDeadline(now(), min(pendingDeadline, lossDeadline)));
            return min(pendingDeadline, lossDeadline);
        }
        Deadline ptoDeadline = ptoDeadline();
        if (verbose && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            log(System.Logger.Level.TRACE, "ptoDeadline is: " + ptoDeadline);
        }
        if (ptoDeadline != null) {
            if (verbose && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                if (!pendingDeadline.isBefore(ptoDeadline)) {
                    log(System.Logger.Level.TRACE, "ptoDeadline in %s ms",
                              Deadline.between(now(), ptoDeadline).toMillis());
                } else {
                    log(System.Logger.Level.TRACE, "pending deadline before ptoDeadline in %s ms",
                              Deadline.between(now(), pendingDeadline).toMillis());
                }
            }
            logTraceIf(verbose, "PTO deadline: %s, pending deadline: %s, deadline in %s",
                       ptoDeadline,
                       pendingDeadline,
                       Utils.debugDeadline(now(), min(pendingDeadline, ptoDeadline)));
            return min(pendingDeadline, ptoDeadline);
        }
        if (verbose && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            if (pendingDeadline == Deadline.MAX) {
                log(System.Logger.Level.TRACE, "pending deadline is: Deadline.MAX");
            } else {
                log(System.Logger.Level.TRACE, "pending deadline in %s ms",
                          Deadline.between(now(), pendingDeadline).toMillis());
            }
        }
        if (pendingDeadline.equals(Deadline.MAX)) {
            logTraceIf(verbose,
                       "no deadline: pendingAcks: %s, triggered: %s, pendingRetransmit: %s",
                       pendingAcknowledgements.size(), triggeredForRetransmission.size(), pendingRetransmission.size());
        } else {
            logTraceIf(verbose, "deadline is %s", Utils.debugDeadline(now(), pendingDeadline));
        }
        return pendingDeadline;
    }

    /**
     * Returns the next deadline at which the scheduler's task for this packet
     * space is currently scheduled to wake up.
     *
     * @return the next deadline at which the scheduler's task for this packet
     *         space is currently scheduled to wake up
     */
    public Deadline nextScheduledDeadline() {
        return packetTransmissionTask.nextDeadline;
    }

    @Override
    public void processAckFrame(AckFrame frame) throws QuicTransportException {
        // for each acknowledged packet, remove it from the
        // list of packets pending acknowledgement, or from the
        // list of packets pending retransmission
        long largestAckAckedBefore = emittedAckTracker.largestAckAcked();
        validateAckFrame(frame);
        long largestAcknowledged = frame.largestAcknowledged();
        Deadline now = now();

        int lostCount;
        boolean pathControlCapacityReleased = false;
        transferLock.lock();
        try {
            ackProcessingState.reset();
            boolean largestAckAdvanced = largestAckReceived(largestAcknowledged);
            if (!largestAckAdvanced) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "RTT sample on packet %s ignored: not largest",
                              largestAcknowledged);
                }
            }
            acknowledgementScan.begin(frame, ++acknowledgementScanEpoch);
            List<PendingAcknowledgement> recovered = LOGGER.isLoggable(System.Logger.Level.DEBUG)
                    ? new ArrayList<>()
                    : null;
            scanAcknowledgements(recovered);
            pathRecoveryState.runWithCurrent(ackGeneration -> accountAcknowledgements(frame,
                                                                                      largestAcknowledged,
                                                                                      largestAckAdvanced,
                                                                                      now,
                                                                                      ackGeneration));
            lostCount = ackProcessingState.lostCount;
            pathControlCapacityReleased = ackProcessingState.pathControlCapacityReleased;
            if (largestAckAdvanced) {
                // complete PingRequests if needed
                processPingResponses(largestAcknowledged);
            }
            if (recovered != null && !recovered.isEmpty()) {
                log(System.Logger.Level.DEBUG,
                          "lost packets recovered: %s(%s) total unrecovered %s, unacknowledged %s",
                          packetType(),
                          recovered.stream().map(PendingAcknowledgement::packetNumber).toList(),
                          lostPackets.size(), pendingAcknowledgements.size() + pendingRetransmission.size());
            }
        } finally {
            try {
                ackProcessingState.clearAcknowledged();
            } finally {
                transferLock.unlock();
            }
        }

        long largestAckAcked = emittedAckTracker.largestAckAcked();
        if (largestAckAcked > largestAckAckedBefore) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "largestAckAcked=%d - cleaning up AckFrame",
                          largestAckAcked);
            }
            // remove ack ranges that we no longer need to acknowledge.
            // this implements the algorithm described in RFC 9000,
            // 13.2.4. Limiting Ranges by Tracking ACK Frames
            cleanupAcks();
        }

        if (lostCount > 0 || pathControlCapacityReleased) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG,
                    "Found %s lost packets%s",
                    lostCount,
                    pathControlCapacityReleased ? " and released path-control capacity" : "");
            }
            // retransmit if possible
            runTransmitter();
        } else if (blockedByCC && congestionController.canSendPacket()) {
            // CC just got unblocked... send more data
            blockedByCC = false;
            runTransmitter();
        } else {
            // RTT was updated, some packets might be lost, recompute timers
            packetTransmissionTask.reschedule();
        }
    }

    private void scanAcknowledgements(List<PendingAcknowledgement> recovered) {
        for (Iterator<PendingAcknowledgement> iterator = pendingRetransmission.iterator(); iterator.hasNext();) {
            PendingAcknowledgement pending = iterator.next();
            boolean firstScan = pending.acknowledgementScanEpoch != acknowledgementScan.epoch();
            if (acknowledgementScan.scan(pending)) {
                iterator.remove();
                if (firstScan) {
                    trackAcknowledgement(pending, acknowledgementScan.trackedLargestAcknowledged());
                }
            }
        }
        for (Iterator<PendingAcknowledgement> iterator = triggeredForRetransmission.iterator(); iterator.hasNext();) {
            PendingAcknowledgement pending = iterator.next();
            boolean firstScan = pending.acknowledgementScanEpoch != acknowledgementScan.epoch();
            if (acknowledgementScan.scan(pending)) {
                iterator.remove();
                if (firstScan) {
                    trackAcknowledgement(pending, acknowledgementScan.trackedLargestAcknowledged());
                }
            }
        }
        resetFailedRetransmissionStateIfIdle();
        for (Iterator<PendingAcknowledgement> iterator = pendingAcknowledgements.iterator(); iterator.hasNext();) {
            PendingAcknowledgement pending = iterator.next();
            boolean firstScan = pending.acknowledgementScanEpoch != acknowledgementScan.epoch();
            if (acknowledgementScan.scan(pending)) {
                iterator.remove();
                ackProcessingState.addAcknowledged(pending);
                if (firstScan) {
                    trackAcknowledgement(pending, acknowledgementScan.trackedLargestAcknowledged());
                }
            }
        }
        for (Iterator<PendingAcknowledgement> iterator = lostPackets.iterator(); iterator.hasNext();) {
            PendingAcknowledgement lost = iterator.next();
            if (acknowledgementScan.scan(lost)) {
                iterator.remove();
                if (recovered != null) {
                    recovered.add(lost);
                }
            }
        }
    }

    private void accountAcknowledgements(AckFrame frame,
                                         long largestAcknowledged,
                                         boolean largestAckAdvanced,
                                         Deadline now,
                                         long ackGeneration) {
        if (largestAckAdvanced) {
            consumeRttSample(frame, largestAcknowledged, now, ackGeneration);
        }
        for (PendingAcknowledgement pending = ackProcessingState.acknowledged;
                pending != null;
                pending = pending.acknowledgedNext) {
            if (pending.pathGeneration == ackGeneration) {
                congestionController.packetAcked(pending.packet.size(), pending.sent);
            }
        }
        ackProcessingState.lostCount = detectAndAccountLostPackets(now, ackGeneration);
        ackProcessingState.pathControlCapacityReleased = completePathControlFlights(frame, now, ackGeneration);
        if (largestAckAdvanced
                && packetNumberSpace != PacketNumberSpace.INITIAL
                && acknowledgementScan.newestAcknowledgedPathGeneration() == ackGeneration) {
            rttEstimator.resetPtoBackoff();
        }
    }

    private void consumeRttSample(AckFrame frame,
                                  long largestAcknowledged,
                                  Deadline now,
                                  long ackGeneration) {
        Deadline sentTime = acknowledgementScan.rttSent();
        if (sentTime == null || acknowledgementScan.rttPathGeneration() != ackGeneration) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "RTT sample on packet %s ignored: not ack eliciting",
                          largestAcknowledged);
            }
            return;
        }
        long ackDelayMicros;
        if (isApplicationSpace()) {
            confirmHandshake();
            long baseAckDelay = peerAckDelayToMicros(frame.ackDelay());
            if (largestAcknowledged >= handshakeConfirmedPN) {
                ackDelayMicros = Math.min(baseAckDelay,
                                          TimeUnit.MILLISECONDS.toMicros(peerMaxAckDelayMillis));
            } else {
                ackDelayMicros = baseAckDelay;
            }
        } else {
            ackDelayMicros = 0;
        }
        long rttSample = sentTime.until(now, ChronoUnit.MICROS);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "New RTT sample on packet %s: %s us (delay %s us)",
                      largestAcknowledged, rttSample, ackDelayMicros);
        }
        rttEstimator.consumeRttSample(rttSample, ackDelayMicros, now);
    }

    private boolean completePathControlFlights(AckFrame frame, Deadline now, long ackGeneration) {
        if (pathControlFlights.isEmpty()) {
            return false;
        }
        Deadline lossSendTime = now.minus(rttEstimator.lossThreshold());
        List<QuicPacket> completed = ackProcessingState.completedPathControls;
        completed.clear();
        for (Iterator<PathControlFlight> iterator = pathControlFlights.values().iterator(); iterator.hasNext();) {
            PathControlFlight flight = iterator.next();
            boolean acknowledged = frame.isAcknowledging(flight.packetNumber);
            boolean lost = flight.packetNumber < largestReceivedAckedPN
                    && (flight.packetNumber < largestReceivedAckedPN - PACKET_THRESHOLD
                            || !lossSendTime.isBefore(flight.sent));
            if (!acknowledged && !lost) {
                continue;
            }
            iterator.remove();
            if (flight.accountingGeneration == ackGeneration) {
                completed.add(flight.packet);
            }
        }
        if (completed.isEmpty()) {
            return false;
        }
        congestionController.packetDiscarded(completed);
        return true;
    }

    @Override
    public void confirmHandshake() {
        if (handshakeConfirmedPN == 0) {
            handshakeConfirmedPN = nextPN.get();
        }
    }

    @Override
    public Optional<AckFrame> nextAckFrame(boolean onlyOverdue) {
        return nextAckFrame(onlyOverdue, Integer.MAX_VALUE);
    }

    @Override
    public Optional<AckFrame> nextAckFrame(boolean onlyOverdue, int maxSize) {
        if (closed) {
            return Optional.empty();
        }
        NextAckFrame ack = nextAck(onlyOverdue, maxSize);
        if (ack == null) {
            return Optional.empty();
        }
        long delay = ack.lastUpdated()
                .until(now(), ChronoUnit.MICROS) >> localAckDelayExponent;
        return Optional.of(ack.ackFrame().withAckDelay(delay));
    }

    /**
     * Used to request sending of a ping frame, for instance, to verify that
     * the connection is alive.
     *
     * @return a completable future that will be completed with the time it
     *        took, in milliseconds, for the peer to acknowledge the packet that
     *        contained the PingFrame (or any packet that was sent after)
     */
    @Override
    public CompletableFuture<Long> requestSendPing() {
        CompletableFuture<Long> pingRequested;
        stateLock.lock();
        try {
            pingRequested = this.pingRequested;
            if (pingRequested == null) {
                pingRequested = MinimalFuture.create();
                this.pingRequested = pingRequested;
            }
        } finally {
            stateLock.unlock();
        }
        runTransmitter();
        return pingRequested;
    }

    @Override
    public boolean isAcknowledged(long packetNumber) {
        var ack = nextAckFrame;
        var ackFrame = ack == null ? null : ack.ackFrame();
        var largestProcessed = largestProcessedPN;
        // if ackFrame is null it means all packets <= largestProcessedPN
        // have been acked.
        if (ackFrame == null) {
            return packetNumber <= largestProcessed;
        }
        if (packetNumber > largestProcessed) {
            return false;
        }
        var largestAckedPNReceivedByPeer = this.largestAckedPNReceivedByPeer;
        if (packetNumber <= largestAckedPNReceivedByPeer) {
            return true;
        }
        return ackFrame.isAcknowledging(packetNumber);
    }

    @Override
    public void fastRetransmit() {
        if (closed || fastRetransmitDone) {
            return;
        }
        fastRetransmit = true;
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "Scheduling fast retransmit");
        }
        runTransmitter();

    }

    Deadline deadline() {
        return packetTransmissionTask.deadline();
    }

    Deadline prospectiveDeadline() {
        return computeNextDeadline(false);
    }

    void discardObsoletePathControlFlights(long generation) {
        transferLock.lock();
        try {
            pathControlFlights.values().removeIf(flight -> flight.accountingGeneration != generation);
        } finally {
            transferLock.unlock();
        }
    }

    void discardPathControlFlights(long pathGeneration) {
        boolean capacityReleased = false;
        transferLock.lock();
        try {
            long accountingGeneration = pathRecoveryState.generation();
            List<QuicPacket> discarded = null;
            for (Iterator<PathControlFlight> iterator = pathControlFlights.values().iterator(); iterator.hasNext();) {
                PathControlFlight flight = iterator.next();
                if (flight.pathGeneration != pathGeneration) {
                    continue;
                }
                iterator.remove();
                if (flight.accountingGeneration == accountingGeneration) {
                    if (discarded == null) {
                        discarded = new ArrayList<>();
                    }
                    discarded.add(flight.packet);
                }
            }
            if (discarded != null) {
                List<QuicPacket> discardedFlights = discarded;
                capacityReleased = pathRecoveryState.runIfCurrent(
                        accountingGeneration,
                        () -> congestionController.packetDiscarded(discardedFlights));
            }
        } finally {
            transferLock.unlock();
        }
        if (capacityReleased) {
            runTransmitter();
        }
    }

    /**
     * Discards outstanding packets in this packet space that match the supplied predicate.
     *
     * @param predicate packet predicate
     */
    void discardOutstandingPackets(Predicate<? super QuicPacket> predicate) {
        List<PendingAcknowledgement> discarded = new ArrayList<>();
        List<PathControlFlight> discardedPathControl = new ArrayList<>();
        transferLock.lock();
        try {
            discardMatchingOutstandingPackets(pendingAcknowledgements, predicate, discarded);
            discardMatchingOutstandingPackets(pendingRetransmission, predicate, discarded);
            triggeredForRetransmission.removeIf(pending -> predicate.test(pending.packet()));
            resetFailedRetransmissionStateIfIdle();
            lostPackets.removeIf(pending -> predicate.test(pending.packet()));
            for (Iterator<PathControlFlight> iterator = pathControlFlights.values().iterator(); iterator.hasNext();) {
                PathControlFlight flight = iterator.next();
                if (predicate.test(flight.packet)) {
                    iterator.remove();
                    discardedPathControl.add(flight);
                }
            }
        } finally {
            transferLock.unlock();
        }
        if (!discarded.isEmpty() || !discardedPathControl.isEmpty()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "discarded outstanding packets %s(%s)",
                          packetType(),
                          discarded.stream().map(PendingAcknowledgement::packetNumber).toList());
            }
            long generation = pathRecoveryState.generation();
            List<QuicPacket> currentPathPackets = new ArrayList<>();
            for (PendingAcknowledgement pending : discarded) {
                if (pending.pathGeneration == generation) {
                    currentPathPackets.add(pending.packet);
                }
            }
            for (PathControlFlight flight : discardedPathControl) {
                if (flight.accountingGeneration == generation) {
                    currentPathPackets.add(flight.packet);
                }
            }
            if (!currentPathPackets.isEmpty()) {
                pathRecoveryState.runIfCurrent(generation,
                                               () -> congestionController.packetDiscarded(currentPathPackets));
            }
            packetTransmissionTask.reschedule();
        }
    }

    boolean isClosing(QuicPacket packet) {
        var frames = packet.frames();
        if (frames == null || frames.isEmpty()) {
            return false;
        }
        return frames.stream()
                .anyMatch(ConnectionCloseFrame.class::isInstance);
    }

    PacketType packetType() {
        return switch (packetNumberSpace) {
            case INITIAL -> PacketType.INITIAL;
            case HANDSHAKE -> PacketType.HANDSHAKE;
            case APPLICATION -> PacketType.ONERTT;
            case NONE -> PacketType.NONE;
        };
    }

    // returns the PTO duration
    Duration ptoDuration() {
        var pto = rttEstimator.basePtoDuration()
                .plusMillis(peerMaxAckDelayMillis)
                .multipliedBy(rttEstimator.ptoBackoff());
        var max = rttEstimator.maxPtoBackoffTimeout();
        // don't allow PTO > 240s
        return pto.compareTo(max) > 0 ? max : pto;
    }

    // returns the persistent congestion duration
    Duration persistentCongestionDuration() {
        return rttEstimator.basePtoDuration()
                .plusMillis(peerMaxAckDelayMillis)
                .multipliedBy(PERSISTENT_CONGESTION_THRESHOLD);
    }

    void debugState() {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "state: %s", isClosed() ? "closed" : "opened");
            log(System.Logger.Level.DEBUG, "AckFrame: " + nextAckFrame);
            String pendingAcks = pendingAcknowledgements.stream()
                    .map(PendingAcknowledgement::prettyPrint)
                    .collect(Collectors.joining(", ", "(", ")"));
            String pendingRetransmit = pendingRetransmission.stream()
                    .map(PendingAcknowledgement::prettyPrint)
                    .collect(Collectors.joining(", ", "(", ")"));
            log(System.Logger.Level.DEBUG, "Pending acks: %s", pendingAcks);
            log(System.Logger.Level.DEBUG, "Pending retransmit: %s", pendingRetransmit);
        }
    }

    void debugState(String prefix, StringBuilder sb) {
        String state = isClosed() ? "closed" : "opened";
        sb.append(prefix).append("State: ").append(state).append('\n');
        sb.append(prefix).append("AckFrame: ").append(nextAckFrame).append('\n');
        String pendingAcks = pendingAcknowledgements.stream()
                .map(PendingAcknowledgement::prettyPrint)
                .collect(Collectors.joining(", ", "(", ")"));
        String pendingRetransmit = pendingRetransmission.stream()
                .map(PendingAcknowledgement::prettyPrint)
                .collect(Collectors.joining(", ", "(", ")"));
        sb.append(prefix).append("Pending acks: ").append(pendingAcks).append('\n');
        sb.append(prefix).append("Pending retransmit: ").append(pendingRetransmit);
    }

    private static Deadline min(Deadline one, Deadline two) {
        return two.isAfter(one) ? one : two;
    }

    private Deadline failedRetransmissionDeadline() {
        if (triggeredForRetransmission.isEmpty()) {
            return Deadline.MAX;
        }
        return failedRetransmissionDeadline;
    }

    private void resetFailedRetransmissionStateIfIdle() {
        if (triggeredForRetransmission.isEmpty() && !retransmissionAttemptActive) {
            resetFailedRetransmissionState();
        }
    }

    private void resetFailedRetransmissionState() {
        failedRetransmissionDeadline = Deadline.MAX;
        failedRetransmissionBackoff = 1;
    }

    private static int nextOptimisticAckSkipInterval() {
        return OPTIMISTIC_ACK_RANDOM.nextInt(OPTIMISTIC_ACK_SKIP_INTERVAL_MIN,
                                             OPTIMISTIC_ACK_SKIP_INTERVAL_MAX + 1);
    }

    // remove all pending acknowledgements and retransmissions.
    private void clearAll() {
        transferLock.lock();
        try {
            long generation = pathRecoveryState.generation();
            List<QuicPacket> currentPathPackets = new ArrayList<>();
            for (PendingAcknowledgement acknowledgement : pendingAcknowledgements) {
                if (acknowledgement.pathGeneration == generation) {
                    currentPathPackets.add(acknowledgement.packet);
                }
            }
            for (PathControlFlight flight : pathControlFlights.values()) {
                if (flight.accountingGeneration == generation) {
                    currentPathPackets.add(flight.packet);
                }
            }
            pathRecoveryState.runIfCurrent(generation,
                                           () -> congestionController.packetDiscarded(currentPathPackets));
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                StringBuilder sb = new StringBuilder();
                pendingAcknowledgements.forEach((p) -> sb.append(" ").append(p));
                if (!sb.isEmpty()) {
                    log(System.Logger.Level.DEBUG, "forgetting pending acks: " + sb);
                }
            }
            pendingAcknowledgements.clear();
            pathControlFlights.clear();

            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                StringBuilder sb = new StringBuilder();
                pendingRetransmission.forEach((p) -> sb.append(" ").append(p));
                if (!sb.isEmpty()) {
                    log(System.Logger.Level.DEBUG, "forgetting pending retransmissions: " + sb);
                }
            }
            pendingRetransmission.clear();

            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                StringBuilder sb = new StringBuilder();
                triggeredForRetransmission.forEach((p) -> sb.append(" ").append(p));
                if (!sb.isEmpty()) {
                    log(System.Logger.Level.DEBUG, "forgetting triggered-for-retransmissions: " + sb.toString());
                }
            }
            triggeredForRetransmission.clear();
            retransmissionAttemptActive = false;
            resetFailedRetransmissionState();

            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                StringBuilder sb = new StringBuilder();
                lostPackets.forEach((p) -> sb.append(" ").append(p));
                if (!sb.isEmpty()) {
                    log(System.Logger.Level.DEBUG, "forgetting lost-packets: " + sb.toString());
                }
            }
            lostPackets.clear();
        } finally {
            transferLock.unlock();
        }
    }

    private void retransmitPTO() throws QuicKeyUnavailableException, QuicTransportException {
        if (!isOpenForTransmission()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "already closed; retransmission on PTO dropped");
            }
            clearAll();
            return;
        }

        PendingAcknowledgement pending;
        transferLock.lock();
        try {
            pending = null;
            for (PendingAcknowledgement candidate : pendingAcknowledgements) {
                if (hasRetransmittableFrames(candidate.packet())
                        && pendingAcknowledgements.remove(candidate)) {
                    pending = candidate;
                    break;
                }
            }
            if (pending != null) {
                retransmissionAttemptActive = true;
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Retransmit on PTO: looking for candidate");
                }
                // The PTO probe is moved to the retransmission queue and removed from
                // congestion accounting until it is emitted again.
                PendingAcknowledgement selected = pending;
                pathRecoveryState.runIfCurrent(selected.pathGeneration,
                                               () -> congestionController.packetDiscarded(List.of(selected.packet)));
                pendingRetransmission.add(pending);
            }
        } finally {
            transferLock.unlock();
        }
        if (pending != null) {
            emitRetransmission(pending);
        }
    }

    /**
     * Returns true if this packet space isn't closed and if the underlying packet emitter
     * is open, otherwise returns false.
     *
     * @return true if this packet space isn't closed and if the underlying packet emitter
     *         is open, otherwise returns false
     */
    private boolean isOpenForTransmission() {
        return !this.closed && this.packetEmitter.isOpen();
    }

    private void logDebugIf(boolean enabled, String format, Object... args) {
        if (enabled && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, format, args);
        }
    }

    private void logTraceIf(boolean enabled, String format, Object... args) {
        if (enabled && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            log(System.Logger.Level.TRACE, format, args);
        }
    }

    private void log(System.Logger.Level level, String format, Object... arguments) {
        LOGGER.log(level,
                   () -> "[" + debugDescriptionSupplier.get() + "] "
                           + (arguments.length == 0 ? format : format.formatted(arguments)));
    }

    private void log(System.Logger.Level level, String message, Throwable throwable) {
        LOGGER.log(level, "[" + debugDescriptionSupplier.get() + "] " + message, throwable);
    }

    // adds the PingRequest to the pendingPingRequests queue so
    // that it can be completed when the packet is ACK'ed.
    private void registerPingRequest(PingRequest pingRequest) {
        if (closed) {
            pingRequest.response().completeExceptionally(new IllegalStateException("Packet space is closed"));
            return;
        }
        pendingPingRequests.add(pingRequest);
        // could be acknowledged already!
        processPingResponses(largestReceivedAckedPN);
    }

    // Called by the retransmitLoop scheduler.
    // Retransmit one packet for which retransmission has been triggered by
    // the PacketTransmissionTask.
    // return true if something was retransmitted, or false if there was nothing to retransmit
    private boolean retransmit() throws QuicKeyUnavailableException, QuicTransportException {
        PendingAcknowledgement pending;
        var closed = !this.isOpenForTransmission();
        if (closed) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "already closed; retransmission dropped");
            }
            clearAll();
            return false;
        }
        transferLock.lock();
        try {
            Deadline retryDeadline = failedRetransmissionDeadline;
            if (!Deadline.MAX.equals(retryDeadline) && retryDeadline.isAfter(now())) {
                pending = null;
            } else {
                pending = triggeredForRetransmission.poll();
            }
            if (pending != null) {
                // Consume only the absolute deadline. Keep the backoff until an
                // emission succeeds or the queue no longer owns retry state.
                failedRetransmissionDeadline = Deadline.MAX;
                retransmissionAttemptActive = true;
            } else {
                resetFailedRetransmissionStateIfIdle();
            }
        } finally {
            transferLock.unlock();
        }

        if (pending != null) {
            // allocate new packet number
            // create new packet
            // encrypt packet
            // send packet
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "handle: retransmitting...");
            }
            if (!emitRetransmission(pending)) {
                return false;
            }
            return true;
        }
        return false;
    }

    private boolean emitRetransmission(PendingAcknowledgement pending)
            throws QuicKeyUnavailableException, QuicTransportException {
        try {
            if (!packetEmitter.retransmit(this, pending.packet(), pending.attempts())) {
                requeueFailedRetransmission(pending);
                return false;
            }
            retransmissionSucceeded();
            return true;
        } catch (RuntimeException | Error failure) {
            retransmissionAborted();
            throw failure;
        }
    }

    private void requeueFailedRetransmission(PendingAcknowledgement pending) {
        transferLock.lock();
        try {
            retransmissionAttemptActive = false;
            if (isOpenForTransmission()
                    && pendingRetransmission.contains(pending)
                    && !triggeredForRetransmission.contains(pending)) {
                triggeredForRetransmission.add(pending);
                Deadline now = now();
                failedRetransmissionDeadline = now.plus(failedRetransmissionRetryDelay());
                long maxBackoff = rttEstimator.maxPtoBackoff();
                if (failedRetransmissionBackoff < maxBackoff) {
                    failedRetransmissionBackoff =
                            Math.min(maxBackoff, failedRetransmissionBackoff * 2);
                }
            } else {
                resetFailedRetransmissionStateIfIdle();
            }
        } finally {
            transferLock.unlock();
        }
    }

    private Duration failedRetransmissionRetryDelay() {
        Duration basePto = rttEstimator.basePtoDuration().plusMillis(peerMaxAckDelayMillis);
        Duration lossThreshold = rttEstimator.lossThreshold();
        Duration baseDelay = basePto.compareTo(lossThreshold) < 0 ? lossThreshold : basePto;
        Duration retryDelay = baseDelay.multipliedBy(failedRetransmissionBackoff);
        Duration maxDelay = rttEstimator.maxPtoBackoffTimeout();
        return retryDelay.compareTo(maxDelay) > 0 ? maxDelay : retryDelay;
    }

    private void retransmissionSucceeded() {
        transferLock.lock();
        try {
            retransmissionAttemptActive = false;
            resetFailedRetransmissionState();
        } finally {
            transferLock.unlock();
        }
    }

    private void retransmissionAborted() {
        transferLock.lock();
        try {
            retransmissionAttemptActive = false;
            resetFailedRetransmissionStateIfIdle();
        } finally {
            transferLock.unlock();
        }
    }

    /**
     * Called by the {@link PacketTransmissionTask} to
     * generate a non ACK eliciting packet containing only the given
     * ACK frame.
     *
     * <p> If a received packet is ACK-eliciting, then it will be either
     * directly acknowledged by {@link QuicConnectionImpl} - which will
     * call {@link #nextAckFrame(boolean)}  to embed the {@link AckFrame}
     * in a packet, or by a non-eliciting ACK packet which will be
     * triggered {@link #maxAckDelay() maxAckDelay} after the reception
     * of the ACK-eliciting packet (this method, triggered by the {@link
     * PacketTransmissionTask}).
     *
     * <p> This method doesn't reset the {@linkplain #nextAckFrame(boolean)
     * next ack frame} to be sent, but reset its delay so that only
     * one non ACK-eliciting packet is emitted to acknowledge a given
     * packet.
     *
     * @param ackFrame The ACK frame to send.
     * @return the packet number of the emitted packet
     */
    private long emitAckPacket(AckFrame ackFrame, boolean sendPing)
            throws QuicKeyUnavailableException, QuicTransportException {
        boolean closed = !this.isOpenForTransmission();
        if (closed) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "Packet space closed, ack/ping won't be sent"
                        + (ackFrame != null ? ": " + ackFrame : ""));
            }
            return -1L;
        }
        try {
            return packetEmitter.emitAckPacket(this, ackFrame, sendPing);
        } catch (QuicKeyUnavailableException | QuicTransportException e) {
            if (!this.isOpenForTransmission()) {
                // possible race condition where the packet space was closed (and keys discarded)
                // while there was an attempt to send an ACK/PING frame.
                // Ignore such cases, since it's OK to not send those frames when the packet space
                // is already closed
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "ack/ping wasn't sent since packet space was closed"
                            + (ackFrame != null ? ": " + ackFrame : ""));
                }
                return -1L;
            }
            throw e;
        }
    }

    private void lastAckElicitingSent(long packetNumber) {
        stateLock.lock();
        try {
            if (largestAckElicitingSentPN < packetNumber) {
                largestAckElicitingSentPN = packetNumber;
            }
        } finally {
            stateLock.unlock();
        }
    }

    private void addAcknowledgement(PendingAcknowledgement ack) {
        lastAckElicitingSent(ack.sent);
        lastAckElicitingSent(ack.packetNumber);
        pendingAcknowledgements.add(ack);
        pathRecoveryState.runIfCurrent(ack.pathGeneration,
                                       () -> congestionController.packetSent(ack.packet().size()));
    }

    private void discardMatchingOutstandingPackets(ConcurrentLinkedQueue<PendingAcknowledgement> queue,
                                                   Predicate<? super QuicPacket> predicate,
                                                   List<PendingAcknowledgement> discarded) {
        for (Iterator<PendingAcknowledgement> iterator = queue.iterator(); iterator.hasNext();) {
            PendingAcknowledgement pending = iterator.next();
            if (!predicate.test(pending.packet())) {
                continue;
            }
            iterator.remove();
            discarded.add(pending);
        }
    }

    private Deadline now() {
        return instantSource.instant();
    }

    /**
     * Tracks the largest packet acknowledged by the packets acknowledged in the
     * given AckFrame. This helps to implement the algorithm described in
     * RFC 9000,  13.2.4. Limiting Ranges by Tracking ACK Frames.
     *
     * @param pending a yet unacknowledged packet that may be acknowledged
     *               by the given{@link AckFrame}.
     * @param largestAcknowledged the largest packet acknowledged by the matching transmission
     */
    private void trackAcknowledgement(PendingAcknowledgement pending, long largestAcknowledged) {
        emittedAckTracker.trackAcknowledgement(pending, largestAcknowledged);
    }

    private static boolean hasRetransmittableFrames(QuicPacket packet) {
        for (QuicFrame frame : packet.frames()) {
            if (!(frame instanceof AckFrame)
                    && !(frame instanceof PaddingFrame)
                    && !(frame instanceof PathChallengeFrame)
                    && !(frame instanceof PathResponseFrame)) {
                return true;
            }
        }
        return false;
    }

    private long peerAckDelayToMicros(long ackDelay) {
        return ackDelay << peerAckDelayExponent;
    }

    private NextAckFrame nextAck(boolean onlyOverdue, int maxSize) {
        Deadline now = now();
        // This method is called to retrieve the AckFrame that will
        // be embedded in the next packet sent to the peer.
        // We therefore need to disarm the timer that will send a
        // non-ACK eliciting packet with that AckFrame (if any) before
        // returning the AckFrame. This is the purpose of the loop
        // below...
        while (true) {
            NextAckFrame ack = nextAckFrame;
            if (ack == null
                    || ack.deadline() == Deadline.MAX
                    || (onlyOverdue && ack.deadline().isAfter(now))
                    || ack.sent() != null) {
                return null;
            }
            // also reserve 3 bytes for the ack delay
            if (ack.ackFrame().size() > maxSize - 3) {
                return null;
            }
            NextAckFrame newAck = ack.withDeadline(Deadline.MAX, now);
            boolean respin = !Handles.NEXTACK.compareAndSet(this, ack, newAck);
            if (!respin) {
                return ack;
            }
        }
    }

    /**
     * Finds unacknowledged packets that should be declared lost.
     * The lost packets are moved from the pendingAcknowledgements
     * into the pendingRetransmission.
     *
     * @param now        current time, used for time-based loss detection
     * @param generation current recovery generation
     * @return number of packets declared lost
     */
    private int detectAndAccountLostPackets(Deadline now, long generation) {
        Deadline lossSendTime = now.minus(rttEstimator.lossThreshold());
        int count = 0;
        // log(System.Logger.Level.DEBUG, "preparing for retransmission");
        List<PendingAcknowledgement> lost = LOGGER.isLoggable(System.Logger.Level.DEBUG) ? new ArrayList<>() : null;
        List<QuicPacket> packets = ackProcessingState.lostPackets;
        packets.clear();
        Deadline firstSendTime = null;
        Deadline lastSendTime = null;
        for (PendingAcknowledgement head = pendingAcknowledgements.peek();
                head != null && head.packetNumber < largestReceivedAckedPN;
                head = pendingAcknowledgements.peek()) {
            if (head.packetNumber < largestReceivedAckedPN - PACKET_THRESHOLD
                    || !lossSendTime.isBefore(head.sent)) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "retransmit:head pn:" + head.packetNumber
                            + ",largest acked PN:" + largestReceivedAckedPN
                            + ",sent:" + head.sent
                            + ",lossSendTime:" + lossSendTime
                    );
                }
                if (pendingAcknowledgements.remove(head)) {
                    if (hasRetransmittableFrames(head.packet())) {
                        pendingRetransmission.add(head);
                        triggeredForRetransmission.add(head);
                    }
                    if (head.pathGeneration == generation) {
                        packets.add(head.packet);
                        if (firstSendTime == null) {
                            firstSendTime = head.sent;
                        }
                        lastSendTime = head.sent;
                    }
                    if (lostPackets.add(head) && head.previousNumbers != null) {
                        for (Iterator<PendingAcknowledgement> iterator = lostPackets.iterator(); iterator.hasNext();) {
                            PendingAcknowledgement lostEntry = iterator.next();
                            if (lostEntry != head && head.hasPreviousNumber(lostEntry.packetNumber)) {
                                iterator.remove();
                            }
                        }
                    }
                    count++;
                    if (lost != null) {
                        lost.add(head);
                    }
                }
            } else {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "no retransmit:head pn:" + head.packetNumber
                            + ",largest acked PN:" + largestReceivedAckedPN
                            + ",sent:" + head.sent
                            + ",lossSendTime:" + lossSendTime
                    );
                }
                break;
            }
        }
        boolean persistent = !packets.isEmpty()
                && Deadline.between(firstSendTime, lastSendTime)
                        .compareTo(persistentCongestionDuration()) > 0;
        if (!packets.isEmpty()) {
            // Persistent congestion is detected more aggressively than mandated by RFC 9002:
            // - may be reported even if there's no prior RTT sample
            // - may be reported even if there are acknowledged packets between the lost ones
            congestionController.packetLost(packets, lastSendTime, persistent);
        }
        if (lost != null && !lost.isEmpty()) {
            log(System.Logger.Level.DEBUG,
                      "lost packet %s(%s) total unrecovered %s, unacknowledged %s",
                      packetType(), lost.stream().map(PendingAcknowledgement::packetNumber).toList(),
                      lostPackets.size(), pendingAcknowledgements.size() + pendingRetransmission.size());
        }
        return count;
    }

    /**
     * Returns true if PTO timer expired, false otherwise.
     *
     * @return true if PTO timer expired, false otherwise.
     */
    private boolean isPTO(Deadline now) {
        Deadline ptoDeadline = ptoDeadline();
        return ptoDeadline != null && !ptoDeadline.isAfter(now);
    }

    // returns true if this space is the APPLICATION space
    private boolean isApplicationSpace() {
        return packetNumberSpace == PacketNumberSpace.APPLICATION;
    }

    private Deadline ptoDeadline() {
        if (packetNumberSpace == PacketNumberSpace.INITIAL && lastAckElicitingTime != null) {
            if (!quicTLSEngine.keysAvailable(QuicTLSEngine.KeySpace.HANDSHAKE)) {
                // if handshake keys are not available, initial PTO must be set
                return lastAckElicitingTime.plus(ptoDuration());
            }
        }
        if (packetNumberSpace == PacketNumberSpace.HANDSHAKE) {
            // set anti-deadlock timer
            if (lastAckElicitingTime == null) {
                lastAckElicitingTime = now();
            }
            if (largestAckElicitingSentPN == -1) {
                return lastAckElicitingTime.plus(ptoDuration());
            }
        }
        if (largestAckElicitingSentPN <= largestReceivedAckedPN) {
            return null;
        }
        // Application space deadline can only be set when handshake is confirmed
        if (isApplicationSpace() && quicTLSEngine.handshakeState() != QuicTLSEngine.HandshakeState.HANDSHAKE_CONFIRMED) {
            return null;
        }
        return lastAckElicitingTime.plus(ptoDuration());
    }

    private Deadline lossTimer() {
        PendingAcknowledgement head = pendingAcknowledgements.peek();
        if (head == null || head.packetNumber >= largestReceivedAckedPN) {
            return null;
        }
        if (head.packetNumber < largestReceivedAckedPN - PACKET_THRESHOLD) {
            return Deadline.MIN;
        }
        return head.sent.plus(rttEstimator.lossThreshold());
    }

    // Compute the new deadline when adding an ack-eliciting packet number
    // to an ack frame which is not empty.
    private Deadline computeNewDeadlineFor(AckFrame frame, Deadline now, Deadline deadline,
                                           long packetNumber, long previousLargest,
                                           long ackDelay) {

        boolean previousEliciting = !deadline.equals(Deadline.MAX);

        if (closed) {
            return Deadline.MAX;
        }

        if (previousEliciting) {
            // RFC 9000 #13.2.2:
            // We should send an ACK immediately after receiving two
            // ACK-eliciting packets
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "two ACK-Eliciting packets received: "
                        + "next ack deadline now");
            }
            return now;
        } else if (packetNumber < previousLargest) {
            // RFC 9000 #13.2.1:
            // if the packet has PN less than another ack-eliciting packet,
            // send ACK frame as soon as possible
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "ACK-Eliciting packet received out of order: "
                        + "next ack deadline now");
            }
            return now;
        } else if (packetNumber - 1 > previousLargest && previousLargest > -1) {
            // RFC 9000 #13.2.1:
            // Check whether there are gaps between this packet and the
            // previous ACK-eliciting packet that was received:
            // if we find any gap we should send an ACK frame as soon
            // as possible
            if (!frame.isRangeAcknowledged(previousLargest + 1, packetNumber)) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "gaps detected between this packet"
                            + " and the previous ACK eliciting packet: "
                            + "next ack deadline now");
                }
                return now;
            }
        }
        // send ACK within max delay
        return now.plusMillis(ackDelay);
    }

    // Look at whether a ping frame should be sent with the
    // next ACK frame...
    // If a PING frame should be sent, return the new deadline (now)
    // Otherwise, return Deadline.MAX;
    // A PING frame will be sent if:
    //     - the AckFrame contains more than (10) ACK Ranges
    //     - and no ACK eliciting packet was sent, or the last ACK-eliciting was
    //       sent long enough ago - typically 1 PTO delay
    // These numbers are implementation dependent and not defined in the RFC, but
    // help implement a strategy that sends occasional PING frames to limit the size
    // of the ACK frames - as described in RFC 9000.
    //
    // See RFC 9000 Section 13.2.4
    private boolean shouldSendPing(Deadline now, AckFrame frame) {
        Deadline last = lastAckElicitingTime;
        if (frame != null
                && (
                        last == null
                                || last.isBefore(now.minus(rttEstimator.basePtoDuration())))
                && frame.ackRanges().size() > MAX_ACKRANGE_COUNT_BEFORE_PING) {
            return true;
        }
        return false;
    }

    // Keep immutable AckFrame snapshots here. Reusing a builder would require
    // additional locking around mutation and packet-space handoff.
    // This method is called when a new packet is received, and it adds the
    // received packet number to the next ACK frame to send out.
    // If the packet is ACK eliciting it also arms a timeout (if needed)
    // to make sure the packet will be acknowledged within the committed
    // time frame.
    private void addToAckFrame(long packetNumber, boolean isAckEliciting) {

        long largestAckEliciting = largestAckElicitingReceivedPN;
        if (isAckEliciting) {
            ackElicitingPacketProcessed(packetNumber);
        }

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            if (packetNumber < largestAckEliciting) {
                log(System.Logger.Level.DEBUG, "already received a larger ACK eliciting packet");
            }
        }

        // compute a new AckFrame that includes the
        // provided packet number
        NextAckFrame nextAckFrame;
        NextAckFrame ack = null;
        boolean reschedule;
        long largestAckAcked;
        long newLargestAckAcked = -1;
        do {
            Deadline now = now();
            nextAckFrame = this.nextAckFrame;
            var frame = nextAckFrame == null ? null : nextAckFrame.ackFrame();
            largestAckAcked = emittedAckTracker.largestAckAcked();
            boolean needNewFrame = (frame == null || !frame.isAcknowledging(packetNumber))
                    && packetNumber > largestAckAcked;
            if (needNewFrame) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Adding packet %d to ackFrame %s (ackEliciting %s)",
                              packetNumber, nextAckFrame, isAckEliciting);
                }
                AckFrameBuilder builder = null;
                if (frame != null
                        && frame.ackRanges().size() == 1
                        && packetNumber == frame.largestAcknowledged() + 1
                        && largestAckAcked < frame.smallestAcknowledged()) {
                    frame = frame.withNextAcknowledged(packetNumber);
                } else {
                    builder = (frame == null ? AckFrameBuilder.create() : AckFrameBuilder.create(frame))
                            .dropAcksBefore(largestAckAcked)
                            .addAck(packetNumber);
                    frame = builder.build();
                }

                // Note: we could optimize this if needed by simply using a max number of
                // ranges: we could pre-compute the approximate size of a frame that has N ranges
                // and use that.
                int maxFrameSize = QuicConnectionImpl.SMALLEST_MAXIMUM_DATAGRAM_SIZE - 100;
                if (frame.size() > maxFrameSize) {
                    // frame is too big. We will drop some ranges
                    if (builder == null) {
                        builder = AckFrameBuilder.create(frame);
                    }
                    int ranges = frame.ackRanges().size();
                    int index = ranges / 3;
                    builder.dropAckRangesAfter(index);
                    newLargestAckAcked = builder.largestAckAcked();
                    var newFrame = builder.build();
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG,
                                  "frame too big (%s bytes) dropping ack ranges after %s, will ignore packets smaller than %s "
                + "(new frame: %s bytes)",
                                  frame.size(),
                                  index,
                                  newLargestAckAcked,
                                  newFrame.size());
                    }
                    frame = newFrame;
                }
                if (nextAckFrame == null) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "no previous ackframe");
                    }
                    Deadline deadline = isAckEliciting
                            ? now.plusMillis(maxAckDelay)
                            : Deadline.MAX;
                    ack = new NextAckFrame(frame, deadline, now, null);
                    reschedule = isAckEliciting;
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "next deadline: " + maxAckDelay);
                    }
                } else {
                    Deadline deadline = nextAckFrame.deadline();
                    Deadline nextDeadline = deadline;
                    boolean deadlineNotExpired = now.isBefore(deadline);
                    if (isAckEliciting && deadlineNotExpired) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            log(System.Logger.Level.DEBUG, "computing new deadline for ackframe");
                        }
                        nextDeadline = computeNewDeadlineFor(frame, now, deadline,
                                                             packetNumber, largestAckEliciting, maxAckDelay);
                    }
                    long millisToNext = nextDeadline.equals(Deadline.MAX)
                            ? Long.MAX_VALUE
                            : now.until(nextDeadline, ChronoUnit.MILLIS);
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        if (nextDeadline == Deadline.MAX) {
                            log(System.Logger.Level.DEBUG, "next deadline is: Deadline.MAX");
                        } else {
                            log(System.Logger.Level.DEBUG, "next deadline is: " + millisToNext);
                        }
                    }
                    ack = new NextAckFrame(frame, nextDeadline, now, null);
                    reschedule = !nextDeadline.equals(deadline)
                            || millisToNext <= 0;
                }
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    String delay = reschedule ? Utils.millis(now(), ack.deadline())
                            : "not rescheduled";
                    log(System.Logger.Level.DEBUG, "new ackFrame composed: %s - reschedule=%s",
                              ack.ackFrame(), delay);
                }
            } else {
                reschedule = false;
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "packet %d is already in ackFrame %s",
                              packetNumber, nextAckFrame);
                }
                break;
            }
        } while (!Handles.NEXTACK.compareAndSet(this, nextAckFrame, ack));

        if (newLargestAckAcked >= 0) {
            // we reduced the frame because it was too big: we need to ignore
            // packets that are larger than the new largest ignored packet.
            // this is now our new de-facto 'largestAckAcked' even if it wasn't
            // really acked by the peer
            emittedAckTracker.dropPacketNumbersSmallerThan(newLargestAckAcked);
        }

        if (reschedule) {
            runTransmitter();
        }
    }

    // This implements the algorithm described in RFC 9000:
    // 13.2.4. Limiting Ranges by Tracking ACK Frames
    private void cleanupAcks() {
        // clean up the next ACK frame, removing all packets <= largestAckAcked
        NextAckFrame nextAckFrame;
        NextAckFrame ack = null;
        long largestAckAcked;
        do {
            nextAckFrame = this.nextAckFrame;
            if (nextAckFrame == null) {
                return; // nothing to do!
            }
            var frame = nextAckFrame.ackFrame();
            largestAckAcked = emittedAckTracker.largestAckAcked();
            boolean needNewFrame = frame != null
                    && frame.smallestAcknowledged() <= largestAckAcked;
            if (needNewFrame) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Dropping all acks below %d in ackFrame %s",
                              largestAckAcked, nextAckFrame);
                }
                var builder = AckFrameBuilder
                        .create(frame)
                        .dropAcksBefore(largestAckAcked);
                frame = builder.isEmpty() ? null : builder.build();
                if (frame == null) {
                    ack = null;
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "ackFrame cleared - nothing to acknowledge");
                    }
                } else {
                    Deadline deadline = nextAckFrame.deadline();
                    ack = new NextAckFrame(frame, deadline,
                                           nextAckFrame.lastUpdated(), nextAckFrame.sent());
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "ackFrame cleaned up: %s",
                                  ack.ackFrame());
                    }
                }
            } else {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "no packet smaller than %d in ackFrame %s",
                              largestAckAcked, nextAckFrame);
                }
                break;
            }
        } while (!Handles.NEXTACK.compareAndSet(this, nextAckFrame, ack));

    }

    private long ackElicitingPacketProcessed(long packetNumber) {
        long largestPN;
        do {
            largestPN = largestAckElicitingReceivedPN;
            if (largestPN >= packetNumber) {
                return largestPN;
            }
        } while (!Handles.LARGEST_ACK_ELICITING_RECEIVED_PN
                .compareAndSet(this, largestPN, packetNumber));
        return packetNumber;
    }

    private long packetProcessed(long packetNumber) {
        long largestPN;
        do {
            largestPN = largestProcessedPN;
            if (largestPN >= packetNumber) {
                return largestPN;
            }
        } while (!Handles.LARGEST_PROCESSED_PN
                .compareAndSet(this, largestPN, packetNumber));
        return packetNumber;
    }

    /**
     * Theoretically we should wait for the packet that contains the
     * ping frame to be acknowledged, but if we receive the ack of a
     * packet with a larger number, we can assume that the connection
     * is still alive, and therefore complete the ping response.
     *
     * @param packetNumber the acknowledged packet number
     */
    private void processPingResponses(long packetNumber) {
        if (pendingPingRequests.isEmpty()) {
            return;
        }
        var iterator = pendingPingRequests.iterator();
        while (iterator.hasNext()) {
            var pr = iterator.next();
            if (pr.packetNumber() <= packetNumber) {
                iterator.remove();
                pr.response().complete(pr.sent().until(now(), ChronoUnit.MILLIS));
            } else {
                // this is a queue, so the PingRequest with the smaller
                // packet number will be at the head. We can stop iterating
                // as soon as we find a PingRequest that has a packet
                // number larger than the one acknowledged.
                break;
            }
        }
    }

    private long largestAckSent(long packetNumber) {
        long largestPN;
        do {
            largestPN = largestSentAckedPN;
            if (largestPN >= packetNumber) {
                return largestPN;
            }
        } while (!Handles.LARGEST_SENT_ACKED_PN
                .compareAndSet(this, largestPN, packetNumber));
        return packetNumber;
    }

    private boolean largestAckReceived(long packetNumber) {
        long largestPN;
        do {
            largestPN = largestReceivedAckedPN;
            if (largestPN >= packetNumber) {
                return false; // already up to date
            }
        } while (!Handles.LARGEST_RECEIVED_ACKED_PN
                .compareAndSet(this, largestPN, packetNumber));
        return true; // updated
    }

    private boolean optimisticAckDetectionEnabled() {
        return packetNumberSpace == PacketNumberSpace.APPLICATION;
    }

    private boolean shouldSkipPacketNumber() {
        if (!optimisticAckDetectionEnabled()) {
            return false;
        }

        while (true) {
            long remaining = packetsUntilOptimisticAckSkip.get();
            if (remaining > 1) {
                if (packetsUntilOptimisticAckSkip.compareAndSet(remaining, remaining - 1)) {
                    return false;
                }
            } else {
                long nextInterval = nextOptimisticAckSkipInterval();
                if (packetsUntilOptimisticAckSkip.compareAndSet(remaining, nextInterval)) {
                    return true;
                }
            }
        }
    }

    private void recordSkippedPacketNumber(long packetNumber) {
        skippedPacketNumbers.add(packetNumber);
    }

    private void validateAckFrame(AckFrame frame) throws QuicTransportException {
        long largestAcknowledged = frame.largestAcknowledged();
        if (largestAcknowledged >= nextPN.get()) {
            throw new QuicTransportException("Acknowledgement for a nonexistent packet",
                                             frame.typeField(), QuicTransportErrors.PROTOCOL_VIOLATION);
        }
        if (skippedPacketNumbers.isEmpty()) {
            return;
        }

        long smallestAcknowledged = frame.smallestAcknowledged();
        for (Long skippedPacketNumber : skippedPacketNumbers.subSet(smallestAcknowledged, true,
                                                                    largestAcknowledged, true)) {
            if (frame.isAcknowledging(skippedPacketNumber)) {
                throw new QuicTransportException("Acknowledgement for a skipped packet: " + skippedPacketNumber,
                                                 frame.typeField(), QuicTransportErrors.PROTOCOL_VIOLATION);
            }
        }
    }

    // records the time at which the last ACK-eliciting packet was sent.
    // This has the side effect of resetting the nextPingTime to Deadline.MAX
    // The logic is that a PING frame only need to be sent if no ACK-eliciting
    // packet has been sent for some time (and the AckFrame has grown big enough).
    // See RFC 9000 - Section 13.2.4
    private Deadline lastAckElicitingSent(Deadline now) {
        Deadline max;
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "Updating last send time to %s", now);
        }
        do {
            max = lastAckElicitingTime;
            if (max != null && !now.isAfter(max)) {
                return max;
            }
        } while (!Handles.LAST_ACK_ELICITING_TIME
                .compareAndSet(this, max, now));
        return now;
    }

    /**
     * returns the TLS encryption level of this packet space as specified
     * in RFC-9001, section 4, table 1.
     */
    private QuicTLSEngine.KeySpace tlsEncryptionLevel() {
        return switch (this.packetNumberSpace) {
            case INITIAL -> QuicTLSEngine.KeySpace.INITIAL;
            // 0-RTT keys are discarded explicitly by the TLS engine; closing the application
            // packet space only needs to tear down the long-lived 1-RTT keys.
            case APPLICATION -> QuicTLSEngine.KeySpace.ONE_RTT;
            case HANDSHAKE -> QuicTLSEngine.KeySpace.HANDSHAKE;
            default -> throw new IllegalStateException("No known TLS encryption level"
                                                               + " for packet space: " + this.packetNumberSpace.text());
        };
    }

    /**
     * A record that stores the next AckFrame that should be sent
     * within this packet number space.
     *
     * @param ackFrame    the ACK frame to send.
     * @param deadline    the deadline by which to send this ACK frame.
     * @param lastUpdated the time at which the {@link AckFrame}'s
     *                   {@link AckFrame#largestAcknowledged()} was
     *                   last updated. Used for calculating ack delay.
     * @param sent        the time at which the {@link AckFrame} was sent,
     *                   or {@code null} if it has not been sent yet.
     */
    record NextAckFrame(AckFrame ackFrame,
                        Deadline deadline,
                        Deadline lastUpdated,
                        Deadline sent) {
        /**
         * Returns an identical {@code NextAckFrame} record, with an updated
         * {@code deadline}.
         *
         * @return an identical {@code NextAckFrame} record, with an updated
         *         {@code deadline}
         *
         * @param deadline the new deadline
         * @param sent     the point in time at which the ack frame was sent, or null.
         */
        public NextAckFrame withDeadline(Deadline deadline, Deadline sent) {
            return new NextAckFrame(ackFrame, deadline, lastUpdated, sent);
        }
    }

    static final class PathRecoveryState {
        private final ReentrantLock lock = new ReentrantLock();
        private volatile long generation;

        PathRecoveryState(long generation) {
            this.generation = generation;
        }

        long generation() {
            return generation;
        }

        boolean runIfCurrent(long expectedGeneration, Runnable action) {
            lock.lock();
            try {
                if (generation != expectedGeneration) {
                    return false;
                }
                action.run();
                return true;
            } finally {
                lock.unlock();
            }
        }

        void runWithCurrent(LongConsumer action) {
            lock.lock();
            try {
                action.accept(generation);
            } finally {
                lock.unlock();
            }
        }

        void transition(long newGeneration, Runnable action) {
            lock.lock();
            try {
                if (newGeneration <= generation) {
                    return;
                }
                action.run();
                generation = newGeneration;
            } finally {
                lock.unlock();
            }
        }
    }

    static final class OneRttPacketSpaceManager extends PacketSpaceManager
            implements QuicOneRttContext {

        OneRttPacketSpaceManager(QuicConnectionImpl connection) {
            super(connection, PacketNumberSpace.APPLICATION);
        }
    }

    static final class HandshakePacketSpaceManager extends PacketSpaceManager {
        private final PacketSpaceManager initialPktSpaceMgr;
        private final boolean isClientConnection;
        private final AtomicBoolean firstPktSent = new AtomicBoolean();

        HandshakePacketSpaceManager(QuicConnectionImpl connection,
                                    PacketSpaceManager initialPktSpaceManager) {
            super(connection, PacketNumberSpace.HANDSHAKE);
            this.isClientConnection = connection.isClientConnection();
            this.initialPktSpaceMgr = initialPktSpaceManager;
        }

        @Override
        public void packetSent(QuicPacket packet, long previousPacketNumber, long packetNumber) {
            super.packetSent(packet, previousPacketNumber, packetNumber);
            afterPacketSent();
        }

        @Override
        public void packetSent(QuicPacket packet,
                               long previousPacketNumber,
                               long packetNumber,
                               long pathGeneration) {
            super.packetSent(packet, previousPacketNumber, packetNumber, pathGeneration);
            afterPacketSent();
        }

        private void afterPacketSent() {
            if (!isClientConnection) {
                // nothing additional to be done for server connections
                return;
            }
            if (firstPktSent.compareAndSet(false, true)) {
                // if this is the first packet we sent in the HANDSHAKE keyspace
                // then we close the INITIAL space discard the INITIAL keys.
                // RFC-9000, section 17.2.2.1:
                // A client stops both sending and processing Initial packets when it sends
                // its first Handshake packet. ... Though packets might still be in flight or
                // awaiting acknowledgment, no further Initial packets need to be exchanged
                // beyond this point. Initial packet protection keys are discarded along with
                // any loss recovery and congestion control state
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    initialPktSpaceMgr.log(System.Logger.Level.DEBUG,
                                           "first handshake packet sent by client, initiating close of"
                                                   + " INITIAL packet space");
                }
                this.initialPktSpaceMgr.close();
            }
        }
    }

    /**
     * A task that sends packets to the peer.
     *
     * Packets are sent after a delay when:
     * - ack delay timer expires
     * - PTO timer expires
     * They can also be sent without delay when:
     * - we are unblocked by the peer
     * - new data is available for sending, and we are not blocked
     * - need to send ack without delay
     */
    final class PacketTransmissionTask implements QuicTimedEvent {
        private final SequentialScheduler handleScheduler =
                SequentialScheduler.lockingScheduler(this::handleLoop);
        private final long id = QuicTimerQueue.newEventId();
        private final ReentrantLock logStateLock = new ReentrantLock();
        private volatile Deadline nextDeadline; // updated through VarHandle
        private boolean hadNoDeadline;

        private PacketTransmissionTask() {
            nextDeadline = Deadline.MAX;
        }

        @Override
        public long eventId() {
            return id;
        }

        @Override
        public Deadline deadline() {
            return nextDeadline;
        }

        @Override
        public Deadline handle() {
            if (closed) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "packet space already closed, PacketTransmissionTask will"
                            + " no longer be scheduled");
                }
                return Deadline.MAX;
            }
            handleScheduler.runOrSchedule(packetEmitter.executor());
            return Deadline.MAX;
        }

        @Override
        public Deadline refreshDeadline() {
            Deadline previousDeadline;
            Deadline newDeadline;
            do {
                previousDeadline = this.nextDeadline;
                newDeadline = computeNextDeadline();
            } while (!Handles.DEADLINE.compareAndSet(this, previousDeadline, newDeadline));

            if (!newDeadline.equals(previousDeadline)) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    var now = now();
                    if (newDeadline.equals(Deadline.MAX)) {
                        log(System.Logger.Level.DEBUG, "Deadline refreshed: no new deadline");
                    } else if (newDeadline.equals(Deadline.MIN)) {
                        log(System.Logger.Level.DEBUG, "Deadline refreshed: run immediately");
                    } else if (previousDeadline.equals(Deadline.MAX) || previousDeadline.equals(Deadline.MIN)) {
                        var delay = now.until(newDeadline, ChronoUnit.MILLIS);
                        if (delay < 0) {
                            log(System.Logger.Level.DEBUG, "Deadline refreshed: new deadline passed by %dms", delay);
                        } else {
                            log(System.Logger.Level.DEBUG, "Deadline refreshed: new deadline in %dms", delay);
                        }
                    } else {
                        var delay = now.until(newDeadline, ChronoUnit.MILLIS);
                        if (delay < 0) {
                            log(System.Logger.Level.DEBUG, "Deadline refreshed: new deadline passed by %dms (diff: %dms)",
                                      delay, previousDeadline.until(newDeadline, ChronoUnit.MILLIS));
                        } else {
                            log(System.Logger.Level.DEBUG, "Deadline refreshed: new deadline in %dms (diff: %dms)",
                                      instantSource.instant().until(newDeadline, ChronoUnit.MILLIS),
                                      previousDeadline.until(newDeadline, ChronoUnit.MILLIS));
                        }
                    }
                }
            } else {
                log(System.Logger.Level.DEBUG, "Deadline not refreshed: no change");
            }
            logNoDeadline(newDeadline, false);
            return newDeadline;
        }

        @Override
        public String toString() {
            return "PacketTransmissionTask(" + debugDescriptionSupplier.get() + ")";
        }

        void logNoDeadline(Deadline newDeadline, boolean onlyNoDeadline) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                if (Deadline.MAX.equals(newDeadline)) {
                    if (shouldLogWhenNoDeadline()) {
                        log(System.Logger.Level.DEBUG, "no deadline, task unscheduled");
                    } // else: no changes...
                } else if (!onlyNoDeadline && shouldLogWhenNewDeadline()) {
                    if (Deadline.MIN.equals(newDeadline)) {
                        log(System.Logger.Level.DEBUG, "Deadline.MIN, task will be rescheduled immediately");
                    } else {
                        try {
                            log(System.Logger.Level.DEBUG, "new deadline computed, deadline in %sms",
                                      now().until(newDeadline, ChronoUnit.MILLIS));
                        } catch (ArithmeticException ae) {
                            log(System.Logger.Level.ERROR,
                                      "Unexpected exception while logging deadline " + newDeadline,
                                      ae);
                        }
                    }
                }
            }
        }

        // reschedule this task
        void reschedule() {
            Deadline deadline = computeNextDeadline();
            if (Deadline.MAX.equals(deadline)) {
                log(System.Logger.Level.DEBUG, "no deadline, don't reschedule");
                return;
            }
            packetEmitter.reschedule(this, deadline);
            log(System.Logger.Level.DEBUG, "retransmission task: rescheduled");
        }

        /**
         * The handle loop takes care of sending ACKs, packaging stream data
         * (if applicable), and retransmitting on PTO. It is never invoked
         * directly - but can be triggered by {@link #handle()} or {@link
         * #runTransmitter()}
         */
        private void handleLoop() {
            transmitLock.lock();
            try {
                handleLoop0();
            } catch (Throwable t) {
                log(System.Logger.Level.ERROR, "handleLoop failed", t);
            } finally {
                transmitLock.unlock();
            }
        }

        private void handleLoop0() throws QuicTransportException {
            // while congestion control allows, or if PTO expired:
            // - send lost packet or new packet
            // if PTO still expired (== nothing was sent)
            // - resend oldest packet, if available
            // - otherwise send ping (+ack, if available)
            // if ACK still not sent, send ack
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "PacketTransmissionTask::handle");
            }
            packetEmitter.checkAbort(PacketSpaceManager.this.packetNumberSpace);
            // Handle is called from within the executor
            Deadline newDeadline;
            Deadline now = now();
            do {
                congestionController.updatePacer(now);
                transmitNow = false;
                var closed = !isOpenForTransmission();
                if (closed) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "PacketTransmissionTask::handle: closed");
                    }
                    return;
                }
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "PacketTransmissionTask::handle");
                }
                // this may update congestion controller
                Deadline lossDetectionTime = now;
                int lost;
                transferLock.lock();
                try {
                    pathRecoveryState.runWithCurrent(generation ->
                            ackProcessingState.lostCount = detectAndAccountLostPackets(lossDetectionTime, generation));
                    lost = ackProcessingState.lostCount;
                } finally {
                    transferLock.unlock();
                }
                if (lost > 0 && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "handle: found %s lost packets", lost);
                }
                // if we're sending on PTO, we need to double backoff afterwards
                boolean needBackoff = isPTO(now);
                if (!transmitAvailablePackets(needBackoff)) {
                    return;
                }
                try {
                    if (isPTO(now) && isOpenForTransmission()) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            log(System.Logger.Level.DEBUG, "handle: retransmit on PTO");
                        }
                        // nothing was sent by the above loop - try to resend the oldest packet
                        retransmitPTO();
                    } else if (fastRetransmit) {
                        fastRetransmitDone = true;
                        fastRetransmit = false;
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            log(System.Logger.Level.DEBUG, "handle: fast retransmit");
                        }
                        // try to resend the oldest packet
                        retransmitPTO();
                    }
                } catch (QuicKeyUnavailableException qkue) {
                    if (!isOpenForTransmission()) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            log(System.Logger.Level.DEBUG, "already closed; not re-transmitting any more data");
                        }
                        return;
                    }
                    throw new IllegalStateException("Failed to retransmit PTO data", qkue);
                }
                boolean stillPTO = isPTO(now);
                // if the ack frame is not sent yet, send it now
                var ackFrame = nextAckFrame(!stillPTO).orElse(null);
                var pingRequested = PacketSpaceManager.this.pingRequested;
                boolean sendPing = pingRequested != null || stillPTO
                        || shouldSendPing(now, ackFrame);
                if (sendPing || ackFrame != null) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "handle: generate ACK packet or PING ack:%s ping:%s",
                                  ackFrame != null, sendPing);
                    }
                    long emitted;
                    try {
                        emitted = emitAckPacket(ackFrame, sendPing);
                    } catch (QuicKeyUnavailableException qkue) {
                        if (!isOpenForTransmission()) {
                            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                                log(System.Logger.Level.DEBUG, "already closed; not sending ack/ping packet");
                            }
                            return;
                        }
                        throw new IllegalStateException("Failed to send ACK/PING data", qkue);
                    }
                    if (sendPing && pingRequested != null) {
                        if (emitted < 0) {
                            pingRequested.complete(-1L);
                        } else {
                            registerPingRequest(new PingRequest(now, emitted, pingRequested));
                        }
                        PacketSpaceManager.this.stateLock.lock();
                        try {
                            PacketSpaceManager.this.pingRequested = null;
                        } finally {
                            PacketSpaceManager.this.stateLock.unlock();
                        }
                    }
                }
                if (needBackoff) {
                    long generation = pathRecoveryState.generation();
                    long[] updatedBackoff = {rttEstimator.ptoBackoff()};
                    pathRecoveryState.runIfCurrent(generation,
                                                   () -> updatedBackoff[0] = rttEstimator.increasePtoBackoff());
                    long backoff = updatedBackoff[0];
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "handle: increase backoff to %s", backoff);
                    }
                    packetEmitter.ptoBackoffIncreased(PacketSpaceManager.this, backoff);
                }

                // if newDeadline is not Deadline.MAX the task will be
                // automatically rescheduled.
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "handle: refreshing deadline");
                }
                newDeadline = computeNextDeadline();
                now = now();
            } while (!newDeadline.isAfter(now));

            logNoDeadline(newDeadline, true);
            if (Deadline.MAX.equals(newDeadline)) {
                return;
            }
            // we have a new deadline
            packetEmitter.reschedule(this, newDeadline);
        }

        private boolean transmitAvailablePackets(boolean needBackoff) throws QuicTransportException {
            int packetsSent = 0;
            boolean cwndAvailable;
            long startTime = System.nanoTime();
            while (true) {
                cwndAvailable = congestionController.canSendPacket();
                if (!cwndAvailable && !(needBackoff && packetsSent < 2)) {
                    break;
                }
                if (!isOpenForTransmission()) {
                    break;
                }
                boolean retransmitted = false;
                boolean sentNew = false;
                boolean retransmissionTried = false;
                try {
                    sentNew = packetEmitter.sendPriorityData(packetNumberSpace);
                    if (!sentNew) {
                        retransmissionTried = true;
                        retransmitted = retransmit();
                    }
                    if (!sentNew && !retransmitted) {
                        sentNew = sendNewData();
                    }
                } catch (QuicKeyUnavailableException qkue) {
                    if (!isOpenForTransmission()) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            log(System.Logger.Level.DEBUG, "already closed; not transmitting any more data");
                        }
                        if (retransmissionTried) {
                            clearAll();
                        }
                        return false;
                    }
                    throw new IllegalStateException("Failed to transmit data", qkue);
                }
                if (retransmitted) {
                    packetsSent++;
                    continue;
                }
                if (!sentNew) {
                    congestionController.appLimited();
                    break;
                } else {
                    logDebugIf(needBackoff && packetsSent == 0,
                               "OUT: transmitted new packet on PTO");
                }
                packetsSent++;
            }
            logDebugIf(packetsSent != 0,
                       "OUT: sent: %s packets in %s ns, cwnd limited: %s, pacer limited: %s",
                       packetsSent, System.nanoTime() - startTime,
                       congestionController.isCwndLimited(), congestionController.isPacerLimited());
            blockedByCC = !cwndAvailable && congestionController.isCwndLimited();
            blockedByPacer = !cwndAvailable && congestionController.isPacerLimited();
            if (!cwndAvailable && isOpenForTransmission()) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "handle: blocked by CC");
                }
                // CC might be available already
                if (congestionController.canSendPacket()) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "handle: unblocked immediately");
                    }
                    blockedByCC = false;
                    blockedByPacer = false;
                    transmitNow = true;
                }
            }
            return true;
        }

        /**
         * Create and send a new packet.
         *
         * @return true if packet was sent, false if there is no more data to send
         */
        private boolean sendNewData() throws QuicKeyUnavailableException, QuicTransportException {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "handle: sending data...");
            }
            boolean sent = packetEmitter.sendData(packetNumberSpace);
            if (!sent) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "handle: no more data to send");
                }
            }
            return sent;
        }

        private boolean shouldLogWhenNoDeadline() {
            logStateLock.lock();
            try {
                if (!hadNoDeadline) {
                    hadNoDeadline = true;
                    return true;
                }
                return false;
            } finally {
                logStateLock.unlock();
            }
        }

        private boolean shouldLogWhenNewDeadline() {
            logStateLock.lock();
            try {
                if (hadNoDeadline) {
                    hadNoDeadline = false;
                    return true;
                }
                return false;
            } finally {
                logStateLock.unlock();
            }
        }
    }

    /**
     * A record to store previous numbers with which a packet has been
     * retransmitted. If such a packet is acknowledged, we can stop
     * retransmission.
     *
     * @param number              A packet number with which the content of this
     *                           packet was previously sent.
     * @param sent                The instant when the previous packet was sent.
     * @param largestAcknowledged the largest packet number acknowledged by this
     *                           previous packet, or {@code -1L} if no packet was
     *                           acknowledged by this packet.
     * @param pathGeneration      The path generation on which the previous packet was sent.
     * @param previous            Further previous packet numbers, or {@code null}.
     */
    private record PreviousNumbers(long number,
                                   Deadline sent,
                                   long largestAcknowledged,
                                   long pathGeneration,
                                   PreviousNumbers previous) { }

    /**
     * A record used to implement {@link #requestSendPing()}.
     *
     * @param sent         when the ping frame was sent
     * @param packetNumber the packet number of the packet containing the pingframe
     * @param response     the response, which will be complete as soon as a packet whose number is
     *                    >= to {@code packetNumber} is received.
     */
    private record PingRequest(Deadline sent, long packetNumber, CompletableFuture<Long> response) { }

    private record PathControlFlight(QuicPacket packet,
                                     Deadline sent,
                                     long packetNumber,
                                     long pathGeneration,
                                     long accountingGeneration) {
    }

    /**
     * A record to store a packet that hasn't been acknowledged, and should
     * be scheduled for retransmission if not acknowledged when the deadline
     * is reached.
     *
     * @param packet              the unacknowledged quic packet
     * @param sent                the instant when the packet was sent.
     * @param packetNumber        the packet number of the {@code packet}
     * @param largestAcknowledged the largest packet number acknowledged by this
     *                           packet, or {@code -1L} if no packet is acknowledged
     *                           by this packet.
     * @param previousNumbers     previous packet numbers with which the packet was
     *                           transmitted, if any, {@code null} otherwise.
     */
    private static final class PendingAcknowledgement {
        private final QuicPacket packet;
        private final Deadline sent;
        private final long packetNumber;
        private final long largestAcknowledged;
        private final PreviousNumbers previousNumbers;
        private final long pathGeneration;
        private long acknowledgementScanEpoch;
        private boolean acknowledgedInScan;
        private PendingAcknowledgement acknowledgedNext;

        PendingAcknowledgement(QuicPacket packet, Deadline sent,
                               long packetNumber, PreviousNumbers previousNumbers,
                               long pathGeneration) {
            this.packet = packet;
            this.sent = sent;
            this.packetNumber = packetNumber;
            this.largestAcknowledged = AckFrame.largestAcknowledgedInPacket(packet);
            this.previousNumbers = previousNumbers;
            this.pathGeneration = pathGeneration;
        }

        QuicPacket packet() {
            return packet;
        }

        Deadline sent() {
            return sent;
        }

        long packetNumber() {
            return packetNumber;
        }

        long largestAcknowledged() {
            return largestAcknowledged;
        }

        PreviousNumbers previousNumbers() {
            return previousNumbers;
        }

        long pathGeneration() {
            return pathGeneration;
        }

        public int attempts() {
            var pn = previousNumbers;
            int count = 0;
            while (pn != null) {
                count++;
                pn = pn.previous;
            }
            return count;
        }

        boolean hasPreviousNumber(long packetNumber) {
            if (this.packetNumber <= packetNumber) {
                return false;
            }
            var pn = previousNumbers;
            while (pn != null) {
                if (pn.number == packetNumber) {
                    return true;
                }
                pn = pn.previous;
            }
            return false;
        }

        boolean hasExactNumber(long packetNumber) {
            return this.packetNumber == packetNumber;
        }

        String prettyPrint() {
            StringBuilder b = new StringBuilder();
            b.append("pn:").append(packetNumber);
            var ppn = previousNumbers;
            if (ppn != null) {
                var sep = " [";
                while (ppn != null) {
                    b.append(sep).append(ppn.number);
                    ppn = ppn.previous;
                    sep = ", ";
                }
                b.append("]");
            }
            return b.toString();
        }

        @Override
        public String toString() {
            return prettyPrint();
        }
    }

    private static final class AcknowledgementScan {
        private AckFrame frame;
        private long epoch;
        private long trackedLargestAcknowledged;
        private long newestAcknowledgedPathGeneration;
        private Deadline rttSent;
        private long rttPathGeneration;

        void begin(AckFrame frame, long epoch) {
            this.frame = frame;
            this.epoch = epoch;
            trackedLargestAcknowledged = -1;
            newestAcknowledgedPathGeneration = -1;
            rttSent = null;
            rttPathGeneration = -1;
        }

        long epoch() {
            return epoch;
        }

        boolean scan(PendingAcknowledgement pending) {
            if (pending.acknowledgementScanEpoch == epoch) {
                return pending.acknowledgedInScan;
            }
            trackedLargestAcknowledged = -1;
            boolean acknowledged = frame.isAcknowledging(pending.packetNumber);
            if (acknowledged) {
                trackedLargestAcknowledged = pending.largestAcknowledged;
                recordAcknowledgedTransmission(pending.packetNumber, pending.sent, pending.pathGeneration);
            } else {
                PreviousNumbers previous = pending.previousNumbers;
                while (previous != null) {
                    if (frame.isAcknowledging(previous.number)) {
                        if (!acknowledged) {
                            trackedLargestAcknowledged = previous.largestAcknowledged;
                        }
                        acknowledged = true;
                        recordAcknowledgedTransmission(previous.number, previous.sent, previous.pathGeneration);
                    }
                    previous = previous.previous;
                }
            }
            pending.acknowledgementScanEpoch = epoch;
            pending.acknowledgedInScan = acknowledged;
            return acknowledged;
        }

        private void recordAcknowledgedTransmission(long packetNumber, Deadline sent, long pathGeneration) {
            newestAcknowledgedPathGeneration = Math.max(newestAcknowledgedPathGeneration, pathGeneration);
            if (packetNumber == frame.largestAcknowledged()) {
                rttSent = sent;
                rttPathGeneration = pathGeneration;
            }
        }

        long trackedLargestAcknowledged() {
            return trackedLargestAcknowledged;
        }

        long newestAcknowledgedPathGeneration() {
            return newestAcknowledgedPathGeneration;
        }

        Deadline rttSent() {
            return rttSent;
        }

        long rttPathGeneration() {
            return rttPathGeneration;
        }
    }

    private static final class AckProcessingState {
        private final List<QuicPacket> lostPackets = new ArrayList<>();
        private final List<QuicPacket> completedPathControls = new ArrayList<>();
        private PendingAcknowledgement acknowledged;
        private int lostCount;
        private boolean pathControlCapacityReleased;

        void reset() {
            clearAcknowledged();
            lostPackets.clear();
            completedPathControls.clear();
            lostCount = 0;
            pathControlCapacityReleased = false;
        }

        void addAcknowledged(PendingAcknowledgement pending) {
            pending.acknowledgedNext = acknowledged;
            acknowledged = pending;
        }

        void clearAcknowledged() {
            while (acknowledged != null) {
                PendingAcknowledgement next = acknowledged.acknowledgedNext;
                acknowledged.acknowledgedNext = null;
                acknowledged = next;
            }
        }
    }

    // VarHandles provide the same atomic compareAndSet functionality
    // as atomic classes, but without the additional cost in
    // footprint.
    private static final class Handles {
        static final VarHandle DEADLINE;
        static final VarHandle NEXTACK;
        static final VarHandle LARGEST_PROCESSED_PN;
        static final VarHandle LARGEST_ACK_ELICITING_RECEIVED_PN;
        static final VarHandle LARGEST_RECEIVED_ACKED_PN;
        static final VarHandle LARGEST_SENT_ACKED_PN;
        static final VarHandle LARGEST_ACK_ACKED_PN;
        static final VarHandle LAST_ACK_ELICITING_TIME;
        static final VarHandle IGNORE_ALL_PN_BEFORE;

        static {
            Lookup lookup = MethodHandles.lookup();
            try {
                Class<?> srt = PacketTransmissionTask.class;
                DEADLINE = lookup.findVarHandle(srt, "nextDeadline", Deadline.class);

                Class<?> pmc = PacketSpaceManager.class;
                LAST_ACK_ELICITING_TIME = lookup.findVarHandle(pmc,
                                                               "lastAckElicitingTime", Deadline.class);
                NEXTACK = lookup.findVarHandle(pmc, "nextAckFrame", NextAckFrame.class);
                LARGEST_RECEIVED_ACKED_PN = lookup
                        .findVarHandle(pmc, "largestReceivedAckedPN", long.class);
                LARGEST_SENT_ACKED_PN = lookup
                        .findVarHandle(pmc, "largestSentAckedPN", long.class);
                LARGEST_PROCESSED_PN = lookup
                        .findVarHandle(pmc, "largestProcessedPN", long.class);
                LARGEST_ACK_ELICITING_RECEIVED_PN = lookup
                        .findVarHandle(pmc, "largestAckElicitingReceivedPN", long.class);
                LARGEST_ACK_ACKED_PN = lookup
                        .findVarHandle(pmc, "largestAckedPNReceivedByPeer", long.class);

                Class<?> eat = EmittedAckTracker.class;
                IGNORE_ALL_PN_BEFORE = lookup
                        .findVarHandle(eat, "ignoreAllPacketsBefore", long.class);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);

            }
        }

        private Handles() {
            throw new InternalError();
        }
    }

    /**
     * A class to keep track of the largest packet that was acknowledged by
     * a packet that is being acknowledged.
     * This information is used to implement the algorithm described in
     * RFC 9000 13.2.4. Limiting Ranges by Tracking ACK Frames
     */
    private final class EmittedAckTracker {
        private volatile long ignoreAllPacketsBefore = -1;

        /**
         * Tracks the largest packet acknowledged by the packets acknowledged in the
         * given AckFrame. This helps to implement the algorithm described in
         * RFC 9000,  13.2.4. Limiting Ranges by Tracking ACK Frames.
         *
         * @param pending             the acknowledged packet
         * @param largestAcknowledged largest packet acknowledged by the matching transmission
         */
        void trackAcknowledgement(PendingAcknowledgement pending, long largestAcknowledged) {
            record(largestAcknowledged);
            packetEmitter.acknowledged(pending.packet());
        }

        public long largestAckAcked() {
            return largestAckedPNReceivedByPeer;
        }

        public void dropPacketNumbersSmallerThan(long newLargestIgnored) {
            // this method is called after arbitrarily reducing the ack range
            // to this value; This mean we will drop packets whose packet
            // number is smaller than the given packet number.
            if (ignoreAllPacketsBefore(newLargestIgnored)) {
                record(newLargestIgnored);
            }
        }

        /**
         * Record the {@link AckFrame#largestAcknowledged()
         * largest acknowledged} packet that was sent in an
         * {@link AckFrame} that the peer has acknowledged.
         *
         * @param largestAcknowledged the packet number to record
         * @return the largest {@code largestAcknowledged}
         *        packet number that was recorded.
         *        This is necessarily smaller than (or equal to) the
         *        {@link #largestSentAcknowledgedPacketNumber()}.
         */
        private long record(long largestAcknowledged) {
            long witness;
            do {
                witness = largestAckedPNReceivedByPeer;
                if (witness >= largestAcknowledged) {
                    return witness;
                }
            } while (!Handles.LARGEST_ACK_ACKED_PN.compareAndSet(
                    PacketSpaceManager.this, witness, largestAcknowledged));
            return largestAcknowledged;
        }

        private boolean ignoreAllPacketsBefore(long packetNumber) {
            long ignoreAllPacketsBefore;
            do {
                ignoreAllPacketsBefore = this.ignoreAllPacketsBefore;
                if (packetNumber <= ignoreAllPacketsBefore) {
                    return false;
                }
            } while (!Handles.IGNORE_ALL_PN_BEFORE.compareAndSet(
                    this, ignoreAllPacketsBefore, packetNumber));
            return true;
        }
    }
}
