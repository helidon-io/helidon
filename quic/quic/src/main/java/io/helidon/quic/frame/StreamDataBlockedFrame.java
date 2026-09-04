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
 * A STREAM_DATA_BLOCKED frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class StreamDataBlockedFrame extends QuicFrame {

    private final long streamId;
    private final long maxStreamData;

    /**
     * Incoming STREAM_DATA_BLOCKED frame returned by QuicFrame.decode().
     *
     * @param buffer source buffer
     * @param type   frame type
     * @throws QuicTransportException if the frame was malformed
     */
    StreamDataBlockedFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(STREAM_DATA_BLOCKED);
        streamId = decodeVLField(buffer, "streamID");
        maxStreamData = decodeVLField(buffer, "maxData");
    }

    private StreamDataBlockedFrame(long streamId, long maxStreamData) {
        super(STREAM_DATA_BLOCKED);
        this.streamId = requireVLRange(streamId, "streamID");
        this.maxStreamData = requireVLRange(maxStreamData, "maxStreamData");
    }

    /**
     * Outgoing STREAM_DATA_BLOCKED frame.
     *
     * @param streamId      stream identifier
     * @param maxStreamData maximum stream data the sender is currently blocked by
     * @return new STREAM_DATA_BLOCKED frame
     */
    public static StreamDataBlockedFrame create(long streamId, long maxStreamData) {
        return new StreamDataBlockedFrame(streamId, maxStreamData);
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(buffer, STREAM_DATA_BLOCKED, "type");
        encodeVLField(buffer, streamId, "streamID");
        encodeVLField(buffer, maxStreamData, "maxStreamData");
    }

    /**
     * Returns the stream-data limit that blocked the sender.
     *
     * @return maximum stream data value
     */
    public long maxStreamData() {
        return maxStreamData;
    }

    /**
     * Returns the stream identifier carried by this frame.
     *
     * @return stream identifier
     */
    public long streamId() {
        return streamId;
    }

    @Override
    public int size() {
        return variableLengthFieldLength(STREAM_DATA_BLOCKED)
                + variableLengthFieldLength(streamId)
                + variableLengthFieldLength(maxStreamData);
    }

    @Override
    public String toString() {
        return "StreamDataBlockedFrame("
                + "streamId=" + streamId
                + ", maxStreamData=" + maxStreamData
                + ')';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StreamDataBlockedFrame that)) {
            return false;
        }
        if (streamId != that.streamId) {
            return false;
        }
        return maxStreamData == that.maxStreamData;
    }

    @Override
    public int hashCode() {
        int result = (int) (streamId ^ (streamId >>> 32));
        result = 31 * result + (int) (maxStreamData ^ (maxStreamData >>> 32));
        return result;
    }
}
