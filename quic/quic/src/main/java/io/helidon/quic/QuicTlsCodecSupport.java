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
import java.util.Objects;

final class QuicTlsCodecSupport {
    static final int UINT8_LENGTH = 1;
    static final int UINT16_LENGTH = 2;
    // TLS ClientHello and ServerHello carry a fixed-width 32-byte random field, so the codec keeps that size
    // centralized instead of duplicating a magic number in every parser.
    static final int RANDOM_LENGTH = 32;

    private QuicTlsCodecSupport() {
    }

    static ByteBuffer handshakeBody(ByteBuffer message, int expectedType, String messageName) throws QuicTransportException {
        if (QuicTlsHandshakeMessages.messageType(message) != expectedType) {
            throw QuicTlsHandshakeMessages.decodeError("Expected " + messageName + " message");
        }
        return handshakeBody(message, messageName);
    }

    static ByteBuffer handshakeBody(ByteBuffer message, String messageName) throws QuicTransportException {
        ByteBuffer body = message.asReadOnlyBuffer();
        int messageLength = QuicTlsHandshakeMessages.messageBodyLength(body);
        if (body.remaining() != QuicTlsHandshakeMessages.HEADER_LENGTH + messageLength) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed " + messageName + " message");
        }

        body.position(body.position() + QuicTlsHandshakeMessages.HEADER_LENGTH);
        return body.slice();
    }

    static int readUnsigned(ByteBuffer buffer, int lengthBytes, String fieldName, String messageName)
            throws QuicTransportException {
        if (buffer.remaining() < lengthBytes) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed " + messageName + " message: " + fieldName);
        }

        return switch (lengthBytes) {
            case UINT8_LENGTH -> buffer.get() & 0xFF;
            case UINT16_LENGTH -> buffer.getShort() & 0xFFFF;
            case 3 -> ((buffer.get() & 0xFF) << 16)
                    | ((buffer.get() & 0xFF) << 8)
                    | (buffer.get() & 0xFF);
            default -> throw new IllegalArgumentException("Unsupported integer width: " + lengthBytes);
        };
    }

    static ByteBuffer readVector(ByteBuffer buffer, int lengthBytes, String fieldName, String messageName)
            throws QuicTransportException {
        int fieldLength = readUnsigned(buffer, lengthBytes, fieldName + "_length", messageName);
        if (buffer.remaining() < fieldLength) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed " + messageName + " message: " + fieldName);
        }

        ByteBuffer result = buffer.slice(buffer.position(), fieldLength);
        buffer.position(buffer.position() + fieldLength);
        return result;
    }

    static byte[] readFixed(ByteBuffer buffer, int length, String fieldName, String messageName)
            throws QuicTransportException {
        if (buffer.remaining() < length) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed " + messageName + " message: " + fieldName);
        }
        byte[] data = new byte[length];
        buffer.get(data);
        return data;
    }

    static void skip(ByteBuffer buffer, int length, String fieldName, String messageName)
            throws QuicTransportException {
        if (buffer.remaining() < length) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed " + messageName + " message: " + fieldName);
        }
        buffer.position(buffer.position() + length);
    }

    static void ensureConsumed(ByteBuffer buffer, String messageName) throws QuicTransportException {
        if (buffer.hasRemaining()) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed " + messageName + " message");
        }
    }

    static int encodedVectorLength(int lengthBytes, int dataLength, String fieldName) {
        requireEncodableLength(dataLength, lengthBytes, fieldName);
        return lengthBytes + dataLength;
    }

    static void putUnsigned(ByteBuffer buffer, int lengthBytes, int value) {
        if (value < 0 || value > maxUnsigned(lengthBytes)) {
            throw new IllegalArgumentException("Value does not fit " + (lengthBytes * 8) + "-bit unsigned integer: " + value);
        }

        switch (lengthBytes) {
        case UINT8_LENGTH -> buffer.put((byte) value);
        case UINT16_LENGTH -> buffer.putShort((short) value);
        case 3 -> {
            buffer.put((byte) ((value >>> 16) & 0xFF));
            buffer.put((byte) ((value >>> 8) & 0xFF));
            buffer.put((byte) (value & 0xFF));
        }
        default -> throw new IllegalArgumentException("Unsupported integer width: " + lengthBytes);
        }
    }

    static void putVector(ByteBuffer buffer, int lengthBytes, byte[] data, String fieldName) {
        byte[] copy = Objects.requireNonNull(data, fieldName).clone();
        requireEncodableLength(copy.length, lengthBytes, fieldName);
        putUnsigned(buffer, lengthBytes, copy.length);
        buffer.put(copy);
    }

    static void putHandshakeHeader(ByteBuffer buffer, int messageType, int bodyLength) {
        putUnsigned(buffer, UINT8_LENGTH, messageType);
        // TLS handshake headers carry the message body length as a uint24.
        putUnsigned(buffer, 3, bodyLength);
    }

    static byte[] copy(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        byte[] copy = new byte[duplicate.remaining()];
        duplicate.get(copy);
        return copy;
    }

    private static void requireEncodableLength(int dataLength, int lengthBytes, String fieldName) {
        if (dataLength < 0 || dataLength > maxUnsigned(lengthBytes)) {
            throw new IllegalArgumentException(fieldName + " exceeds the maximum vector length");
        }
    }

    private static int maxUnsigned(int lengthBytes) {
        return switch (lengthBytes) {
            case UINT8_LENGTH -> 0xFF;
            case UINT16_LENGTH -> 0xFFFF;
            case 3 -> 0xFF_FFFF;
            default -> throw new IllegalArgumentException("Unsupported integer width: " + lengthBytes);
        };
    }
}
