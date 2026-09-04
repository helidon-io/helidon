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

import java.util.List;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.quic.QuicConnectionId;
import io.helidon.quic.QuicTLSEngine.KeySpace;
import io.helidon.quic.frame.QuicFrame;

/**
 * A super-interface for all specific Quic packet implementation
 * classes.
 */
@Api.Internal
public interface QuicPacket {

    /**
     * Returns the packet's Destination Connection ID.
     *
     * @return the packet's Destination Connection ID
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-7.2">
     *        RFC 9000, Section 7.2</a>
     */
    QuicConnectionId destinationId();

    /**
     * Returns this packet's number space.
     *
     * @return this packet's number space
     */
    PacketNumberSpace numberSpace();

    /**
     * This packet size.
     *
     * @return the number of bytes needed to encode the packet.
     * @see #payloadSize()
     * @see #length()
     */
    int size();

    /**
     * Returns true if this packet is <em>ACK-eliciting</em>.
     *
     * @return true if this packet is <em>ACK-eliciting</em>.
     * A packet is <em>ACK-eliciting</em> if it contains any
     * {@linkplain QuicFrame#isAckEliciting()
     * <em>ACK-eliciting frame</em>}.
     */
    default boolean isAckEliciting() {
        List<QuicFrame> frames = frames();
        if (frames == null || frames.isEmpty()) {
            return false;
        }
        return frames.stream().anyMatch(QuicFrame::isAckEliciting);
    }

    /**
     * Whether this packet has a length field whose value can be read
     * from the packet bytes.
     *
     * @return whether this packet has a length.
     */
    default boolean hasLength() {
        return switch (packetType()) {
            case INITIAL, ZERORTT, HANDSHAKE -> true;
            default -> false;
        };
    }

    /**
     * Returns the length of the payload and packet number. Includes encryption tag.
     *
     * This is the value stored in the {@code Length} field in Initial,
     * Handshake and 0-RTT packets.
     *
     * @return the length of the payload and packet number.
     * @throws UnsupportedOperationException if this packet type does not have
     *                                      the {@code Length} field.
     * @see #hasLength()
     * @see #size()
     * @see #payloadSize()
     */
    default int length() {
        throw new UnsupportedOperationException();
    }

    /**
     * This packet header's type. Either SHORT or LONG.
     *
     * @return this packet's header's type.
     */
    HeadersType headersType();

    /**
     * Returns this packet's type.
     *
     * @return this packet's type
     */
    PacketType packetType();

    /**
     * Returns this packet's packet number.
     *
     * @return this packet's packet number, if applicable, {@code -1L} otherwise
     */
    default long packetNumber() {
        return -1L;
    }

    /**
     * Returns this packet's frames.
     *
     * @return this packet's frames
     */
    default List<QuicFrame> frames() {
        return List.of();
    }

    /**
     * Returns the packet's payload size.
     *
     * This is the number of bytes needed to encode the packet's
     * {@linkplain #frames() frames}.
     *
     * @return the packet's payload size
     * @see #size()
     * @see #length()
     */
    default int payloadSize() {
        List<QuicFrame> frames = frames();
        if (frames == null || frames.isEmpty()) {
            return 0;
        }
        return frames.stream()
                .mapToInt(QuicFrame::size)
                .reduce(0, Math::addExact);
    }

    /**
     * Return a concise human-readable representation for debugging.
     *
     * @return packet summary text
     */
    default String prettyPrint() {
        long pn = packetNumber();
        if (pn >= 0) {
            return String.format("%s(pn:%s, size=%s, frames:%s)", packetType(), pn, size(), frames());
        } else {
            return String.format("%s(size=%s)", packetType(), size());
        }
    }

    /**
     * The packet number space.
     * NONE is for packets that don't have a packet number,
     * such as Stateless Reset.
     */
    enum PacketNumberSpace {
        /**
         * Initial packet number space.
         */
        INITIAL,
        /**
         * Handshake packet number space.
         */
        HANDSHAKE,
        /**
         * Application-data packet number space.
         */
        APPLICATION,
        /**
         * No packet number space (for packets without packet numbers).
         */
        NONE;

