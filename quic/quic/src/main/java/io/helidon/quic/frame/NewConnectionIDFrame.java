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
 * A NEW_CONNECTION_ID frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class NewConnectionIDFrame extends QuicFrame {

    private final long sequenceNumber;
    private final long retirePriorTo;
    private final ByteBuffer connectionId;
    private final ByteBuffer statelessResetToken;

    /**
     * Incoming NEW_CONNECTION_ID frame returned by QuicFrame.decode().
     *
     * @param buffer source buffer
     * @param type   frame type
     * @throws QuicTransportException if the frame was malformed
     */
    NewConnectionIDFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(NEW_CONNECTION_ID);
        sequenceNumber = decodeVLField(buffer, "sequenceNumber");
        retirePriorTo = decodeVLField(buffer, "retirePriorTo");
        if (retirePriorTo > sequenceNumber) {
            throw new QuicTransportException("Invalid retirePriorTo",
                                             type, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        validateRemainingLength(buffer, 17, type);
        int length = Byte.toUnsignedInt(buffer.get());
        if (length < 1 || length > 20) {
            throw new QuicTransportException("Invalid connection ID",
                                             type, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        validateRemainingLength(buffer, length + 16, type);
        int position = buffer.position();
        connectionId = buffer.slice(position, length);
        position += length;
        statelessResetToken = buffer.slice(position, 16);
        position += 16;
        buffer.position(position);
    }

    private NewConnectionIDFrame(long sequenceNumber,
                                 long retirePriorTo,
                                 ByteBuffer connectionId,
                                 ByteBuffer statelessResetToken) {
        super(NEW_CONNECTION_ID);
        this.sequenceNumber = requireVLRange(sequenceNumber, "sequenceNumber");
        this.retirePriorTo = requireVLRange(retirePriorTo, "retirePriorTo");
        int length = connectionId.remaining();
        if (length < 1 || length > 20) {
            throw new IllegalArgumentException("invalid length");
        }
        this.connectionId = connectionId.slice();
        if (statelessResetToken.remaining() != 16) {
            throw new IllegalArgumentException("stateless reset token must be 16 bytes");
        }
        this.statelessResetToken = statelessResetToken.slice();
    }

    /**
     * Outgoing NEW_CONNECTION_ID frame.
     *
     * @param sequenceNumber      sequence number of the new connection ID
     * @param retirePriorTo       highest sequence number that can be retired immediately
     * @param connectionId        replacement connection ID
     * @param statelessResetToken associated stateless reset token
     * @return new NEW_CONNECTION_ID frame
     */
    public static NewConnectionIDFrame create(long sequenceNumber,
                                              long retirePriorTo,
                                              ByteBuffer connectionId,
                                              ByteBuffer statelessResetToken) {
        return new NewConnectionIDFrame(sequenceNumber, retirePriorTo, connectionId, statelessResetToken);
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(buffer, NEW_CONNECTION_ID, "type");
        encodeVLField(buffer, sequenceNumber, "sequenceNumber");
        encodeVLField(buffer, retirePriorTo, "retirePriorTo");
        int length = connectionId.remaining();
        buffer.put((byte) length);
        putByteBuffer(buffer, connectionId);
        putByteBuffer(buffer, statelessResetToken);
    }

    @Override
    public int size() {
        return variableLengthFieldLength(NEW_CONNECTION_ID)
                + variableLengthFieldLength(sequenceNumber)
                + variableLengthFieldLength(retirePriorTo)
                + 1 // connection length
                + connectionId.remaining()
                + statelessResetToken.remaining();
    }

    /**
     * Returns the sequence number assigned to this connection ID.
     *
     * @return sequence number
     */
    public long sequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Returns the highest sequence number the peer should retire before using this ID.
     *
     * @return highest prior sequence number to retire
     */
    public long retirePriorTo() {
        return retirePriorTo;
    }

    /**
     * Returns the advertised replacement connection ID.
     *
     * @return replacement connection ID
     */
    public ByteBuffer connectionId() {
        return connectionId;
    }

    /**
     * Returns the associated stateless reset token.
     *
     * @return stateless reset token
     */
    public ByteBuffer statelessResetToken() {
        return statelessResetToken;
    }

    @Override
    public String toString() {
        return "NewConnectionIDFrame(seqNumber=" + sequenceNumber
                + ", retirePriorTo=" + retirePriorTo
                + ", connIdLength=" + connectionId.remaining()
                + ")";
    }
}
