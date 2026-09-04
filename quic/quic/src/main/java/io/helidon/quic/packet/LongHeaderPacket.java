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
import io.helidon.quic.QuicConnectionId;

/**
 * This class models Quic Long Header Packets, as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc8999#section-5.1">RFC 8999, Section 5.1</a>:
 *
 * <blockquote><pre>{@code
 *   Long Header Packet {
 *      Header Form (1) = 1,
 *      Version-Specific Bits (7),
 *      Version (32),
 *      Destination Connection ID Length (8),
 *      Destination Connection ID (0..2040),
 *      Source Connection ID Length (8),
 *      Source Connection ID (0..2040),
 *      Version-Specific Data (..),
 *   }
 * }</pre></blockquote>
 *
 * <p>Subclasses of this class may be used to model packets exchanged with either
 * <a href="https://www.rfc-editor.org/info/rfc9000">Quic Version 1</a> or
 * <a href="https://www.rfc-editor.org/info/rfc9369">Quic Version 2</a>.
 *
 * @see <a href="https://www.rfc-editor.org/info/rfc8999">
 *        RFC 8999: Version-Independent Properties of QUIC</a>
 * @see <a href="https://www.rfc-editor.org/info/rfc9000">
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport</a>
 * @see <a href="https://www.rfc-editor.org/info/rfc9369">
 *        RFC 9369: QUIC Version 2</a>
 */
@Api.Internal
public interface LongHeaderPacket extends QuicPacket {
    @Override
    default HeadersType headersType() {
        return HeadersType.LONG;
    }

    /**
     * Returns the packet's source connection ID.
     *
     * @return the packet's source connection ID
     */
    QuicConnectionId sourceId();

    /**
     * Returns the Quic version of the packet.
     *
     * @return the Quic version of the packet
     */
    int version();

}
