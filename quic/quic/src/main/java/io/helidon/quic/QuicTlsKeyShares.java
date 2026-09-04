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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class QuicTlsKeyShares {
    private QuicTlsKeyShares() {
    }

    static ByteBuffer encodeClientHello(List<QuicTlsKeyShareEntry> keyShares) {
        Objects.requireNonNull(keyShares, "keyShares");
        int listLength = 0;
        for (QuicTlsKeyShareEntry keyShare : keyShares) {
            listLength += Objects.requireNonNull(keyShare, "keyShare").encodedLength();
        }

        ByteBuffer encoded = ByteBuffer.allocate(2 + listLength);
        encoded.putShort((short) listLength);
        for (QuicTlsKeyShareEntry keyShare : keyShares) {
            keyShare.encodeTo(encoded);
        }
        return encoded.flip();
    }

    static List<QuicTlsKeyShareEntry> decodeClientHello(ByteBuffer extensionData) throws QuicTransportException {
        ByteBuffer buffer = extensionData.slice();
        if (buffer.remaining() < 2) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed key_share extension");
        }

        int listLength = buffer.getShort() & 0xFFFF;
        if (listLength != buffer.remaining()) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed key_share extension");
        }

        List<QuicTlsKeyShareEntry> keyShares = new ArrayList<>();
        Set<Integer> seenGroups = new HashSet<>();
        while (buffer.hasRemaining()) {
            DecodedEntry entry = decodeEntry(buffer);
            if (!seenGroups.add(entry.namedGroupCodePoint())) {
                throw QuicTlsHandshakeMessages.decodeError("Malformed key_share extension");
            }
            if (entry.keyShare() != null) {
                keyShares.add(entry.keyShare());
            }
        }
        return List.copyOf(keyShares);
    }

    static ByteBuffer encodeServerHello(QuicTlsKeyShareEntry keyShare) {
        Objects.requireNonNull(keyShare, "keyShare");
        ByteBuffer encoded = ByteBuffer.allocate(keyShare.encodedLength());
        keyShare.encodeTo(encoded);
        return encoded.flip();
    }

    static QuicTlsKeyShareEntry decodeServerHello(ByteBuffer extensionData) throws QuicTransportException {
        ByteBuffer buffer = extensionData.slice();
        DecodedEntry entry = decodeEntry(buffer);
        if (entry.keyShare() == null) {
            throw QuicTlsHandshakeMessages.decodeError("Unsupported TLS named group");
        }
        if (buffer.hasRemaining()) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed key_share extension");
        }
        return entry.keyShare();
    }

    static ByteBuffer encodeHelloRetryRequest(QuicTlsNamedGroup selectedGroup) {
        Objects.requireNonNull(selectedGroup, "selectedGroup");
        ByteBuffer encoded = ByteBuffer.allocate(2);
        encoded.putShort((short) selectedGroup.codePoint());
        return encoded.flip();
    }

    static QuicTlsNamedGroup decodeHelloRetryRequest(ByteBuffer extensionData) throws QuicTransportException {
        ByteBuffer buffer = extensionData.slice();
        if (buffer.remaining() != 2) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed key_share extension");
        }

        try {
            return QuicTlsNamedGroup.forCodePoint(buffer.getShort() & 0xFFFF);
        } catch (IllegalArgumentException e) {
            throw QuicTlsHandshakeMessages.decodeError("Unsupported TLS named group");
        }
    }

    private static DecodedEntry decodeEntry(ByteBuffer buffer) throws QuicTransportException {
        if (buffer.remaining() < 4) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed key_share extension");
        }

        int namedGroupCodePoint = buffer.getShort() & 0xFFFF;
        int keyExchangeLength = buffer.getShort() & 0xFFFF;
        if (keyExchangeLength == 0 || keyExchangeLength > buffer.remaining()) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed key_share extension");
        }

        byte[] keyExchange = new byte[keyExchangeLength];
        buffer.get(keyExchange);
        try {
            return new DecodedEntry(namedGroupCodePoint,
                                    QuicTlsKeyShareEntry.create(QuicTlsNamedGroup.forCodePoint(namedGroupCodePoint),
                                                                keyExchange));
        } catch (IllegalArgumentException e) {
            // ClientHello key_share vectors can legitimately contain groups outside Helidon's current public-JCA
            // implementation set. Keep the raw extension bytes for round-tripping and return only usable entries.
            return new DecodedEntry(namedGroupCodePoint, null);
        }
    }

    private record DecodedEntry(int namedGroupCodePoint, QuicTlsKeyShareEntry keyShare) {
    }
}
