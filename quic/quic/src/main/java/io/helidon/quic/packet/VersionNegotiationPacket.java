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
 * This class models Quic Version Negotiation Packets, as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.2.1">RFC 9000, Section 17.2.1</a>.
 *
 * <blockquote><pre>{@code
 *   A Version Negotiation packet is inherently not version-specific.
 *   Upon receipt by a client, it will be identified as a Version
 *   Negotiation packet based on the Version field having a value of 0.
 *
 *   The Version Negotiation packet is a response to a client packet that
 *   contains a version that is not supported by the server, and is only
 *   sent by servers.
 *
 *   The layout of a Version Negotiation packet is:
 *
 *   Version Negotiation Packet {
 *     Header Form (1) = 1,
 *     Unused (7),
 *     Version (32) = 0,
 *     Destination Connection ID Length (8),
 *     Destination Connection ID (0..2040),
 *     Source Connection ID Length (8),
 *     Source Connection ID (0..2040),
 *     Supported Version (32) ...,
 *   }
 * }</pre></blockquote>
 *
 * @see <a href="https://www.rfc-editor.org/info/rfc9000">
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport</a>
 */
@Api.Internal
public interface VersionNegotiationPacket extends LongHeaderPacket {
    @Override
    default PacketType packetType() {
        return PacketType.VERSIONS;
    }

    @Override
    default int version() {
        return 0;
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
     * This packet type is not numbered: returns -1L always.
     *
     * @return -1L
     */
    @Override
    default long packetNumber() {
        return -1L;
    }

    /**
     * Returns versions supported by the sender of this packet.
     *
     * @return supported QUIC versions
     */
    int[] supportedVersions();
}
