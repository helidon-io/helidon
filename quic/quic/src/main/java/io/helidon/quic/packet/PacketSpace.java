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

package io.helidon.quic.packet;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.Api;
import io.helidon.quic.PacketSpaceManager;
import io.helidon.quic.QuicConnectionImpl;
import io.helidon.quic.QuicTransportException;
import io.helidon.quic.frame.AckFrame;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;
import io.helidon.quic.packet.QuicPacket.PacketType;

/**
 * An interface implemented by classes which keep track of packet
 * numbers for a given packet number space.
 */
@Api.Internal
public sealed interface PacketSpace permits PacketSpaceManager {

    /**
     * Called on application packet space to record peer's transport parameters.
     *
     * @param peerDelay        max_ack_delay
     * @param ackDelayExponent ack_delay_exponent
     */
    void updatePeerTransportParameters(long peerDelay, long ackDelayExponent);

    /**
     * Returns the packet number space managed by this class.
     *
     * @return the packet number space managed by this class
     */
    PacketNumberSpace packetNumberSpace();

    /**
     * The largest processed PN is used to compute
     * the packet number of an incoming Quic packet.
     *
     * @return the largest incoming packet number that
     *        was successfully processed in this space.
     */
    long largestProcessedPacketNumber();

    /**
     * The largest received acked PN is used to compute the
     * packet number that we include in an outgoing Quic packet.
     *
     * @return the largest packet number that was acknowledged by
     *        the peer in this space.
     */
    long largestPeerAcknowledgedPacketNumber();

    /**
     * Returns the largest packet number that we have acknowledged in this
     * space.
     *
     * @return the largest packet number that we have acknowledged in this space
     * <p>Note: This is necessarily greater or equal to the packet number
     *        returned by {@linkplain #minimumPacketNumberThreshold()}.
     */
    long largestSentAcknowledgedPacketNumber();

    /**
     * Returns the packet number threshold below which packets should be
     * discarded without being processed in this space.
     *
     * @return the packet number threshold below which packets should be discarded without being processed in this space
     * <p>Note: This corresponds to the largest acknowledged packet number
     *        carried in an outgoing ACK frame whose packet number has
     *        been acknowledged by the peer. In other words, the largest
     *        packet number sent by the peer for which we know that the
     *        peer has received an acknowledgement.
     *        <p>
     *        Note that we need to track the ACK of outgoing packets that
     *        contain ACK frames in order to figure out whether a peer
     *        knows that a particular packet number has been received and
     *        avoid retransmission. However - we don't want ACK frames to grow
     *        too big and therefore we can drop some of the information,
     *        based on the largestSentAckedPN - see RFC 9000 Section 13.2
     */
    long minimumPacketNumberThreshold();

    /**
     * Returns a new packet number atomically allocated in this space.
     *
     * @return a new packet number atomically allocated in this space
     */
    long allocateNextPN();

    /**
     * This method is called by {@link QuicConnectionImpl} upon reception of
     * and successful negotiation of a new version.
     * In that case we should stop retransmitting packet that have the
     * "wrong" version: they will never be acknowledged.
     */
    void versionChanged();

    /**
     * This method is called by {@link QuicConnectionImpl} upon reception of
     * and successful processing of retry packet.
     * In that case we should treat all previously sent packets as lost.
     */
    void retry();

    /**
     * Returns a lock used by the transmission task.
     *
     * Used to ensure that the transmission task does not observe partial changes
     * during processing of incoming Versions and Retry packets.
     *
     * @return a lock used by the transmission task
     */
    ReentrantLock transmitLock();

    /**
     * Called when a packet is received. Causes the next ack frame to be
     * updated. If a packet contains an {@link AckFrame}, the caller is
     * expected to also later call {@link #processAckFrame(AckFrame)}
     * when processing the packet payload.
     *
     * @param packet         the received packet
     * @param packetNumber   the received packet number
     * @param isAckEliciting whether this packet is ack eliciting
     */
    void packetReceived(PacketType packet, long packetNumber, boolean isAckEliciting);

