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
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.quic.QuicTransportException;

/**
 * A NEW_TOKEN frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class NewTokenFrame extends QuicFrame {
    private final byte[] token;

    /**
     * Incoming NEW_TOKEN frame.
     *
     * @param buffer source buffer
     * @param type   frame type
     * @throws QuicTransportException if the frame was malformed
     */
    NewTokenFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(NEW_TOKEN);
        int length = decodeVLFieldAsInt(buffer, "token length");
        if (length == 0) {
            throw new QuicTransportException("Empty token",
                                             type,
                                             QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        validateRemainingLength(buffer, length, type);
        byte[] t = new byte[length];
        buffer.get(t);
        this.token = t;
    }

    private NewTokenFrame(ByteBuffer tokenBuf) {
        super(NEW_TOKEN);
        Objects.requireNonNull(tokenBuf);
        int length = tokenBuf.remaining();
        if (length <= 0) {
            throw new IllegalArgumentException("Invalid token length");
        }
        byte[] t = new byte[length];
        tokenBuf.get(t);
        this.token = t;
    }

    /**
     * Outgoing NEW_TOKEN frame whose token is the given ByteBuffer
     * (position to limit).
     *
     * @param tokenBuf token bytes
     * @return new NEW_TOKEN frame
     */
    public static NewTokenFrame create(ByteBuffer tokenBuf) {
        return new NewTokenFrame(tokenBuf);
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(buffer, NEW_TOKEN, "type");
        encodeVLField(buffer, token.length, "token length");
        buffer.put(token);
    }

    /**
     * Returns the NEW_TOKEN payload.
     *
     * @return defensive copy of the NEW_TOKEN payload
     */
    public byte[] token() {
        return this.token.clone();
    }

    @Override
    public int size() {
        int tokenLength = token.length;
        return variableLengthFieldLength(NEW_TOKEN)
                + variableLengthFieldLength(tokenLength)
                + tokenLength;
    }
}
