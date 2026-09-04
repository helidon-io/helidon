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
 * A PATH_RESPONSE frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class PathResponseFrame extends QuicFrame {

    /**
     * Required PATH_RESPONSE payload length in bytes.
     */
    public static final int LENGTH = 8;
    private final ByteBuffer data;

    /**
     * Incoming PATH_RESPONSE frame returned by QuicFrame.decode().
     *
     * @param buffer source buffer
     * @param type   frame type
     * @throws QuicTransportException if the frame was malformed
     */
    PathResponseFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(PATH_RESPONSE);
        validateRemainingLength(buffer, LENGTH, type);
        int position = buffer.position();
        data = buffer.slice(position, LENGTH);
        buffer.position(position + LENGTH);
    }

    private PathResponseFrame(ByteBuffer data) {
        super(PATH_RESPONSE);
        if (data.remaining() != LENGTH) {
            throw new IllegalArgumentException("response data must be 8 bytes");
        }
        this.data = data.slice();
    }

    /**
     * Outgoing PATH_RESPONSE frame.
     *
     * @param data eight-byte response payload
     * @return new PATH_RESPONSE frame
     */
    public static PathResponseFrame create(ByteBuffer data) {
        return new PathResponseFrame(data);
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(buffer, PATH_RESPONSE, "type");
        putByteBuffer(buffer, data);
    }

    /**
     * Returns the response payload.
     *
     * @return response payload
     */
    public ByteBuffer data() {
        return data;
    }

    @Override
    public int size() {
        return variableLengthFieldLength(PATH_RESPONSE) + LENGTH;
    }
}
