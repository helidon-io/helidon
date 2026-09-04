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
import java.util.Optional;

final class QuicTlsClientHelloMessage {
    private static final String MESSAGE_NAME = "ClientHello";
    // Cipher suites are IANA-assigned 16-bit values, so the codec keeps their wire width explicit for sizing/parsing.
    private static final int CIPHER_SUITE_LENGTH = 2;

    private final int legacyVersion;
    private final byte[] random;
    private final byte[] legacySessionId;
    private final List<Integer> cipherSuites;
    private final byte[] legacyCompressionMethods;
    private final List<QuicTlsExtension> extensions;

    private QuicTlsClientHelloMessage(int legacyVersion,
                                      byte[] random,
                                      byte[] legacySessionId,
                                      List<Integer> cipherSuites,
                                      byte[] legacyCompressionMethods,
                                      List<QuicTlsExtension> extensions) {
        this.legacyVersion = requireUint16(legacyVersion, "legacyVersion");
        this.random = requireExactLength(random, QuicTlsCodecSupport.RANDOM_LENGTH, "random");
        this.legacySessionId = requireMaxLength(legacySessionId, 32, "legacySessionId");
        this.cipherSuites = copyCipherSuites(cipherSuites);
        this.legacyCompressionMethods = requireNonEmptyVector(legacyCompressionMethods, 0xFF, "legacyCompressionMethods");
        this.extensions = QuicTlsExtensions.copyOf(extensions);
    }

    static QuicTlsClientHelloMessage create(int legacyVersion,
                                            byte[] random,
                                            byte[] legacySessionId,
                                            List<Integer> cipherSuites,
                                            byte[] legacyCompressionMethods,
                                            List<QuicTlsExtension> extensions) {
        return new QuicTlsClientHelloMessage(legacyVersion,
                                             random,
                                             legacySessionId,
                                             cipherSuites,
                                             legacyCompressionMethods,
                                             extensions);
    }

    static QuicTlsClientHelloMessage decode(ByteBuffer message) throws QuicTransportException {
        ByteBuffer body = QuicTlsCodecSupport.handshakeBody(message,
                                                            QuicTlsHandshakeMessages.CLIENT_HELLO,
                                                            MESSAGE_NAME);

        int legacyVersion = QuicTlsCodecSupport.readUnsigned(body,
                                                             QuicTlsCodecSupport.UINT16_LENGTH,
                                                             "legacy_version",
                                                             MESSAGE_NAME);
        byte[] random = QuicTlsCodecSupport.readFixed(body,
                                                      QuicTlsCodecSupport.RANDOM_LENGTH,
                                                      "random",
                                                      MESSAGE_NAME);
        byte[] legacySessionId = QuicTlsCodecSupport.copy(QuicTlsCodecSupport.readVector(body,
                                                                                         QuicTlsCodecSupport.UINT8_LENGTH,
                                                                                         "legacy_session_id",
                                                                                         MESSAGE_NAME));

        ByteBuffer cipherSuitesBuffer = QuicTlsCodecSupport.readVector(body,
                                                                       QuicTlsCodecSupport.UINT16_LENGTH,
                                                                       "cipher_suites",
                                                                       MESSAGE_NAME);
        if (cipherSuitesBuffer.remaining() < CIPHER_SUITE_LENGTH || (cipherSuitesBuffer.remaining() & 1) != 0) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed ClientHello message: cipher_suites");
        }
        List<Integer> cipherSuites = new ArrayList<>();
        while (cipherSuitesBuffer.hasRemaining()) {
            cipherSuites.add(cipherSuitesBuffer.getShort() & 0xFFFF);
        }

