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
 * A MAX_STREAM frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class MaxStreamsFrame extends QuicFrame {
    static final long MAX_VALUE = 1L << 60;

    private final long maxStreams;
    private final boolean bidi;

    /**
     * Incoming MAX_STREAMS frame returned by QuicFrame.decode().
     *
     * @param buffer source buffer
     * @param type   frame type
     * @throws QuicTransportException if the frame was malformed
     */
    MaxStreamsFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(MAX_STREAMS);
        bidi = (type == MAX_STREAMS);
        maxStreams = decodeVLField(buffer, "maxStreams");
        if (maxStreams > MAX_VALUE) {
            throw new QuicTransportException("Invalid maximum streams",
                                             type, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
    }

    private MaxStreamsFrame(boolean bidi, long maxStreams) {
        super(MAX_STREAMS);
        this.bidi = bidi;
        this.maxStreams = requireVLRange(maxStreams, "maxStreams");
    }

    /**
     * Outgoing MAX_STREAMS frame.
     *
     * @param bidi       whether the limit applies to bidirectional streams
     * @param maxStreams new stream-count limit
     * @return new MAX_STREAMS frame
     */
    public static MaxStreamsFrame create(boolean bidi, long maxStreams) {
        return new MaxStreamsFrame(bidi, maxStreams);
    }

    @Override
    public long typeField() {
        return MAX_STREAMS + (bidi ? 0 : 1);
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(buffer, typeField(), "type");
        encodeVLField(buffer, maxStreams, "maxStreams");
    }

    /**
     * Returns the advertised maximum number of streams.
     *
     * @return advertised maximum number of streams
     */
    public long maxStreams() {
        return maxStreams;
    }

    /**
     * Checks whether the limit applies to bidirectional streams.
     *
     * @return {@code true} when the limit applies to bidirectional streams
     */
    public boolean isBidi() {
        return bidi;
    }

    @Override
    public int size() {
        return variableLengthFieldLength(MAX_STREAMS)
                + variableLengthFieldLength(maxStreams);
    }

    @Override
    public String toString() {
        return "MaxStreamsFrame(bidi=" + bidi
                + ", maxStreams=" + maxStreams + ')';
    }
}
