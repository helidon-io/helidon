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
 * A HANDSHAKE_DONE frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class HandshakeDoneFrame extends QuicFrame {

    /**
     * Incoming HANDSHAKE_DONE frame returned by QuicFrame.decode().
     *
     * @param buffer source buffer
     * @param type   frame type
     * @throws QuicTransportException if the frame was malformed
     */
    HandshakeDoneFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(HANDSHAKE_DONE);
    }

    private HandshakeDoneFrame() {
        super(HANDSHAKE_DONE);
    }

    /**
     * Outgoing HANDSHAKE_DONE frame.
     *
     * @return new HANDSHAKE_DONE frame
     */
    public static HandshakeDoneFrame create() {
        return new HandshakeDoneFrame();
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(buffer, HANDSHAKE_DONE, "type");
    }

    @Override
    public int size() {
        return variableLengthFieldLength(HANDSHAKE_DONE);
    }
}
