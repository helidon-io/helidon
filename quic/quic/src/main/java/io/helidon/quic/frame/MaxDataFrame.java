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
 * A MAX_DATA frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class MaxDataFrame extends QuicFrame {

    private final long maxData;

    /**
     * Incoming MAX_DATA frame returned by QuicFrame.decode().
     *
     * @param buffer source buffer
     * @param type   frame type
     * @throws QuicTransportException if the frame was malformed
     */
    MaxDataFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(MAX_DATA);
        maxData = decodeVLField(buffer, "maxData");
    }

    private MaxDataFrame(long maxData) {
        super(MAX_DATA);
        this.maxData = requireVLRange(maxData, "maxData");
    }

    /**
     * Outgoing MAX_DATA frame.
     *
     * @param maxData new connection-level flow-control limit
     * @return new MAX_DATA frame
     */
    public static MaxDataFrame create(long maxData) {
        return new MaxDataFrame(maxData);
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(buffer, MAX_DATA, "type");
        encodeVLField(buffer, maxData, "maxData");
    }

    /**
     * Returns the advertised connection-level flow-control limit.
     *
     * @return advertised connection-level flow-control limit
     */
    public long maxData() {
        return maxData;
    }

    @Override
    public int size() {
        return variableLengthFieldLength(MAX_DATA)
                + variableLengthFieldLength(maxData);
    }

    @Override
    public String toString() {
        return "MaxDataFrame("
                + "maxData=" + maxData
                + ')';
    }
}
