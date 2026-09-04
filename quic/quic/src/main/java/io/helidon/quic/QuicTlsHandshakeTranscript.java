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
import java.security.MessageDigest;
import java.security.ProviderException;
import java.util.ArrayList;
import java.util.List;

final class QuicTlsHandshakeTranscript {
    // TLS 1.3 uses handshake type 254 for the synthetic message_hash record inserted after HelloRetryRequest.
    private static final int MESSAGE_HASH = 0xFE;

    private final List<byte[]> messages = new ArrayList<>();

    private int helloRetryRequestIndex = -1;

    void add(ByteBuffer message) {
        byte[] encoded = copy(message);
        requireCompleteMessage(encoded);
        if (isHelloRetryRequest(encoded)) {
            if (helloRetryRequestIndex != -1) {
                throw new IllegalArgumentException("Only one HelloRetryRequest is permitted in a transcript");
            }
            if (messages.size() != 1 || messageType(messages.getFirst()) != QuicTlsHandshakeMessages.CLIENT_HELLO) {
                throw new IllegalArgumentException("HelloRetryRequest requires a preceding ClientHello");
            }
            helloRetryRequestIndex = messages.size();
        }
        messages.add(encoded);
    }

    void reset() {
        messages.clear();
        helloRetryRequestIndex = -1;
    }

    byte[] hash(QuicTls13CipherSuite cipherSuite) {
        MessageDigest digest = cipherSuite.newDigest();
        try {
            if (helloRetryRequestIndex == -1) {
                messages.forEach(digest::update);
                return digest.digest();
            }

            if (helloRetryRequestIndex != 1) {
                throw new IllegalStateException("HelloRetryRequest must follow the initial ClientHello");
            }
            digest.update(syntheticMessageHash(cipherSuite.digest(messages.getFirst())));
            for (int i = helloRetryRequestIndex; i < messages.size(); i++) {
                digest.update(messages.get(i));
            }
            return digest.digest();
        } catch (ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to hash the TLS handshake transcript", e);
        }
    }

    private static int messageType(byte[] message) {
        return message[0] & 0xFF;
    }

    private static boolean isHelloRetryRequest(byte[] message) {
        return QuicTlsServerHelloMessage.isHelloRetryRequest(ByteBuffer.wrap(message));
    }

    private static byte[] syntheticMessageHash(byte[] messageHash) {
        byte[] result = new byte[QuicTlsHandshakeMessages.HEADER_LENGTH + messageHash.length];
        result[0] = (byte) MESSAGE_HASH;
        result[3] = (byte) messageHash.length;
        System.arraycopy(messageHash, 0, result, QuicTlsHandshakeMessages.HEADER_LENGTH, messageHash.length);
        return result;
    }

    private static void requireCompleteMessage(byte[] message) {
        if (message.length < QuicTlsHandshakeMessages.HEADER_LENGTH) {
            throw new IllegalArgumentException("Incomplete TLS handshake message header");
        }
        int bodyLength = ((message[1] & 0xFF) << 16)
                | ((message[2] & 0xFF) << 8)
                | (message[3] & 0xFF);
        if (message.length != QuicTlsHandshakeMessages.HEADER_LENGTH + bodyLength) {
            throw new IllegalArgumentException("Handshake message length does not match its header");
        }
    }

    private static byte[] copy(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        byte[] copy = new byte[duplicate.remaining()];
        duplicate.get(copy);
        return copy;
    }
}
