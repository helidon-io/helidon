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
 * A MAX_STREAM_DATA frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class MaxStreamDataFrame extends QuicFrame {

    private final long streamID;
    private final long maxStreamData;

    /**
     * Incoming MAX_STREAM_DATA frame returned by QuicFrame.decode().
     *
     * @param buffer source buffer
     * @param type   frame type
     * @throws QuicTransportException if the frame was malformed
     */
    MaxStreamDataFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(MAX_STREAM_DATA);
        streamID = decodeVLField(buffer, "streamID");
        maxStreamData = decodeVLField(buffer, "maxData");
    }

    private MaxStreamDataFrame(long streamID, long maxStreamData) {
        super(MAX_STREAM_DATA);
        this.streamID = requireVLRange(streamID, "streamID");
        this.maxStreamData = requireVLRange(maxStreamData, "maxStreamData");
    }

    /**
     * Outgoing MAX_STREAM_DATA frame.
     *
     * @param streamID      stream identifier
     * @param maxStreamData new per-stream flow-control limit
     * @return new MAX_STREAM_DATA frame
     */
    public static MaxStreamDataFrame create(long streamID, long maxStreamData) {
        return new MaxStreamDataFrame(streamID, maxStreamData);
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(buffer, MAX_STREAM_DATA, "type");
        encodeVLField(buffer, streamID, "streamID");
        encodeVLField(buffer, maxStreamData, "maxStreamData");
    }

    /**
     * Returns the advertised per-stream flow-control limit.
     *
     * @return advertised per-stream flow-control limit
     */
    public long maxStreamData() {
        return maxStreamData;
    }

    /**
     * Returns the stream identifier to which this limit applies.
     *
     * @return stream identifier to which this limit applies
     */
    public long streamID() {
        return streamID;
    }

    @Override
    public int size() {
        return variableLengthFieldLength(MAX_STREAM_DATA)
                + variableLengthFieldLength(streamID)
                + variableLengthFieldLength(maxStreamData);
    }

    @Override
    public String toString() {
        return "MaxStreamDataFrame("
                + "streamId=" + streamID
                + ", maxStreamData=" + maxStreamData
                + ')';
    }
}
