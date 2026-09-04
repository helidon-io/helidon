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

final class QuicTlsSupportedVersions {
    // TLS 1.3 is negotiated with supported_versions = 0x0304 even though the surrounding hello legacy_version fields
    // stay fixed at 0x0303 for backward compatibility.
    static final int TLS_1_3 = 0x0304;

    // RFC 8446 defines ClientHello supported_versions as versions<2..254>, so the list length is carried in one byte.
    private static final int CLIENT_HELLO_VECTOR_LENGTH_BYTES = QuicTlsCodecSupport.UINT8_LENGTH;

    private QuicTlsSupportedVersions() {
    }

    static ByteBuffer encodeClientHello(List<Integer> versions) {
        Objects.requireNonNull(versions, "versions");
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("supported_versions requires at least one version");
        }

        int versionsLength = 2 * versions.size();
        if (versionsLength > 0xFE) {
            throw new IllegalArgumentException("supported_versions exceeds the ClientHello length limit");
        }

        ByteBuffer encoded = ByteBuffer.allocate(CLIENT_HELLO_VECTOR_LENGTH_BYTES + versionsLength);
        encoded.put((byte) versionsLength);
        for (int version : versions) {
            encoded.putShort((short) requireUint16(version, "version"));
        }
        return encoded.flip();
    }

    static List<Integer> decodeClientHello(ByteBuffer extensionData) throws QuicTransportException {
        ByteBuffer buffer = extensionData.slice();
        if (buffer.remaining() < 3) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed supported_versions extension");
        }

        int versionsLength = buffer.get() & 0xFF;
        if (versionsLength < 2 || versionsLength != buffer.remaining() || (versionsLength & 1) != 0) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed supported_versions extension");
        }

        List<Integer> versions = new ArrayList<>();
        while (buffer.hasRemaining()) {
            versions.add(buffer.getShort() & 0xFFFF);
        }
        return List.copyOf(versions);
    }

    static ByteBuffer encodeServerHello(int selectedVersion) {
        ByteBuffer encoded = ByteBuffer.allocate(2);
        encoded.putShort((short) requireUint16(selectedVersion, "selectedVersion"));
        return encoded.flip();
    }

    static int decodeServerHello(ByteBuffer extensionData) throws QuicTransportException {
        ByteBuffer buffer = extensionData.slice();
        if (buffer.remaining() != 2) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed supported_versions extension");
        }
        return buffer.getShort() & 0xFFFF;
    }

    private static int requireUint16(int value, String fieldName) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException(fieldName + " does not fit a 16-bit unsigned integer");
        }
        return value;
    }
}
