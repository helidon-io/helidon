/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.Api;

/**
 * This class models Quic Retry Packets, as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.2.5">RFC 9000, Section 17.2.5</a>:
 *
 * <blockquote><pre>{@code
 *   A Retry packet uses a long packet header with a type value of 0x03.
 *   It carries an address validation token created by the server.
 *   It is used by a server that wishes to perform a retry; see Section 8.1.
 *
 *   Retry Packet {
 *     Header Form (1) = 1,
 *     Fixed Bit (1) = 1,
 *     Long Packet Type (2) = 3,
 *     Unused (4),
 *     Version (32),
 *     Destination Connection ID Length (8),
 *     Destination Connection ID (0..160),
 *     Source Connection ID Length (8),
 *     Source Connection ID (0..160),
 *     Retry Token (..),
 *     Retry Integrity Tag (128),
 *   }
 * }</pre></blockquote>
 *
 * <p>Subclasses of this class may be used to model packets exchanged with either
 * <a href="https://www.rfc-editor.org/info/rfc9000">Quic Version 1</a> or
 * <a href="https://www.rfc-editor.org/info/rfc9369">Quic Version 2</a>.
 * Note that Quic Version 2 uses the same Retry Packet structure than
 * Quic Version 1, but uses a different long packet type than that shown above. See
 * <a href="https://www.rfc-editor.org/rfc/rfc9369#section-3.2">RFC 9369, Section 3.2</a>.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.2.5">RFC 9000, Section 17.2.5</a>
 * @see <a href="https://www.rfc-editor.org/info/rfc9000">
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport</a>
 * @see <a href="https://www.rfc-editor.org/info/rfc9369">
 *        RFC 9369: QUIC Version 2</a>
 */
@Api.Internal
public interface RetryPacket extends LongHeaderPacket {
    @Override
    default PacketType packetType() {
        return PacketType.RETRY;
    }

    /**
     * This packet type is not numbered: returns
     * {@link PacketNumberSpace#NONE} always.
     *
     * @return {@link PacketNumberSpace#NONE}
     */
    @Override
    default PacketNumberSpace numberSpace() {
        return PacketNumberSpace.NONE;
    }

    /**
     * This packet type is not numbered: always returns -1L.
     *
     * @return -1L
     */
    @Override
    default long packetNumber() {
        return -1L;
    }

    /**
     * Returns the packet's retry token.
     *
     * @return the packet's retry token.
     *
     * As per <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.2.5">RFC 9000, Section 17.2.5</a>:
     * <blockquote><pre>{@code
     *   An opaque token that the server can use to validate the client's address.
     * }</pre></blockquote>
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#validate-handshake">
     *        RFC 9000, Section 8.1</a>
     */
    byte[] retryToken();
}
