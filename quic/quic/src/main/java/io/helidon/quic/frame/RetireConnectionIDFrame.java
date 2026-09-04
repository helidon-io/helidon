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
 * A RETIRE_CONNECTION_ID frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class RetireConnectionIDFrame extends QuicFrame {

    private final long sequenceNumber;

    /**
     * Incoming RETIRE_CONNECTION_ID frame returned by QuicFrame.decode().
     *
     * @param buffer source buffer
     * @param type   frame type
     * @throws QuicTransportException if the frame was malformed
     */
    RetireConnectionIDFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(RETIRE_CONNECTION_ID);
        sequenceNumber = decodeVLField(buffer, "sequenceNumber");
    }

    private RetireConnectionIDFrame(long sequenceNumber) {
        super(RETIRE_CONNECTION_ID);
        this.sequenceNumber = requireVLRange(sequenceNumber, "sequenceNumber");
    }

    /**
     * Outgoing RETIRE_CONNECTION_ID frame.
     *
     * @param sequenceNumber sequence number of the connection ID being retired
     * @return new RETIRE_CONNECTION_ID frame
     */
    public static RetireConnectionIDFrame create(long sequenceNumber) {
        return new RetireConnectionIDFrame(sequenceNumber);
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(buffer, RETIRE_CONNECTION_ID, "type");
        encodeVLField(buffer, sequenceNumber, "sequenceNumber");
    }

    /**
     * Returns the sequence number of the connection ID being retired.
     *
     * @return connection-ID sequence number
     */
    public long sequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public int size() {
        return variableLengthFieldLength(RETIRE_CONNECTION_ID)
                + variableLengthFieldLength(sequenceNumber);
    }

    @Override
    public String toString() {
        return "RetireConnectionIDFrame("
                + "sequenceNumber=" + sequenceNumber
                + ')';
    }
}
