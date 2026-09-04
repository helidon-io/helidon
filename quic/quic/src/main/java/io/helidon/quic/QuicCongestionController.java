/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.util.Collection;

import io.helidon.common.Api;
import io.helidon.quic.packet.QuicPacket;

/**
 * Congestion-control strategy used by a QUIC connection.
 */
@Api.Internal
public interface QuicCongestionController {

    /**
     * Checks whether a new non-ACK packet can be sent at this time.
     *
     * @return true if a new non-ACK packet can be sent at this time, false otherwise
     */
    boolean canSendPacket();

    /**
     * Update the maximum datagram size.
     *
     * @param newSize new maximum datagram size.
     */
    void updateMaxDatagramSize(int newSize);

    /**
     * Resets path-specific congestion state after a network-path change.
     *
     * @param maxDatagramSize maximum datagram size on the new path
     */
    void resetForPath(int maxDatagramSize);

    /**
     * Update CC with a non-ACK packet.
     *
     * @param packetBytes packet size in bytes
     */
    void packetSent(int packetBytes);

    /**
     * Update CC after a non-ACK packet is acked.
     *
     * @param packetBytes acked packet size in bytes
     * @param sentTime    time when packet was sent
     */
    void packetAcked(int packetBytes, Deadline sentTime);

    /**
     * Update CC after packets are declared lost.
     *
     * @param lostPackets collection of lost packets
     * @param sentTime    time when the most recent lost packet was sent
     * @param persistent  true if persistent congestion detected, false otherwise
     */
    void packetLost(Collection<QuicPacket> lostPackets, Deadline sentTime, boolean persistent);

    /**
     * Update CC after packets are discarded.
     *
     * @param discardedPackets collection of discarded packets
     */
    void packetDiscarded(Collection<QuicPacket> discardedPackets);

    /**
     * Returns the current size of the congestion window in bytes.
     *
     * @return the current size of the congestion window in bytes
     */
    long congestionWindow();

    /**
     * Returns the initial window size in bytes.
     *
     * @return the initial window size in bytes
     */
    long initialWindow();

    /**
     * Returns the maximum datagram size.
     *
     * @return maximum datagram size
     */
    long maxDatagramSize();

    /**
     * Checks whether the connection is in slow start phase.
     *
     * @return true if the connection is in slow start phase, false otherwise
     */
    boolean isSlowStart();

    /**
     * Update the pacer with the current time.
     *
     * @param now the current time
     */
    void updatePacer(Deadline now);

    /**
     * Checks whether sending is blocked by pacer.
     *
     * @return true if sending is blocked by pacer, false otherwise
     */
    boolean isPacerLimited();

    /**
     * Checks whether sending is blocked by congestion window.
     *
     * @return true if sending is blocked by congestion window, false otherwise
     */
    boolean isCwndLimited();

    /**
     * Returns the deadline when pacer will unblock sending.
     *
     * @return deadline when pacer will unblock sending
     */
    Deadline pacerDeadline();

    /**
     * Notify the congestion controller that sending is app-limited.
     */
    void appLimited();
}
