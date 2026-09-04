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
 * A RESET_STREAM frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class ResetStreamFrame extends QuicFrame {

    private final long streamID;
    private final long errorCode;
    private final long finalSize;

    ResetStreamFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(RESET_STREAM);
        streamID = decodeVLField(buffer, "streamID");
        errorCode = decodeVLField(buffer, "errorCode");
        finalSize = decodeVLField(buffer, "finalSize");
    }

    private ResetStreamFrame(
            long streamID,
            long errorCode,
            long finalSize) {
        super(RESET_STREAM);
        this.streamID = requireVLRange(streamID, "streamID");
        this.errorCode = requireVLRange(errorCode, "errorCode");
        this.finalSize = requireVLRange(finalSize, "finalSize");
    }

    /**
     * Creates an outgoing RESET_STREAM frame.
     *
     * @param streamID  stream identifier
     * @param errorCode application or transport error code
     * @param finalSize final size of the stream in bytes
     * @return new RESET_STREAM frame
     */
    public static ResetStreamFrame create(
            long streamID,
            long errorCode,
            long finalSize) {
        return new ResetStreamFrame(streamID, errorCode, finalSize);
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(buffer, RESET_STREAM, "type");
        encodeVLField(buffer, streamID, "streamID");
        encodeVLField(buffer, errorCode, "errorCode");
        encodeVLField(buffer, finalSize, "finalSize");
    }

    /**
     * Returns the stream identifier carried by this frame.
     *
     * @return stream identifier
     */
    public long streamId() {
        return streamID;
    }

    /**
     * Returns the error code carried by this frame.
     *
     * @return reset error code
     */
    public long errorCode() {
        return errorCode;
    }

    /**
     * Returns the final stream size carried by this frame.
     *
     * @return final stream size in bytes
     */
    public long finalSize() {
        return finalSize;
    }

    @Override
    public int size() {
        return variableLengthFieldLength(RESET_STREAM)
                + variableLengthFieldLength(streamID)
                + variableLengthFieldLength(errorCode)
                + variableLengthFieldLength(finalSize);
    }

    @Override
    public String toString() {
        return "ResetStreamFrame(stream=" + streamID
                + ", errorCode=" + errorCode
                + ", finalSize=" + finalSize + ')';
    }
}