        ByteBuffer compressionMethodsBuffer = QuicTlsCodecSupport.readVector(body,
                                                                             QuicTlsCodecSupport.UINT8_LENGTH,
                                                                             "legacy_compression_methods",
                                                                             MESSAGE_NAME);
        if (!compressionMethodsBuffer.hasRemaining()) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed ClientHello message: legacy_compression_methods");
        }
        byte[] legacyCompressionMethods = QuicTlsCodecSupport.copy(compressionMethodsBuffer);

        List<QuicTlsExtension> extensions = QuicTlsExtensions.decode(body, MESSAGE_NAME);
        QuicTlsCodecSupport.ensureConsumed(body, MESSAGE_NAME);

        return create(legacyVersion,
                      random,
                      legacySessionId,
                      cipherSuites,
                      legacyCompressionMethods,
                      extensions);
    }

    int legacyVersion() {
        return legacyVersion;
    }

    byte[] random() {
        return random.clone();
    }

    byte[] legacySessionId() {
        return legacySessionId.clone();
    }

    List<Integer> cipherSuites() {
        return cipherSuites;
    }

    byte[] legacyCompressionMethods() {
        return legacyCompressionMethods.clone();
    }

    List<QuicTlsExtension> extensions() {
        return extensions;
    }

    Optional<QuicTlsExtension> extension(int type) {
        return QuicTlsExtensions.find(extensions, type);
    }

    List<Integer> supportedVersions() throws QuicTransportException {
        Optional<QuicTlsExtension> extension = extension(QuicTlsExtensions.SUPPORTED_VERSIONS);
        return extension.isPresent()
                ? QuicTlsSupportedVersions.decodeClientHello(extension.get().dataBuffer())
                : List.of();
    }

    List<QuicTlsNamedGroup> supportedGroups() throws QuicTransportException {
        Optional<QuicTlsExtension> extension = extension(QuicTlsExtensions.SUPPORTED_GROUPS);
        return extension.isPresent()
                ? QuicTlsSupportedGroups.decode(extension.get().dataBuffer())
                : List.of();
    }

    List<QuicTlsKeyShareEntry> keyShares() throws QuicTransportException {
        Optional<QuicTlsExtension> extension = extension(QuicTlsExtensions.KEY_SHARE);
        return extension.isPresent()
                ? QuicTlsKeyShares.decodeClientHello(extension.get().dataBuffer())
                : List.of();
    }

    Optional<QuicTlsPreSharedKeys.OfferedPsks> offeredPreSharedKeys() throws QuicTransportException {
        Optional<QuicTlsExtension> extension = extension(QuicTlsExtensions.PRE_SHARED_KEY);
        return extension.isPresent()
                ? Optional.of(QuicTlsPreSharedKeys.decodeClientHello(extension.get().dataBuffer()))
                : Optional.empty();
    }

    ByteBuffer encode() {
        ByteBuffer encodedExtensions = QuicTlsExtensions.encode(extensions);
        int cipherSuitesLength = CIPHER_SUITE_LENGTH * cipherSuites.size();
        int bodyLength = QuicTlsCodecSupport.UINT16_LENGTH
                + QuicTlsCodecSupport.RANDOM_LENGTH
                + QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT8_LENGTH,
                                                          legacySessionId.length,
                                                          "legacy_session_id")
                + QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT16_LENGTH,
                                                          cipherSuitesLength,
                                                          "cipher_suites")
                + QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT8_LENGTH,
                                                          legacyCompressionMethods.length,
                                                          "legacy_compression_methods")
                + encodedExtensions.remaining();

        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsHandshakeMessages.HEADER_LENGTH + bodyLength);
        QuicTlsCodecSupport.putHandshakeHeader(encoded, QuicTlsHandshakeMessages.CLIENT_HELLO, bodyLength);
        encoded.putShort((short) legacyVersion);
        encoded.put(random);
        QuicTlsCodecSupport.putVector(encoded, QuicTlsCodecSupport.UINT8_LENGTH, legacySessionId, "legacy_session_id");
        QuicTlsCodecSupport.putUnsigned(encoded, QuicTlsCodecSupport.UINT16_LENGTH, cipherSuitesLength);
        for (int cipherSuite : cipherSuites) {
            encoded.putShort((short) cipherSuite);
        }
        QuicTlsCodecSupport.putVector(encoded,
                                      QuicTlsCodecSupport.UINT8_LENGTH,
                                      legacyCompressionMethods,
                                      "legacy_compression_methods");
        encoded.put(encodedExtensions.asReadOnlyBuffer());
        return encoded.flip();
    }

    private static List<Integer> copyCipherSuites(List<Integer> cipherSuites) {
        Objects.requireNonNull(cipherSuites, "cipherSuites");
        if (cipherSuites.isEmpty()) {
            throw new IllegalArgumentException("ClientHello requires at least one cipher suite");
        }

        List<Integer> copy = new ArrayList<>(cipherSuites.size());
        for (int cipherSuite : cipherSuites) {
            copy.add(requireUint16(cipherSuite, "cipherSuite"));
        }
        return List.copyOf(copy);
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

    private static byte[] requireNonEmptyVector(byte[] data, int maxLength, String fieldName) {
        byte[] copy = requireMaxLength(data, maxLength, fieldName);
        if (copy.length == 0) {
            throw new IllegalArgumentException(fieldName + " requires at least one byte");
        }
        return copy;
    }

    private static int requireUint16(int value, String fieldName) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException(fieldName + " does not fit a 16-bit unsigned integer");
        }
        return value;
    }
}
