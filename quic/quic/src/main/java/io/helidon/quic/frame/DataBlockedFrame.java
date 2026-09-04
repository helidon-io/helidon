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

package io.helidon.quic.frame;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import io.helidon.common.Api;
import io.helidon.quic.QuicTransportException;

/**
 * A DATA_BLOCKED frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class DataBlockedFrame extends QuicFrame {

    private final long maxData;

    /**
     * Incoming DATA_BLOCKED frame returned by QuicFrame.decode().
     *
     * @param buffer source buffer
     * @param type   frame type
     * @throws QuicTransportException if the frame was malformed
     */
    DataBlockedFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(DATA_BLOCKED);
        maxData = decodeVLField(buffer, "maxData");
    }

    private DataBlockedFrame(long maxData) {
        super(DATA_BLOCKED);
        this.maxData = requireVLRange(maxData, "maxData");
    }

    /**
     * Outgoing DATA_BLOCKED frame.
     *
     * @param maxData connection-level data limit that caused blocking
     * @return new DATA_BLOCKED frame
     */
    public static DataBlockedFrame create(long maxData) {
        return new DataBlockedFrame(maxData);
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(buffer, DATA_BLOCKED, "type");
        encodeVLField(buffer, maxData, "maxData");
    }

    /**
     * Returns the connection-level limit that caused the sender to become blocked.
     *
     * @return connection-level limit that caused the sender to become blocked
     */
    public long maxData() {
        return maxData;
    }

    @Override
    public int size() {
        return variableLengthFieldLength(DATA_BLOCKED)
                + variableLengthFieldLength(maxData);
    }

    @Override
    public String toString() {
        return "DataBlockedFrame("
                + "maxData=" + maxData
                + ')';
    }
}
