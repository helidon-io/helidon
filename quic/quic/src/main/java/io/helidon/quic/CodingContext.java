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

import java.nio.ByteBuffer;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.quic.packet.QuicPacket;

/**
 * Packet encoding and decoding context for a QUIC connection or endpoint.
 */
@Api.Internal
public interface CodingContext {

    /**
     * Returns the largest incoming packet number successfully processed
     * in the given packet number space.
     * <p>
     * This method is used when decoding the packet number of an incoming packet.
     *
     * @param packetSpace the packet number space
     * @return the largest incoming packet number successfully processed in the given packet number space
     */
    long largestProcessedPN(QuicPacket.PacketNumberSpace packetSpace);

    /**
     * Returns the largest outgoing packet number acknowledged by the peer
     * in the given packet number space.
     * <p>
     * This method is used when encoding the packet number of an outgoing packet.
     *
     * @param packetSpace the packet number space
     * @return the largest outgoing packet number acknowledged by the peer in the given packet number space
     */
    long largestAckedPN(QuicPacket.PacketNumberSpace packetSpace);

    /**
     * Returns the length of the local connection ids expected
     * to be found in incoming short header packets.
     *
     * @return the length of the local connection ids expected in incoming short header packets
     */
    int connectionIdLength();

    /**
     * Returns the maximum number of packet-number ranges accepted in one peer ACK frame.
     *
     * @return maximum number of accepted ACK ranges per frame
     */
    default int maxAckRangesPerFrame() {
        return QuicConfigSupport.DEFAULT_MAX_ACK_RANGES_PER_FRAME;
    }

    /**
     * Returns the largest incoming packet number successfully processed
     * in the packet number space corresponding to the given packet type.
     * <p>
     * This is equivalent to calling:<pre>
     *    {@code largestProcessedPN(QuicPacket.PacketNumberSpace.of(packetType));}
     * </pre>
     * This method is used when decoding the packet number of an incoming packet.
     *
     * @param packetType the packet type
     * @return the largest incoming packet number successfully processed for the packet type
     */
    default long largestProcessedPN(QuicPacket.PacketType packetType) {
        return largestProcessedPN(QuicPacket.PacketNumberSpace.of(packetType));
    }

    /**
     * Returns the largest outgoing packet number acknowledged by the peer
     * in the packet number space corresponding to the given packet type.
     * <p>
     * This is equivalent to calling:<pre>
     *    {@code largestAckedPN(QuicPacket.PacketNumberSpace.of(packetType));}
     * </pre>
     * This method is used when encoding the packet number of an outgoing packet.
     *
     * @param packetType the packet type
     * @return the largest outgoing packet number acknowledged by the peer for the packet type
     */
    default long largestAckedPN(QuicPacket.PacketType packetType) {
        return largestAckedPN(QuicPacket.PacketNumberSpace.of(packetType));
    }

    /**
     * Writes the given outgoing packet in the given byte buffer.
     * This method moves the position of the byte buffer.
     *
     * @param packet the outgoing packet to write
     * @param buffer the byte buffer to write the packet into
     * @return the number of bytes written
     * @throws java.nio.BufferOverflowException if the buffer doesn't have
     *                                         enough space to write the packet
     * @throws QuicKeyUnavailableException      if the required packet protection keys are not available yet
     * @throws QuicTransportException           if packet encoding fails
     */
    int writePacket(QuicPacket packet, ByteBuffer buffer)
            throws QuicKeyUnavailableException, QuicTransportException;

    /**
     * Reads an encrypted packet from the given byte buffer.
     * This method moves the position of the byte buffer.
     * The returned packet may retain payload slices backed by the supplied transport-owned storage. Overlapping storage must
     * not be overwritten or reused while the packet or any derived frame remains in use.
     *
     * @param src a byte buffer containing a non encrypted packet
     * @return parsed packet, or {@link Optional#empty()} if packet keys are unavailable
     * @throws io.helidon.quic.packet.QuicPacketDecodeException if a discardable packet cannot be decoded
     * @throws QuicKeyUnavailableException if the required packet protection keys are not available yet
     * @throws QuicTransportException      if packet is correctly signed but malformed
     */
    Optional<QuicPacket> parsePacket(ByteBuffer src)
            throws QuicKeyUnavailableException, QuicTransportException;

    /**
     * Returns the original destination connection id, required for
     * calculating the retry integrity tag.
     * <p>
     * This is only of interest when protecting/unprotecting a {@linkplain
     * QuicPacket.PacketType#RETRY Retry Packet}.
     *
     * @return the original destination connection id, required for calculating
     *        the retry integrity tag
     */
    QuicConnectionId originalServerConnId();

    /**
     * Returns the TLS engine associated with this context.
     *
     * @return the TLS engine associated with this context
     */
    QuicTLSEngine tlsEngine();

    /**
     * Returns a log tag for packet diagnostics.
     *
     * @return log tag for packet diagnostics
     */
    default String logTag() {
        return "";
    }

    /**
     * Whether packet diagnostics may include raw protocol data.
     *
     * @return whether unsafe raw protocol logging is enabled
     */
    default boolean unsafeRawData() {
        return false;
    }

    /**
     * Checks if the provided token is valid for the given context and connection ID.
     *
     * @param destinationID destination connection ID found in the packet
     * @param token         token to verify
     * @return true if token is valid, false otherwise
     */
    boolean verifyToken(QuicConnectionId destinationID, byte[] token);

    /**
     * Returns the minimum payload size for short packet payloads.
     * Padding will be added to match that size if needed.
     *
     * @param destConnectionIdLength the length of the destination
     *                              connectionId included in the packet
     * @return the minimum payload size for short packet payloads
     */
    default int minShortPacketPayloadSize(int destConnectionIdLength) {
        // See RFC 9000, Section 10.3
        // https://www.rfc-editor.org/rfc/rfc9000#section-10.3
        // [..] the endpoint SHOULD ensure that all packets it sends
        // are at least 22 bytes longer than the minimum connection
        // ID length that it requests the peer to include in its
        // packets [...]
        //
        // A 1-RTT packet contains the peer connection id
        // (whose length is destConnectionIdLength), therefore the
        // payload should be at least 5 - (destConnectionIdLength
        // - connectionIdLength()) - where connectionIdLength is the
        // length of the local connection ID.
        return 5 - (destConnectionIdLength - connectionIdLength());
    }
}
