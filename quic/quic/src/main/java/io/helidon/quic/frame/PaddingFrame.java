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
 * PADDING frame sequence.
 * Since padding frames comprise a single zero byte, this class actually represents sequences of PADDING frames.
 * When decoding, the class consumes all the zero bytes that are
 * available and when encoding, the number of required padding bytes
 * is specified.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class PaddingFrame extends QuicFrame {

    private final int size;

    /**
     * Incoming PADDING frame sequence.
     *
     * @param buffer source buffer
     * @param type   frame type
     * @throws QuicTransportException if the frame was malformed
     */
    PaddingFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(PADDING);
        int count = 1;
        while (buffer.hasRemaining()) {
            if (buffer.get() == 0) {
                count++;
            } else {
                int pos = buffer.position();
                buffer.position(pos - 1);
                break;
            }
        }
        size = count;
    }

    private PaddingFrame(int size) {
        super(PADDING);
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than zero");
        }
        this.size = size;
    }

    /**
     * Outgoing PADDING frame sequence.
     *
     * @param size the number of padding frames that should be written
     *            to the buffer. Each frame is one byte long.
     * @return new padding frame
     */
    public static PaddingFrame create(int size) {
        return new PaddingFrame(size);
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        for (int i = 0; i < size; i++) {
            buffer.put((byte) 0); // would benefit from a fill operation here?
        }
    }

    /**
     * Returns the number of PADDING frames represented.
     *
     * @return the number of PADDING frames represented.
     */
    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isAckEliciting() {
        return false;
    }

    @Override
    public String toString() {
        return "Padding(" + size + ")";
    }
}
