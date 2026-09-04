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
import java.util.Optional;
import java.util.Set;

final class QuicTlsExtensions {
    // SNI uses extension type 0, so the public ClientHello path needs the explicit code point to advertise peer names
    // without reaching into JSSE's internal extension registry.
    static final int SERVER_NAME = 0x0000;
    // RFC 8446 assigns supported_groups the extension type 10; the explicit code point lets Helidon construct and
    // parse the wire format without depending on JDK-internal TLS registries.
    static final int SUPPORTED_GROUPS = 0x000A;
    // TLS 1.3 certificate authentication depends on signature_algorithms, and the public ClientHello path needs the
    // stable IANA code point so it can advertise JSSE-selected verifier schemes itself.
    static final int SIGNATURE_ALGORITHMS = 0x000D;
    // QUIC endpoints negotiate their application protocol with ALPN, so the Helidon-owned ClientHello path keeps the
    // extension code point locally instead of depending on internal JSSE enum constants.
    static final int APPLICATION_LAYER_PROTOCOL_NEGOTIATION = 0x0010;
    // TLS padding can change across a HelloRetryRequest without changing the effective ClientHello offer.
    static final int PADDING = 0x0015;
    // TLS 1.3 PSK resumption uses pre_shared_key in ClientHello / ServerHello, including the binder that ties the
    // cached resumption secret to the current handshake transcript.
    static final int PRE_SHARED_KEY = 0x0029;
    // HelloRetryRequest carries a cookie extension when the server wants the retried ClientHello to prove reachability.
    static final int COOKIE = 0x002C;
    // QUIC repurposes TLS early_data in NewSessionTicket and EncryptedExtensions when negotiating 0-RTT. Helidon does
    // not advertise or accept 0-RTT; its server rejects a valid offer by omitting early_data from EncryptedExtensions.
    static final int EARLY_DATA = 0x002A;
    // Helidon's QUIC resumption path only supports the PSK + (EC)DHE mode, so the public ClientHello path keeps the
    // standard extension code point locally instead of depending on JSSE's internal registries.
    static final int PSK_KEY_EXCHANGE_MODES = 0x002D;
    // TLS 1.3 version negotiation happens in supported_versions while legacy_version stays pinned to 0x0303.
    static final int SUPPORTED_VERSIONS = 0x002B;
    // TLS 1.3 CertificateRequest can constrain acceptable client-certificate issuers via certificate_authorities.
    static final int CERTIFICATE_AUTHORITIES = 0x002F;
    // TLS 1.3 CertificateRequest may separately constrain certificate-signature algorithms with
    // signature_algorithms_cert, so Helidon keeps the IANA code point locally until the JDK QUIC engine can own mTLS.
    static final int SIGNATURE_ALGORITHMS_CERT = 0x0032;
    // QUIC depends on key_share contents to derive handshake/application packet-protection keys on public JCA APIs.
    static final int KEY_SHARE = 0x0033;
    // QUIC transport parameters ride on TLS extension type 57 in ClientHello / EncryptedExtensions.
    static final int QUIC_TRANSPORT_PARAMETERS = 0x0039;

    private QuicTlsExtensions() {
    }

    static ByteBuffer encode(List<QuicTlsExtension> extensions) {
        List<QuicTlsExtension> normalized = copyOf(extensions);
        int totalLength = 0;
        for (QuicTlsExtension extension : normalized) {
            totalLength += extension.encodedLength();
        }

        ByteBuffer encoded = ByteBuffer.allocate(
                QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT16_LENGTH,
                                                        totalLength,
                                                        "extensions"));
        encoded.putShort((short) totalLength);
        for (QuicTlsExtension extension : normalized) {
            extension.encodeTo(encoded);
        }
        return encoded.flip();
    }

    static List<QuicTlsExtension> decode(ByteBuffer buffer, String messageName) throws QuicTransportException {
        return decodeEntries(QuicTlsCodecSupport.readVector(buffer,
                                                            QuicTlsCodecSupport.UINT16_LENGTH,
                                                            "extensions",
                                                            messageName),
                             messageName);
    }

    static Optional<QuicTlsExtension> find(List<QuicTlsExtension> extensions, int type) {
        Objects.requireNonNull(extensions, "extensions");
        for (QuicTlsExtension extension : extensions) {
            if (extension.type() == type) {
                return Optional.of(extension);
            }
        }
        return Optional.empty();
    }

    static List<QuicTlsExtension> copyOf(List<QuicTlsExtension> extensions) {
        Objects.requireNonNull(extensions, "extensions");
        List<QuicTlsExtension> copy = new ArrayList<>(extensions.size());
        Set<Integer> seenTypes = new HashSet<>();
        for (QuicTlsExtension extension : extensions) {
            QuicTlsExtension candidate = Objects.requireNonNull(extension, "extension");
            if (!seenTypes.add(candidate.type())) {
                throw new IllegalArgumentException(String.format("Duplicate TLS extension: 0x%04x", candidate.type()));
            }
            copy.add(candidate);
        }
        return List.copyOf(copy);
    }

    private static List<QuicTlsExtension> decodeEntries(ByteBuffer extensionBlock, String messageName)
            throws QuicTransportException {
        List<QuicTlsExtension> extensions = new ArrayList<>();
        Set<Integer> seenTypes = new HashSet<>();
        while (extensionBlock.hasRemaining()) {
            int type = QuicTlsCodecSupport.readUnsigned(extensionBlock,
                                                        QuicTlsCodecSupport.UINT16_LENGTH,
                                                        "extension_type",
                                                        messageName);
            ByteBuffer extensionData = QuicTlsCodecSupport.readVector(extensionBlock,
                                                                      QuicTlsCodecSupport.UINT16_LENGTH,
                                                                      "extension_data",
                                                                      messageName);
            if (!seenTypes.add(type)) {
                throw QuicTlsHandshakeMessages.decodeError(
                        String.format("Malformed %s message: duplicate extension 0x%04x", messageName, type));
            }
            extensions.add(QuicTlsExtension.create(type, extensionData));
        }
        return List.copyOf(extensions);
    }
}
