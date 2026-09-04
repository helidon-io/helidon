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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class QuicTlsPskKeyExchangeModes {
    // RFC 8446 section 4.2.9 assigns psk_dhe_ke the value 1.
    static final int PSK_DHE_KE = 1;

    private QuicTlsPskKeyExchangeModes() {
    }

    static ByteBuffer encodeClientHello(List<Integer> modes) {
        Objects.requireNonNull(modes, "modes");
        if (modes.isEmpty() || modes.size() > 0xFF) {
            throw new IllegalArgumentException("psk_key_exchange_modes requires between 1 and 255 modes");
        }

        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsCodecSupport.UINT8_LENGTH + modes.size());
        encoded.put((byte) modes.size());
        for (int mode : modes) {
            if (mode < 0 || mode > 0xFF) {
                throw new IllegalArgumentException("PSK key exchange mode does not fit an 8-bit unsigned integer");
            }
            encoded.put((byte) mode);
        }
        return encoded.flip();
    }

    static List<Integer> decodeClientHello(ByteBuffer extensionData) throws QuicTransportException {
        ByteBuffer buffer = Objects.requireNonNull(extensionData, "extensionData").slice();
        if (buffer.remaining() < 2) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed psk_key_exchange_modes extension");
        }

        int modesLength = buffer.get() & 0xFF;
        if (modesLength == 0 || modesLength != buffer.remaining()) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed psk_key_exchange_modes extension");
        }

        List<Integer> modes = new ArrayList<>(modesLength);
        while (buffer.hasRemaining()) {
            modes.add(buffer.get() & 0xFF);
        }
        return List.copyOf(modes);
    }
}