    /**
     * Signals that a packet has been sent.
     * This method is called by {@link QuicConnectionImpl} when a packet has been
     * pushed to the endpoint for sending.
     * <p> The retransmitted packet is taken out the pendingRetransmission list and
     * the new packet is inserted in the pendingAcknowledgement list.
     *
     * @param packet               the new packet being retransmitted
     * @param previousPacketNumber the packet number of the previous packet that was not acknowledged,
     *                            or -1 if this is not a retransmission
     * @param packetNumber         the new packet number under which this packet is being retransmitted
     * @throws IllegalArgumentException If {@code newPacketNumber} is lesser than 0
     */
    void packetSent(QuicPacket packet, long previousPacketNumber, long packetNumber);

    /**
     * Signals that a packet has been sent on a particular network-path generation.
     *
     * @param packet               sent packet
     * @param previousPacketNumber previous packet number, or {@code -1} for a new packet
     * @param packetNumber         sent packet number
     * @param pathGeneration       generation of the network path used for transmission
     */
    default void packetSent(QuicPacket packet,
                            long previousPacketNumber,
                            long packetNumber,
                            long pathGeneration) {
        packetSent(packet, previousPacketNumber, packetNumber);
    }

    /**
     * Processes a received ACK frame.
     * This method is called by {@link QuicConnectionImpl}.
     *
     * @param frame the ACK frame received.
     * @throws QuicTransportException if the ACK frame is invalid for this packet space
     */
    void processAckFrame(AckFrame frame) throws QuicTransportException;

    /**
     * Signals that the peer confirmed the handshake. Application space only.
     */
    void confirmHandshake();

    /**
     * Get the next ack frame to send.
     * This method returns the prepared ack frame if:
     * - it was not sent yet
     * - there are new ack-eliciting packets to acknowledge
     * - optionally, if the ack frame is overdue
     *
     * @param onlyOverdue if true, the frame will only be returned if it's overdue
     * @return the next ACK frame to send to the peer, or an empty optional if there is nothing to acknowledge
     */
    Optional<AckFrame> nextAckFrame(boolean onlyOverdue);

    /**
     * Get the next ack frame to send.
     * This method returns the prepared ack frame if:
     * - it was not sent yet
     * - there are new ack-eliciting packets to acknowledge
     * - the ack frame size doesn't exceed {@code maxSize}
     * - optionally, if the ack frame is overdue
     *
     * @param onlyOverdue if true, the frame will only be returned if it's overdue
     * @param maxSize     maximum encoded ACK frame size to return
     * @return the next ACK frame to send to the peer, or an empty optional if there is nothing to acknowledge
     */
    Optional<AckFrame> nextAckFrame(boolean onlyOverdue, int maxSize);

    /**
     * Used to request sending of a ping frame, for instance, to verify that
     * the connection is alive.
     *
     * @return a completable future that will be completed with the time it
     *        took, in milliseconds, for the peer to acknowledge the packet that
     *        contained the PingFrame (or any packet that was sent after)
     * <p>Note: The returned completable future is actually completed
     *        if any packet whose packet number is greater than the packet number
     *        that contained the ping frame is acknowledged.
     */
    CompletableFuture<Long> requestSendPing();

    /**
     * Stops retransmission for this packet space.
     */
    void close();

    /**
     * Whether this packet space is closed.
     *
     * @return true if this packet space is closed
     */
    boolean isClosed();

    /**
     * Triggers immediate run of transmit loop.
     *
     * This method is called by {@link QuicConnectionImpl} when new data may be
     * available for sending, for example:
     * - new stream data is available
     * - new receive credit is available
     * - stream is forcibly closed
     */
    void runTransmitter();

    /**
     * Whether a packet with that packet number is already being acknowledged
     * (will be, or has been acknowledged).
     *
     * @param packetNumber the packet number
     * @return true if the packet is already being acknowledged
     */
    boolean isAcknowledged(long packetNumber);

    /**
     * Immediately retransmit one unacknowledged initial packet.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9002#name-speeding-up-handshake-compl">
     *        RFC 9002 6.2.3. Speeding up Handshake Completion</a>
     */
    void fastRetransmit();
}
