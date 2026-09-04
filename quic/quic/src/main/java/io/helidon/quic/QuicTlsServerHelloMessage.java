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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

final class QuicTlsServerHelloMessage {
    private static final String MESSAGE_NAME = "ServerHello";

    // HelloRetryRequest reuses the ServerHello wire format and is identified solely by this RFC 8446 sentinel random.
    private static final byte[] HELLO_RETRY_REQUEST_RANDOM = new byte[] {
            (byte) 0xCF, (byte) 0x21, (byte) 0xAD, (byte) 0x74,
            (byte) 0xE5, (byte) 0x9A, (byte) 0x61, (byte) 0x11,
            (byte) 0xBE, (byte) 0x1D, (byte) 0x8C, (byte) 0x02,
            (byte) 0x1E, (byte) 0x65, (byte) 0xB8, (byte) 0x91,
            (byte) 0xC2, (byte) 0xA2, (byte) 0x11, (byte) 0x16,
            (byte) 0x7A, (byte) 0xBB, (byte) 0x8C, (byte) 0x5E,
            (byte) 0x07, (byte) 0x9E, (byte) 0x09, (byte) 0xE2,
            (byte) 0xC8, (byte) 0xA8, (byte) 0x33, (byte) 0x9C
    };

    private final int legacyVersion;
    private final byte[] random;
    private final byte[] legacySessionIdEcho;
    private final int cipherSuite;
    private final int legacyCompressionMethod;
    private final List<QuicTlsExtension> extensions;

    private QuicTlsServerHelloMessage(int legacyVersion,
                                      byte[] random,
                                      byte[] legacySessionIdEcho,
                                      int cipherSuite,
                                      int legacyCompressionMethod,
                                      List<QuicTlsExtension> extensions) {
        this.legacyVersion = requireUint16(legacyVersion, "legacyVersion");
        this.random = requireExactLength(random, QuicTlsCodecSupport.RANDOM_LENGTH, "random");
        this.legacySessionIdEcho = requireMaxLength(legacySessionIdEcho, 32, "legacySessionIdEcho");
        this.cipherSuite = requireUint16(cipherSuite, "cipherSuite");
        this.legacyCompressionMethod = requireUint8(legacyCompressionMethod, "legacyCompressionMethod");
        this.extensions = QuicTlsExtensions.copyOf(extensions);
    }

    static QuicTlsServerHelloMessage create(int legacyVersion,
                                            byte[] random,
                                            byte[] legacySessionIdEcho,
                                            int cipherSuite,
                                            int legacyCompressionMethod,
                                            List<QuicTlsExtension> extensions) {
        return new QuicTlsServerHelloMessage(legacyVersion,
                                             random,
                                             legacySessionIdEcho,
                                             cipherSuite,
                                             legacyCompressionMethod,
                                             extensions);
    }

    static QuicTlsServerHelloMessage helloRetryRequest(byte[] legacySessionIdEcho,
                                                       int cipherSuite,
                                                       List<QuicTlsExtension> extensions) {
        return new QuicTlsServerHelloMessage(0x0303,
                                             HELLO_RETRY_REQUEST_RANDOM,
                                             legacySessionIdEcho,
                                             cipherSuite,
                                             0,
                                             extensions);
    }

    static QuicTlsServerHelloMessage decode(ByteBuffer message) throws QuicTransportException {
        ByteBuffer body = QuicTlsCodecSupport.handshakeBody(message,
                                                            QuicTlsHandshakeMessages.SERVER_HELLO,
                                                            MESSAGE_NAME);

        int legacyVersion = QuicTlsCodecSupport.readUnsigned(body,
                                                             QuicTlsCodecSupport.UINT16_LENGTH,
                                                             "legacy_version",
                                                             MESSAGE_NAME);
        byte[] random = QuicTlsCodecSupport.readFixed(body,
                                                      QuicTlsCodecSupport.RANDOM_LENGTH,
                                                      "random",
                                                      MESSAGE_NAME);
        byte[] legacySessionIdEcho = QuicTlsCodecSupport.copy(QuicTlsCodecSupport.readVector(body,
                                                                                             QuicTlsCodecSupport.UINT8_LENGTH,
                                                                                             "legacy_session_id_echo",
                                                                                             MESSAGE_NAME));
        int cipherSuite = QuicTlsCodecSupport.readUnsigned(body,
                                                           QuicTlsCodecSupport.UINT16_LENGTH,
                                                           "cipher_suite",
                                                           MESSAGE_NAME);
        int legacyCompressionMethod = QuicTlsCodecSupport.readUnsigned(body,
                                                                       QuicTlsCodecSupport.UINT8_LENGTH,
                                                                       "legacy_compression_method",
                                                                       MESSAGE_NAME);
        List<QuicTlsExtension> extensions = QuicTlsExtensions.decode(body, MESSAGE_NAME);
        QuicTlsCodecSupport.ensureConsumed(body, MESSAGE_NAME);

        return create(legacyVersion,
                      random,
                      legacySessionIdEcho,
                      cipherSuite,
                      legacyCompressionMethod,
                      extensions);
    }

