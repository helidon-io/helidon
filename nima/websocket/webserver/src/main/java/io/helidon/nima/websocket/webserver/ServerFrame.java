/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.websocket.webserver;

import java.nio.charset.StandardCharsets;

import io.helidon.common.buffers.BufferData;

/**
 * Frame from a server (never masked).
 */
class ServerFrame implements Frame {
    private final long payloadLength;
    private final BufferData data;
    private final boolean fin;
    private final boolean isPayload;

    private WsOpCode opCode;

    ServerFrame(WsOpCode opCode, BufferData data, boolean fin, boolean isPayload) {
        this.opCode = opCode;
        this.payloadLength = data.available();
        this.data = data;
        this.fin = fin;
        this.isPayload = isPayload;
    }

    static ServerFrame data(String text, boolean last) {
        BufferData bufferData = BufferData.create(text.getBytes(StandardCharsets.UTF_8));

        return new ServerFrame(WsOpCode.TEXT,
                               bufferData,
                               last,
                               true);
    }

    static ServerFrame data(BufferData bufferData, boolean last) {
        return new ServerFrame(WsOpCode.BINARY,
                               bufferData,
                               last,
                               true);
    }

    static ServerFrame control(WsOpCode opCode, BufferData bufferData) {
        if (bufferData.available() > 125) {
            throw new IllegalArgumentException("Control frames cannot have more than 125 bytes");
        }
        return new ServerFrame(opCode, bufferData, true, false);
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
    public boolean masked() {
        return false;
    }

    @Override
    public long payloadLength() {
        return payloadLength;
    }

    @Override
    public int[] maskingKey() {
        return null;
    }

    @Override
    public BufferData payloadData() {
        return data;
    }

    @Override
    public String toString() {
        return opCode + (fin ? " (last): \n" : ": \n") + data.debugDataHex();
    }

    public boolean isPayload() {
        return isPayload;
    }

    ServerFrame opCode(WsOpCode opCode) {
        this.opCode = opCode;
        return this;
    }
}
