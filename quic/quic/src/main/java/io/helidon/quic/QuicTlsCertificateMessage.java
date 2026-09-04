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

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.security.ProviderException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class QuicTlsCertificateMessage {
    private final byte[] requestContext;
    private final List<CertificateEntry> certificateEntries;

    private QuicTlsCertificateMessage(byte[] requestContext, List<CertificateEntry> certificateEntries) {
        this.requestContext = Objects.requireNonNull(requestContext, "requestContext").clone();
        this.certificateEntries = List.copyOf(Objects.requireNonNull(certificateEntries, "certificateEntries"));
    }

    static QuicTlsCertificateMessage create(byte[] requestContext, List<CertificateEntry> certificateEntries) {
        return new QuicTlsCertificateMessage(requestContext, certificateEntries);
    }

    static QuicTlsCertificateMessage decode(ByteBuffer message) throws QuicTransportException {
        ByteBuffer body = QuicTlsCodecSupport.handshakeBody(message, QuicTlsHandshakeMessages.CERTIFICATE, "Certificate");
        byte[] requestContext = QuicTlsCodecSupport.copy(QuicTlsCodecSupport.readVector(body,
                                                                                        QuicTlsCodecSupport.UINT8_LENGTH,
                                                                                        "certificate_request_context",
                                                                                        "Certificate"));
        ByteBuffer certificateList = QuicTlsCodecSupport.readVector(body, 3, "certificate_list", "Certificate");
        List<CertificateEntry> certificateEntries = new ArrayList<>();
        while (certificateList.hasRemaining()) {
            byte[] encodedCertificate = QuicTlsCodecSupport.copy(QuicTlsCodecSupport.readVector(certificateList,
                                                                                                3,
                                                                                                "cert_data",
                                                                                                "Certificate"));
            if (encodedCertificate.length == 0) {
                throw QuicTlsHandshakeMessages.decodeError("Malformed Certificate message: empty cert_data");
            }
            List<QuicTlsExtension> extensions = QuicTlsExtensions.decode(certificateList, "Certificate");
            certificateEntries.add(new CertificateEntry(encodedCertificate, extensions));
        }
        QuicTlsCodecSupport.ensureConsumed(body, "Certificate");
        return new QuicTlsCertificateMessage(requestContext, certificateEntries);
    }

    byte[] requestContext() {
        return requestContext.clone();
    }

    List<CertificateEntry> certificateEntries() {
        return certificateEntries;
    }

    X509Certificate[] x509Certificates() {
        CertificateFactory certificateFactory;
        try {
            certificateFactory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException | ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("X.509 certificate factory is not available", e);
        }
        X509Certificate[] certificates = new X509Certificate[certificateEntries.size()];
        for (int i = 0; i < certificateEntries.size(); i++) {
            try {
                certificates[i] = (X509Certificate) certificateFactory.generateCertificate(
                        new ByteArrayInputStream(certificateEntries.get(i).encodedCertificate()));
            } catch (CertificateException e) {
                throw QuicTlsHandshakeMessages.badCertificate("Failed to parse peer certificate " + (i + 1), e);
            } catch (ProviderException e) {
                throw QuicTlsHandshakeMessages.internalError("X.509 certificate provider failed", e);
            }
        }
        return certificates;
    }

    ByteBuffer encode() {
        int certificateListLength = 0;
        for (CertificateEntry certificateEntry : certificateEntries) {
            certificateListLength += certificateEntry.encodedLength();
        }
        int bodyLength = QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT8_LENGTH,
                                                                 requestContext.length,
                                                                 "certificate_request_context")
                + QuicTlsCodecSupport.encodedVectorLength(3, certificateListLength, "certificate_list");
        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsHandshakeMessages.HEADER_LENGTH + bodyLength);
        QuicTlsCodecSupport.putHandshakeHeader(encoded, QuicTlsHandshakeMessages.CERTIFICATE, bodyLength);
        QuicTlsCodecSupport.putVector(encoded,
                                      QuicTlsCodecSupport.UINT8_LENGTH,
                                      requestContext,
                                      "certificate_request_context");
        QuicTlsCodecSupport.putUnsigned(encoded, 3, certificateListLength);
        for (CertificateEntry certificateEntry : certificateEntries) {
            certificateEntry.encodeTo(encoded);
        }
        return encoded.flip();
    }

    record CertificateEntry(byte[] encodedCertificate, List<QuicTlsExtension> extensions) {
        CertificateEntry {
            encodedCertificate = Objects.requireNonNull(encodedCertificate, "encodedCertificate").clone();
            extensions = QuicTlsExtensions.copyOf(extensions);
        }

        @Override
        public byte[] encodedCertificate() {
            return encodedCertificate.clone();
        }

        @Override
        public List<QuicTlsExtension> extensions() {
            return extensions;
        }

        private int encodedLength() {
            return QuicTlsCodecSupport.encodedVectorLength(3, encodedCertificate.length, "cert_data")
                    + QuicTlsExtensions.encode(extensions).remaining();
        }

        private void encodeTo(ByteBuffer buffer) {
            QuicTlsCodecSupport.putVector(buffer, 3, encodedCertificate, "cert_data");
            buffer.put(QuicTlsExtensions.encode(extensions).slice());
        }
    }
}
