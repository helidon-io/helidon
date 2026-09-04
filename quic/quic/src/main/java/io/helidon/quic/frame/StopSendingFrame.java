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
 * A STOP_SENDING frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class StopSendingFrame extends QuicFrame {

    private final long streamID;
    private final long errorCode;

    StopSendingFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(STOP_SENDING);
        streamID = decodeVLField(buffer, "streamID");
        errorCode = decodeVLField(buffer, "errorCode");
    }

    private StopSendingFrame(long streamID, long errorCode) {
        super(STOP_SENDING);
        this.streamID = requireVLRange(streamID, "streamID");
        this.errorCode = requireVLRange(errorCode, "errorCode");
    }

    /**
     * Creates an outgoing STOP_SENDING frame.
     *
     * @param streamID  stream identifier
     * @param errorCode application error code requested by the sender
     * @return new STOP_SENDING frame
     */
    public static StopSendingFrame create(long streamID, long errorCode) {
        return new StopSendingFrame(streamID, errorCode);
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(buffer, STOP_SENDING, "type");
        encodeVLField(buffer, streamID, "streamID");
        encodeVLField(buffer, errorCode, "errorCode");
    }

    /**
     * Returns the stream identifier carried by this frame.
     *
     * @return stream identifier
     */
    public long streamID() {
        return streamID;
    }

    /**
     * Returns the error code carried by this frame.
     *
     * @return application error code
     */
    public long errorCode() {
        return errorCode;
    }

    @Override
    public int size() {
        return variableLengthFieldLength(STOP_SENDING)
                + variableLengthFieldLength(streamID)
                + variableLengthFieldLength(errorCode);
    }

    @Override
    public String toString() {
        return "StopSendingFrame(stream=" + streamID
                + ", errorCode=" + errorCode + ')';
    }
}
