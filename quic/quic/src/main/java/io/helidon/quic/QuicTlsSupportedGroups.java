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

final class QuicTlsSupportedGroups {
    private QuicTlsSupportedGroups() {
    }

    static ByteBuffer encode(List<QuicTlsNamedGroup> namedGroups) {
        Objects.requireNonNull(namedGroups, "namedGroups");
        if (namedGroups.isEmpty()) {
            throw new IllegalArgumentException("TLS supported_groups extension requires at least one named group");
        }

        ByteBuffer encoded = ByteBuffer.allocate(2 + (2 * namedGroups.size()));
        encoded.putShort((short) (2 * namedGroups.size()));
        for (QuicTlsNamedGroup namedGroup : namedGroups) {
            encoded.putShort((short) Objects.requireNonNull(namedGroup, "namedGroup").codePoint());
        }
        return encoded.flip();
    }

    static List<QuicTlsNamedGroup> decode(ByteBuffer extensionData) throws QuicTransportException {
        ByteBuffer buffer = extensionData.slice();
        if (buffer.remaining() < 4) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed supported_groups extension");
        }

        int listLength = buffer.getShort() & 0xFFFF;
        if (listLength != buffer.remaining() || (listLength & 1) != 0) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed supported_groups extension");
        }

        List<QuicTlsNamedGroup> namedGroups = new ArrayList<>();
        while (buffer.hasRemaining()) {
            int codePoint = buffer.getShort() & 0xFFFF;
            try {
                namedGroups.add(QuicTlsNamedGroup.forCodePoint(codePoint));
            } catch (IllegalArgumentException e) {
                // Peers can advertise groups Helidon does not implement yet. Keep the actionable subset here while
                // the enclosing hello-message codec retains the raw extension bytes for exact round-tripping.
            }
        }
        return List.copyOf(namedGroups);
    }
}
