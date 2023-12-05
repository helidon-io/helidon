/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.websocket;

import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;

abstract sealed class AbstractWsFrame implements WsFrame permits ServerWsFrame, ClientWsFrame {
    private final LazyValue<BufferData> unmaskedData;
    private final long payloadLength;
    private final boolean fin;
    private final boolean isPayload;

    private volatile WsOpCode opCode;

    protected AbstractWsFrame(LazyValue<BufferData> unmaskedData,
                              long payloadLength,
                              boolean fin,
                              boolean isPayload,
                              WsOpCode opCode) {
        this.unmaskedData = unmaskedData;
        this.payloadLength = payloadLength;
        this.fin = fin;
        this.opCode = opCode;
        this.isPayload = isPayload;
    }

    @Override
    public boolean fin() {
        return fin;
    }

    @Override
    public WsOpCode opCode() {
        return opCode;
    }

    @Override
    public long payloadLength() {
        return payloadLength;
    }

    @Override
    public BufferData payloadData() {
        return unmaskedData.get();
    }

    @Override
    public boolean isPayload() {
        return isPayload;
    }

    /**
     * Configure the operation code of this frame.
     *
     * @param opCode code to use
     */
    public void opCode(WsOpCode opCode) {
        this.opCode = opCode;
    }

    @Override
    public String toString() {
        return opCode + (fin ? " (last): \n" : ": \n") + unmaskedData.get().debugDataHex();
    }

    protected static FrameHeader readFrameHeader(DataReader reader, int maxFrameLength) {
        int opCodeByte = reader.read();
        boolean fin = (opCodeByte & 0b10000000) != 0;
        int extensionFlags = opCodeByte & 0b01110000;
        if (extensionFlags != 0) {
            throw new WsCloseException("Extension flags defined where none should be", WsCloseCodes.PROTOCOL_ERROR);
        }
        WsOpCode opCode = WsOpCode.get(opCodeByte & 0b00001111);

        // byte 1 (possible to byte 9 if maximal number of bytes used for length)
        int lenByte = reader.read();
        boolean masked = (lenByte & 0b10000000) != 0;

        long frameLength;
        int length = lenByte & 0b01111111;
        if (length < 126) {
            frameLength = length;
        } else if (length == 126) {
            frameLength = reader.readBuffer(2).readInt16();
        } else {
            frameLength = reader.readBuffer(8).readLong();
        }

        if (frameLength < 0) {
            throw new WsCloseException("Negative payload length", WsCloseCodes.PROTOCOL_ERROR);
        }
        if (frameLength > maxFrameLength) {
            throw new WsCloseException("Payload too large", WsCloseCodes.TOO_BIG);
        }

        return new FrameHeader(opCode, fin, masked, (int) frameLength);
    }

    protected static BufferData readPayload(DataReader reader, FrameHeader header) {
        return reader.readBuffer(header.length());
    }

    protected static boolean isPayload(FrameHeader header) {
        WsOpCode opCode = header.opCode;
        return opCode == WsOpCode.BINARY || opCode == WsOpCode.TEXT;
    }

    protected record FrameHeader(WsOpCode opCode,
                                 boolean fin,
                                 boolean masked,
                                 int length) {
    }
}
