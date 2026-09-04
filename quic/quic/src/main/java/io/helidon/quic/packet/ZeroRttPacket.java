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

import java.util.List;

import io.helidon.common.Api;
import io.helidon.quic.frame.QuicFrame;

/**
 * This class models Quic 0-RTT Packets, as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.2.3">RFC 9000, Section 17.2.3</a>:
 *
 * <blockquote><pre>{@code
 *   A 0-RTT packet uses long headers with a type value of 0x01, followed
 *   by the Length and Packet Number fields; see Section 17.2. The first
 *   byte contains the Reserved and Packet Number Length bits; see Section 17.2.
 *   A 0-RTT packet is used to carry "early" data from the client to the server
 *   as part of the first flight, prior to handshake completion. As part of the
 *   TLS handshake, the server can accept or reject this early data.
 *
 *   See Section 2.3 of [TLS13] for a discussion of 0-RTT data and its limitations.
 *
 *   0-RTT Packet {
 *     Header Form (1) = 1,
 *     Fixed Bit (1) = 1,
 *     Long Packet Type (2) = 1,
 *     Reserved Bits (2),
 *     Packet Number Length (2),
 *     Version (32),
 *     Destination Connection ID Length (8),
 *     Destination Connection ID (0..160),
 *     Source Connection ID Length (8),
 *     Source Connection ID (0..160),
 *     Length (i),
 *     Packet Number (8..32),
 *     Packet Payload (..),
 *   }
 * } </pre></blockquote>
 *
 * <p>Subclasses of this class may be used to model packets exchanged with either
 * <a href="https://www.rfc-editor.org/info/rfc9000">Quic Version 1</a> or
 * <a href="https://www.rfc-editor.org/info/rfc9369">Quic Version 2</a>.
 * Note that Quic Version 2 uses the same 0-RTT Packet structure than
 * Quic Version 1, but uses a different long packet type than that shown above. See
 * <a href="https://www.rfc-editor.org/rfc/rfc9369#section-3.2">RFC 9369, Section 3.2</a>.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.2">
 *        RFC 9000, Section 17.2</a>
 * @see <a href="https://www.rfc-editor.org/info/rfc8446#section-2.3">
 *        [TLS13] RFC 8446, Section 2.3</a>
 * @see <a href="https://www.rfc-editor.org/info/rfc9000">
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport</a>
 * @see <a href="https://www.rfc-editor.org/info/rfc9369">
 *        RFC 9369: QUIC Version 2</a>
 */
@Api.Internal
public interface ZeroRttPacket extends LongHeaderPacket {
    @Override
    default PacketType packetType() {
        return PacketType.ZERORTT;
    }

    @Override
    default PacketNumberSpace numberSpace() {
        return PacketNumberSpace.APPLICATION;
    }

    @Override
    default boolean hasLength() {
        return true;
    }

    /**
     * This packet number.
     *
     * @return this packet number.
     */
    @Override
    long packetNumber();

    @Override
    List<QuicFrame> frames();
}