        /**
         * Maps a {@code PacketType} to the corresponding
         * packet number space.
         * <p>
         * For {@link PacketType#RETRY}, {@link PacketType#VERSIONS}, and
         * {@link PacketType#NONE}, {@link PacketNumberSpace#NONE} is returned.
         *
         * @param packetType a packet type
         * @return the packet number space that corresponds to the
         *        given packet type
         */
        public static PacketNumberSpace of(PacketType packetType) {
            return switch (packetType) {
                case ONERTT, ZERORTT -> APPLICATION;
                case INITIAL -> INITIAL;
                case HANDSHAKE -> HANDSHAKE;
                case RETRY, VERSIONS, NONE -> NONE;
            };
        }

        /**
         * Maps a {@code KeySpace} to the corresponding
         * packet number space.
         * <p>
         * For {@link KeySpace#RETRY}, {@link PacketNumberSpace#NONE}
         * is returned.
         *
         * @param keySpace a key space
         * @return the packet number space that corresponds to the given
         *        key space.
         */
        public static PacketNumberSpace of(KeySpace keySpace) {
            return switch (keySpace) {
                case ONE_RTT, ZERO_RTT -> APPLICATION;
                case HANDSHAKE -> HANDSHAKE;
                case INITIAL -> INITIAL;
                case RETRY -> NONE;
            };
        }

        /**
         * Returns the canonical packet-number-space text.
         *
         * @return the canonical packet-number-space text
         */
        public String text() {
            return name();
        }
    }

    /**
     * The packet type for Quic packets.
     */
    enum PacketType {
        /**
         * Unknown or unsupported packet type.
         */
        NONE,
        /**
         * Initial packet.
         */
        INITIAL,
        /**
         * Version Negotiation packet.
         */
        VERSIONS,
        /**
         * 0-RTT packet.
         */
        ZERORTT,
        /**
         * Handshake packet.
         */
        HANDSHAKE,
        /**
         * Retry packet.
         */
        RETRY,
        /**
         * 1-RTT packet.
         */
        ONERTT;

        /**
         * Returns the canonical packet-type text.
         *
         * @return the canonical packet-type text
         */
        public String text() {
            return name();
        }

        /**
         * Whether packets of this type use the long header form.
         *
         * @return true if packets of this type use the long header form
         */
        public boolean isLongHeaderType() {
            return switch (this) {
                case ONERTT, NONE, VERSIONS -> false;
                default -> true;
            };
        }

        /**
         * Whether packets of this type are short-header packets.
         *
         * @return true if packets of this type are short-header packets
         */
        public boolean isShortHeaderType() {
            return this == ONERTT;
        }

        /**
         * Returns the QUIC-TLS key space corresponding to this packet type.
         *
         * Some packet types, such as {@link #VERSIONS}, do not have an associated
         * key space.
         *
         * @return the QUIC-TLS key space corresponding to this packet type
         */
        public Optional<KeySpace> keySpace() {
            return switch (this) {
                case INITIAL -> Optional.of(KeySpace.INITIAL);
                case HANDSHAKE -> Optional.of(KeySpace.HANDSHAKE);
                case RETRY -> Optional.of(KeySpace.RETRY);
                case ZERORTT -> Optional.of(KeySpace.ZERO_RTT);
                case ONERTT -> Optional.of(KeySpace.ONE_RTT);
                case VERSIONS -> Optional.empty();
                case NONE -> Optional.empty();
            };
        }
    }

    /**
     * The Headers Type of the packet.
     * This is either SHORT or LONG, or NONE when it can't be
     * determined, or when we know that the packet is a stateless
     * reset packet. A stateless reset packet is indistinguishable
     * from a short header packet, so we only know that a packet
     * is a stateless reset if we built it. In that case, the packet
     * may advertise its header's type as NONE.
     */
    enum HeadersType {
        /**
         * Header type is unknown or not applicable.
         */
        NONE,
        /**
         * Short-header packet.
         */
        SHORT,
        /**
         * Long-header packet.
         */
        LONG
    }

}
