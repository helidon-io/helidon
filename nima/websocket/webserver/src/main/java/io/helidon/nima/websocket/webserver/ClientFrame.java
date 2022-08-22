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

import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;

/**
 * Frame from a client (always masked).
 */
class ClientFrame implements Frame {
    private final WsOpCode opCode;
    private final long payloadLength;
    private final BufferData data;
    private final boolean fin;
    private final int[] maskingKey;
    private final LazyValue<BufferData> unmasked;

    ClientFrame(WsOpCode opCode, long payloadLength, BufferData data, boolean fin, int[] maskingKey) {
        this.opCode = opCode;
        this.payloadLength = payloadLength;
        this.data = data;
        this.fin = fin;
        this.maskingKey = maskingKey;

        this.unmasked = LazyValue.create(this::unmask);
    }

    @Override
    public String toString() {
        return opCode + (fin ? " (last): \n" : ": \n") + unmasked.get().debugDataHex();
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
        return true;
    }

    @Override
    public long payloadLength() {
        return payloadLength;
    }

    @Override
    public int[] maskingKey() {
        return maskingKey;
    }

    @Override
    public BufferData payloadData() {
        return data;
    }

    public BufferData unmasked() {
        return unmasked.get();
    }

    private BufferData unmask() {
        int length = data.available();
        BufferData unmasked = BufferData.create(length);

        /*
        Octet i of the transformed data ("transformed-octet-i") is the XOR of
        octet i of the original data ("original-octet-i") with octet at index
        i modulo 4 of the masking key ("masking-key-octet-j"):

        j                   = i MOD 4
        transformed-octet-i = original-octet-i XOR masking-key-octet-j
         */
        for (int i = 0; i < length; i++) {
            int maskIndex = i % 4;
            int mask = maskingKey[maskIndex];
            unmasked.write(data.read() ^ mask);
        }

        return unmasked;
    }
}
