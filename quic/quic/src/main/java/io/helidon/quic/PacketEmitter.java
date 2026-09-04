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

import java.util.concurrent.Executor;

import io.helidon.common.Api;
import io.helidon.quic.frame.AckFrame;
import io.helidon.quic.packet.PacketSpace;
import io.helidon.quic.packet.QuicPacket;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;

/**
 * This interface is a useful abstraction used to tie
 * {@link PacketSpaceManager} and {@link QuicConnectionImpl}.
 * The {@link PacketSpaceManager} uses functionalities provided
 * by a {@link PacketEmitter} when it deems that a packet needs
 * to be retransmitted, or that an acknowledgement is due.
 * It also uses the emitter's {@linkplain #timer() timer facility}
 * when it needs to register a {@link QuicTimedEvent}.
 * <p>
 * These operations are implemented by {@link QuicConnectionImpl}; the interface
 * keeps packet recovery and acknowledgement scheduling separate from the connection's
 * transport implementation.
 *
 */
@Api.Internal
public interface PacketEmitter {
    /**
     * Returns the timer queue used by this packet emitter.
     *
     * @return the timer queue used by this packet emitter
     */
    QuicTimerQueue timer();

    /**
     * Retransmit the given packet on behalf of the given packet space
     * manager.
     *
     * @param packetSpaceManager the packet space manager on behalf of
     *                          which the packet is being retransmitted
     * @param packet             the unacknowledged packet which should be retransmitted
     * @param attempts           the number of previous retransmission of this packet.
     *                          A value of 0 indicates the first retransmission.
     * @return whether the packet was retransmitted; {@code false} when path limits blocked emission
     * @throws QuicKeyUnavailableException if the required packet-protection keys are unavailable
     * @throws QuicTransportException      if packet emission fails
     */
    boolean retransmit(PacketSpace packetSpaceManager, QuicPacket packet, int attempts)
            throws QuicKeyUnavailableException, QuicTransportException;

    /**
     * Emit a possibly non ACK-eliciting packet containing the given ACK frame.
     *
     * @param packetSpaceManager the packet space manager on behalf
     *                          of which the acknowledgement should
     *                          be sent.
     * @param ackFrame           the ACK frame to be sent.
     * @param sendPing           whether a PING frame should be sent.
     * @return the emitted packet number, or -1L if not applicable or not emitted
     * @throws QuicKeyUnavailableException if the required packet-protection keys are unavailable
     * @throws QuicTransportException      if packet emission fails
     */
    long emitAckPacket(PacketSpace packetSpaceManager, AckFrame ackFrame, boolean sendPing)
            throws QuicKeyUnavailableException, QuicTransportException;

    /**
     * Called when a packet has been acknowledged.
     *
     * @param packet the acknowledged packet
     */
    void acknowledged(QuicPacket packet);

    /**
     * Called when congestion controller allows sending one packet.
     *
     * @param packetNumberSpace current packet number space
     * @return true if a packet was sent, false otherwise
     * @throws QuicKeyUnavailableException if the required packet-protection keys are unavailable
     * @throws QuicTransportException      if packet emission fails
     */
    boolean sendData(PacketNumberSpace packetNumberSpace)
            throws QuicKeyUnavailableException, QuicTransportException;

    /**
     * Attempts to emit priority data before ordinary retransmissions.
     *
     * @param packetNumberSpace packet number space
     * @return whether priority data was emitted
     * @throws QuicKeyUnavailableException if the required packet-protection keys are unavailable
     * @throws QuicTransportException      if packet emission fails
     */
    default boolean sendPriorityData(PacketNumberSpace packetNumberSpace)
            throws QuicKeyUnavailableException, QuicTransportException {
        return false;
    }

    /**
     * Returns an executor to use when {@linkplain
     * io.helidon.quic.SequentialScheduler#runOrSchedule(Executor)
     * offloading loops to another thread} is required.
     *
     * @return an executor to use when offloading loops to another thread is required
     */
    Executor executor();

    /**
     * Reschedule the given event on the {@link #timer() timer}.
     *
     * @param event the event to reschedule
     */
    default void reschedule(QuicTimedEvent event) {
        timer().reschedule(event);
    }

    /**
     * Reschedule the given event on the {@link #timer() timer}.
     *
     * @param event    the event to reschedule
     * @param deadline new deadline for the event
     */
    default void reschedule(QuicTimedEvent event, Deadline deadline) {
        timer().reschedule(event, deadline);
    }

    /**
     * Abort the connection if needed, for example if the peer is not responding
     * or max idle time was reached.
     *
     * @param packetNumberSpace packet number space being checked
     */
    void checkAbort(PacketNumberSpace packetNumberSpace);

    /**
     * Checks whether this emitter is open for transmitting packets.
     *
     * @return true if this emitter is open for transmitting packets, false otherwise
     */
    boolean isOpen();

    /**
     * Notify the emitter that the PTO backoff factor increased.
     *
     * @param space   packet space whose PTO backoff changed
     * @param backoff new backoff factor
     */
    default void ptoBackoffIncreased(PacketSpaceManager space, long backoff) {
    }

    /**
     * Returns the diagnostic tag used in logs for packets emitted by this component.
     *
     * @return diagnostic tag used in logs for packets emitted by this component
     */
    default String logTag() {
        return toString();
    }
}
