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
import io.helidon.quic.QuicTransportErrors;
import io.helidon.quic.QuicTransportException;

/**
 * A STREAMS_BLOCKED frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class StreamsBlockedFrame extends QuicFrame {

    private final long maxStreams;
    private final boolean bidi;

    /**
     * Incoming STREAMS_BLOCKED frame returned by QuicFrame.decode().
     *
     * @param buffer source buffer
     * @param type   frame type
     * @throws QuicTransportException if the frame was malformed
     */
    StreamsBlockedFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(STREAMS_BLOCKED);
        bidi = (type == STREAMS_BLOCKED);
        maxStreams = decodeVLField(buffer, "maxStreams");
        if (maxStreams > MaxStreamsFrame.MAX_VALUE) {
            throw new QuicTransportException("Invalid maximum streams",
                                             type, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
    }

    private StreamsBlockedFrame(boolean bidi, long maxStreams) {
        super(STREAMS_BLOCKED);
        this.bidi = bidi;
        this.maxStreams = requireVLRange(maxStreams, "maxStreams");
    }

    /**
     * Outgoing STREAMS_BLOCKED frame.
     *
     * @param bidi       whether this frame applies to bidirectional streams
     * @param maxStreams stream limit that blocked creation of additional streams
     * @return new STREAMS_BLOCKED frame
     */
    public static StreamsBlockedFrame create(boolean bidi, long maxStreams) {
        return new StreamsBlockedFrame(bidi, maxStreams);
    }

    @Override
    public long typeField() {
        return STREAMS_BLOCKED + (bidi ? 0 : 1);
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(buffer, typeField(), "type");
        encodeVLField(buffer, maxStreams, "maxStreams");
    }

    @Override
    public int size() {
        return variableLengthFieldLength(STREAMS_BLOCKED)
                + variableLengthFieldLength(maxStreams);
    }

    /**
     * Returns the advertised stream limit that blocked progress.
     *
     * @return maximum stream count
     */
    public long maxStreams() {
        return maxStreams;
    }

    /**
     * Returns whether this frame applies to bidirectional streams.
     *
     * @return {@code true} for bidirectional streams, {@code false} for unidirectional streams
     */
    public boolean isBidi() {
        return bidi;
    }

    @Override
    public String toString() {
        return "StreamsBlockedFrame(bidi=" + bidi
                + ", maxStreams=" + maxStreams + ')';
    }
}
