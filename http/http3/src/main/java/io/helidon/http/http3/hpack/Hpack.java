/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.http.http3.hpack;

import java.nio.ByteBuffer;

import io.helidon.common.buffers.BufferData;

final class Hpack {
    private Hpack() {
    }

    static int bytesForBits(int bitLength) {
        return bitLength == 0 ? 0 : ((bitLength - 1) / 8) + 1;
    }

    static int read(ByteBuffer source,
                    long buffer,
                    int bufferLen,
                    BufferUpdateConsumer consumer) {
        long data = buffer;
        int length = bufferLen;
        int count = Math.min((64 - length) >> 3, source.remaining());
        for (int i = 0; i < count; i++) {
            data |= ((source.get() & 0xffL) << (56 - length));
            length += 8;
        }
        if (count > 0) {
            consumer.accept(data, length);
        }
        return count;
    }

    static int read(BufferData source,
                    long buffer,
                    int bufferLen,
                    BufferUpdateConsumer consumer) {
        long data = buffer;
        int length = bufferLen;
        int count = Math.min((64 - length) >> 3, source.available());
        for (int i = 0; i < count; i++) {
            data |= ((source.read() & 0xffL) << (56 - length));
            length += 8;
        }
        if (count > 0) {
            consumer.accept(data, length);
        }
        return count;
    }

    static int write(long buffer,
                     int bufferLen,
                     BufferUpdateConsumer consumer,
                     ByteBuffer destination) {
        long data = buffer;
        int length = bufferLen;
        int count = Math.min(bytesForBits(length), destination.remaining());
        for (int i = 0; i < count; i++) {
            destination.put((byte) (data >>> 56));
            data <<= 8;
            length -= 8;
        }
        if (count > 0) {
            consumer.accept(data, Math.max(length, 0));
        }
        return count;
    }

    @FunctionalInterface
    interface BufferUpdateConsumer {
        void accept(long data, int len);
    }
}
