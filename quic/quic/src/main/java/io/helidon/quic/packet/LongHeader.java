/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.quic.QuicConnectionId;

/**
 * This class models Quic Long Header Packet header, as defined by.
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
 * @see <a href="https://www.rfc-editor.org/info/rfc8999">
 *        RFC 8999: Version-Independent Properties of QUIC</a>
 */
@Api.Internal
public final class LongHeader implements QuicPacketDecoder.LongHeaderResult {
    private final int version;
    private final QuicConnectionId destinationId;
    private final QuicConnectionId sourceId;
    private final int headerLength;

    private LongHeader(int version,
                       QuicConnectionId destinationId,
                       QuicConnectionId sourceId,
                       int headerLength) {
        this.version = version;
        this.destinationId = Objects.requireNonNull(destinationId, "destinationId");
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.headerLength = headerLength;
    }

    static LongHeader create(int version,
                             QuicConnectionId destinationId,
                             QuicConnectionId sourceId,
                             int headerLength) {
        return new LongHeader(version, destinationId, sourceId, headerLength);
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public LongHeader orElseThrow() {
        return this;
    }

    /**
     * Returns the QUIC version from the long header.
     *
     * @return the QUIC version from the long header.
     */
    public int version() {
        return version;
    }

    /**
     * Returns the destination connection id from the long header.
     *
     * @return the destination connection id from the long header.
     */
    public QuicConnectionId destinationId() {
        return destinationId;
    }

    /**
     * Returns the source connection id from the long header.
     *
     * @return the source connection id from the long header.
     */
    public QuicConnectionId sourceId() {
        return sourceId;
    }

    /**
     * Returns the number of bytes occupied by the decoded long header.
     *
     * @return the number of bytes occupied by the decoded long header.
     */
    public int headerLength() {
        return headerLength;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LongHeader other)) {
            return false;
        }
        return version == other.version
                && headerLength == other.headerLength
                && destinationId.equals(other.destinationId)
                && sourceId.equals(other.sourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, destinationId, sourceId, headerLength);
    }

    @Override
    public String toString() {
        return "LongHeader[version="
                + version
                + ", destinationId="
                + destinationId
                + ", sourceId="
                + sourceId
                + ", headerLength="
                + headerLength
                + ']';
    }
}
