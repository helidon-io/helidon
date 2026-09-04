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

final class QuicTlsKeyShareEntry {
    private final QuicTlsNamedGroup namedGroup;
    private final byte[] keyExchange;

    private QuicTlsKeyShareEntry(QuicTlsNamedGroup namedGroup, byte[] keyExchange) {
        this.namedGroup = Objects.requireNonNull(namedGroup, "namedGroup");
        this.keyExchange = keyExchange.clone();
    }

    static QuicTlsKeyShareEntry create(QuicTlsNamedGroup namedGroup, byte[] keyExchange) {
        Objects.requireNonNull(keyExchange, "keyExchange");
        if (keyExchange.length == 0) {
            throw new IllegalArgumentException("TLS key share entry requires non-empty key exchange data");
        }
        return new QuicTlsKeyShareEntry(namedGroup, keyExchange);
    }

    @Override
    public String toString() {
        return namedGroup.tlsName() + "[" + keyExchange.length + " bytes]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QuicTlsKeyShareEntry that)) {
            return false;
        }
        return namedGroup == that.namedGroup && Arrays.equals(keyExchange, that.keyExchange);
    }

    @Override
    public int hashCode() {
        return 31 * namedGroup.hashCode() + Arrays.hashCode(keyExchange);
    }

    QuicTlsNamedGroup namedGroup() {
        return namedGroup;
    }

    byte[] keyExchange() {
        return keyExchange.clone();
    }

    int encodedLength() {
        return 4 + keyExchange.length;
    }

    void encodeTo(ByteBuffer buffer) {
        buffer.putShort((short) namedGroup.codePoint());
        buffer.putShort((short) keyExchange.length);
        buffer.put(keyExchange);
    }
}
