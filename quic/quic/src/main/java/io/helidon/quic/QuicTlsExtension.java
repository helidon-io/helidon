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

package io.helidon.quic;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

final class QuicTlsExtension {
    private final int type;
    private final byte[] data;

    private QuicTlsExtension(int type, byte[] data) {
        this.type = type;
        this.data = data.clone();
    }

    static QuicTlsExtension create(int type, byte[] data) {
        if (type < 0 || type > 0xFFFF) {
            throw new IllegalArgumentException("TLS extension type does not fit a 16-bit code point: " + type);
        }
        return new QuicTlsExtension(type, Objects.requireNonNull(data, "data"));
    }

    static QuicTlsExtension create(int type, ByteBuffer data) {
        return create(type, QuicTlsCodecSupport.copy(Objects.requireNonNull(data, "data")));
    }

    @Override
    public String toString() {
        return String.format("0x%04x[%d bytes]", type, data.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QuicTlsExtension that)) {
            return false;
        }
        return type == that.type && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return 31 * type + Arrays.hashCode(data);
    }

    int type() {
        return type;
    }

    byte[] data() {
        return data.clone();
    }

    ByteBuffer dataBuffer() {
        return ByteBuffer.wrap(data).asReadOnlyBuffer();
    }

    int encodedLength() {
        return 4 + data.length;
    }

    void encodeTo(ByteBuffer buffer) {
        buffer.putShort((short) type);
        QuicTlsCodecSupport.putVector(buffer, QuicTlsCodecSupport.UINT16_LENGTH, data, "extension_data");
    }
}