    static boolean isHelloRetryRequest(ByteBuffer message) {
        if (message.remaining() < QuicTlsHandshakeMessages.HEADER_LENGTH
                + QuicTlsCodecSupport.UINT16_LENGTH
                + QuicTlsCodecSupport.RANDOM_LENGTH) {
            return false;
        }
        if (QuicTlsHandshakeMessages.messageType(message) != QuicTlsHandshakeMessages.SERVER_HELLO) {
            return false;
        }

        int randomOffset = message.position()
                + QuicTlsHandshakeMessages.HEADER_LENGTH
                + QuicTlsCodecSupport.UINT16_LENGTH;
        for (int i = 0; i < HELLO_RETRY_REQUEST_RANDOM.length; i++) {
            if (message.get(randomOffset + i) != HELLO_RETRY_REQUEST_RANDOM[i]) {
                return false;
            }
        }
        return true;
    }

    int legacyVersion() {
        return legacyVersion;
    }

    byte[] random() {
        return random.clone();
    }

    byte[] legacySessionIdEcho() {
        return legacySessionIdEcho.clone();
    }

    int cipherSuite() {
        return cipherSuite;
    }

    int legacyCompressionMethod() {
        return legacyCompressionMethod;
    }

    List<QuicTlsExtension> extensions() {
        return extensions;
    }

    boolean helloRetryRequest() {
        return Arrays.equals(random, HELLO_RETRY_REQUEST_RANDOM);
    }

    Optional<QuicTlsExtension> extension(int type) {
        return QuicTlsExtensions.find(extensions, type);
    }

    OptionalInt supportedVersion() throws QuicTransportException {
        Optional<QuicTlsExtension> extension = extension(QuicTlsExtensions.SUPPORTED_VERSIONS);
        return extension.isPresent()
                ? OptionalInt.of(QuicTlsSupportedVersions.decodeServerHello(extension.get().dataBuffer()))
                : OptionalInt.empty();
    }

    Optional<QuicTlsKeyShareEntry> keyShare() throws QuicTransportException {
        if (helloRetryRequest()) {
            return Optional.empty();
        }
        Optional<QuicTlsExtension> extension = extension(QuicTlsExtensions.KEY_SHARE);
        return extension.isPresent()
                ? Optional.of(QuicTlsKeyShares.decodeServerHello(extension.get().dataBuffer()))
                : Optional.empty();
    }

    Optional<QuicTlsNamedGroup> helloRetryRequestSelectedGroup() throws QuicTransportException {
        if (!helloRetryRequest()) {
            return Optional.empty();
        }
        Optional<QuicTlsExtension> extension = extension(QuicTlsExtensions.KEY_SHARE);
        return extension.isPresent()
                ? Optional.of(QuicTlsKeyShares.decodeHelloRetryRequest(extension.get().dataBuffer()))
                : Optional.empty();
    }

    OptionalInt selectedIdentity() throws QuicTransportException {
        if (helloRetryRequest()) {
            return OptionalInt.empty();
        }
        Optional<QuicTlsExtension> extension = extension(QuicTlsExtensions.PRE_SHARED_KEY);
        return extension.isPresent()
                ? OptionalInt.of(QuicTlsPreSharedKeys.decodeServerHello(extension.get().dataBuffer()))
                : OptionalInt.empty();
    }

    ByteBuffer encode() {
        ByteBuffer encodedExtensions = QuicTlsExtensions.encode(extensions);
        int bodyLength = QuicTlsCodecSupport.UINT16_LENGTH
                + QuicTlsCodecSupport.RANDOM_LENGTH
                + QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT8_LENGTH,
                                                          legacySessionIdEcho.length,
                                                          "legacy_session_id_echo")
                + QuicTlsCodecSupport.UINT16_LENGTH
                + QuicTlsCodecSupport.UINT8_LENGTH
                + encodedExtensions.remaining();

        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsHandshakeMessages.HEADER_LENGTH + bodyLength);
        QuicTlsCodecSupport.putHandshakeHeader(encoded, QuicTlsHandshakeMessages.SERVER_HELLO, bodyLength);
        encoded.putShort((short) legacyVersion);
        encoded.put(random);
        QuicTlsCodecSupport.putVector(encoded,
                                      QuicTlsCodecSupport.UINT8_LENGTH,
                                      legacySessionIdEcho,
                                      "legacy_session_id_echo");
        encoded.putShort((short) cipherSuite);
        encoded.put((byte) legacyCompressionMethod);
        encoded.put(encodedExtensions.asReadOnlyBuffer());
        return encoded.flip();
    }

    private static byte[] requireExactLength(byte[] data, int expectedLength, String fieldName) {
        byte[] copy = Objects.requireNonNull(data, fieldName).clone();
        if (copy.length != expectedLength) {
            throw new IllegalArgumentException(fieldName + " must be " + expectedLength + " bytes");
        }
        return copy;
    }

    private static byte[] requireMaxLength(byte[] data, int maxLength, String fieldName) {
        byte[] copy = Objects.requireNonNull(data, fieldName).clone();
        if (copy.length > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds its maximum length");
        }
        return copy;
    }

    private static int requireUint16(int value, String fieldName) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException(fieldName + " does not fit a 16-bit unsigned integer");
        }
        return value;
    }

    private static int requireUint8(int value, String fieldName) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException(fieldName + " does not fit an 8-bit unsigned integer");
        }
        return value;
    }
}
