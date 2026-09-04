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
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.security.auth.x500.X500Principal;

final class QuicTlsCertificateRequestMessage {
    private final byte[] requestContext;
    private final List<QuicTlsExtension> extensions;

    private QuicTlsCertificateRequestMessage(byte[] requestContext, List<QuicTlsExtension> extensions) {
        this.requestContext = Objects.requireNonNull(requestContext, "requestContext").clone();
        this.extensions = QuicTlsExtensions.copyOf(extensions);
    }

    static QuicTlsCertificateRequestMessage create(byte[] requestContext, List<QuicTlsExtension> extensions) {
        return new QuicTlsCertificateRequestMessage(requestContext, extensions);
    }

    static QuicTlsCertificateRequestMessage decode(ByteBuffer message) throws QuicTransportException {
        ByteBuffer body = QuicTlsCodecSupport.handshakeBody(message,
                                                            QuicTlsHandshakeMessages.CERTIFICATE_REQUEST,
                                                            "CertificateRequest");
        byte[] requestContext = QuicTlsCodecSupport.copy(QuicTlsCodecSupport.readVector(body,
                                                                                        QuicTlsCodecSupport.UINT8_LENGTH,
                                                                                        "certificate_request_context",
                                                                                        "CertificateRequest"));
        List<QuicTlsExtension> extensions = QuicTlsExtensions.decode(body, "CertificateRequest");
        QuicTlsCodecSupport.ensureConsumed(body, "CertificateRequest");
        return new QuicTlsCertificateRequestMessage(requestContext, extensions);
    }

    static byte[] encodeCertificateAuthorities(Principal[] authorities) {
        Principal[] principals = Objects.requireNonNull(authorities, "authorities").clone();
        if (principals.length == 0) {
            throw new IllegalArgumentException("At least one certificate authority is required");
        }

        List<byte[]> distinguishedNames = new ArrayList<>(principals.length);
        int authoritiesLength = 0;
        for (Principal authority : principals) {
            X500Principal x500Principal = authority instanceof X500Principal x500
                    ? x500
                    : new X500Principal(Objects.requireNonNull(authority, "authority").getName());
            byte[] distinguishedName = x500Principal.getEncoded();
            distinguishedNames.add(distinguishedName);
            authoritiesLength += QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT16_LENGTH,
                                                                         distinguishedName.length,
                                                                         "DistinguishedName");
        }

        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT16_LENGTH,
                                                                                         authoritiesLength,
                                                                                         "authorities"));
        QuicTlsCodecSupport.putUnsigned(encoded, QuicTlsCodecSupport.UINT16_LENGTH, authoritiesLength);
        for (byte[] distinguishedName : distinguishedNames) {
            QuicTlsCodecSupport.putVector(encoded,
                                          QuicTlsCodecSupport.UINT16_LENGTH,
                                          distinguishedName,
                                          "DistinguishedName");
        }
        return QuicTlsCodecSupport.copy(encoded.flip());
    }

    byte[] requestContext() {
        return requestContext.clone();
    }

    List<QuicTlsExtension> extensions() {
        return extensions;
    }

    List<QuicTlsSignatureScheme> signatureAlgorithms() throws QuicTransportException {
        return QuicTlsSignatureScheme.decodeCertificateVerifyVector(requiredSignatureAlgorithms().dataBuffer(),
                                                                    "signature_algorithms",
                                                                    "CertificateRequest");
    }

    List<QuicTlsSignatureScheme> certificateSignatureAlgorithms() throws QuicTransportException {
        QuicTlsExtension certificateSignatureAlgorithms = extension(QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT);
        if (certificateSignatureAlgorithms == null) {
            return List.of();
        }
        return QuicTlsSignatureScheme.decodeCertificateSignatureVector(certificateSignatureAlgorithms.dataBuffer(),
                                                                       "signature_algorithms_cert",
                                                                       "CertificateRequest");
    }

    List<QuicTlsSignatureScheme> effectiveCertificateSignatureAlgorithms() throws QuicTransportException {
        QuicTlsExtension certificateSignatureAlgorithms = extension(QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT);
        if (certificateSignatureAlgorithms != null) {
            return QuicTlsSignatureScheme.decodeCertificateSignatureVector(certificateSignatureAlgorithms.dataBuffer(),
                                                                           "signature_algorithms_cert",
                                                                           "CertificateRequest");
        }
        return QuicTlsSignatureScheme.decodeCertificateSignatureVector(requiredSignatureAlgorithms().dataBuffer(),
                                                                       "signature_algorithms",
                                                                       "CertificateRequest");
    }

    Principal[] certificateAuthorities() throws QuicTransportException {
        QuicTlsExtension certificateAuthorities = extension(QuicTlsExtensions.CERTIFICATE_AUTHORITIES);
        if (certificateAuthorities == null) {
            return new Principal[0];
        }

        ByteBuffer buffer = certificateAuthorities.dataBuffer();
        ByteBuffer authorities = QuicTlsCodecSupport.readVector(buffer,
                                                                QuicTlsCodecSupport.UINT16_LENGTH,
                                                                "authorities",
                                                                "certificate_authorities");
        QuicTlsCodecSupport.ensureConsumed(buffer, "certificate_authorities");

        List<Principal> principals = new ArrayList<>();
        while (authorities.hasRemaining()) {
            byte[] distinguishedName = QuicTlsCodecSupport.copy(QuicTlsCodecSupport.readVector(authorities,
                                                                                               QuicTlsCodecSupport.UINT16_LENGTH,
                                                                                               "DistinguishedName",
                                                                                               "certificate_authorities"));
            if (distinguishedName.length == 0) {
                throw QuicTlsHandshakeMessages.decodeError(
                        "Malformed certificate_authorities extension: empty DistinguishedName");
            }
            try {
                principals.add(new X500Principal(distinguishedName));
            } catch (IllegalArgumentException e) {
                throw QuicTlsHandshakeMessages.decodeError(
                        "Malformed certificate_authorities extension: invalid DistinguishedName");
            }
        }
        return principals.toArray(Principal[]::new);
    }

    ByteBuffer encode() {
        ByteBuffer encodedExtensions = QuicTlsExtensions.encode(extensions);
        int bodyLength = QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT8_LENGTH,
                                                                 requestContext.length,
                                                                 "certificate_request_context")
                + encodedExtensions.remaining();
        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsHandshakeMessages.HEADER_LENGTH + bodyLength);
        QuicTlsCodecSupport.putHandshakeHeader(encoded, QuicTlsHandshakeMessages.CERTIFICATE_REQUEST, bodyLength);
        QuicTlsCodecSupport.putVector(encoded,
                                      QuicTlsCodecSupport.UINT8_LENGTH,
                                      requestContext,
                                      "certificate_request_context");
        encoded.put(encodedExtensions.slice());
        return encoded.flip();
    }

    private QuicTlsExtension extension(int type) {
        return QuicTlsExtensions.find(extensions, type).orElse(null);
    }

    private QuicTlsExtension requiredSignatureAlgorithms() {
        QuicTlsExtension signatureAlgorithms = extension(QuicTlsExtensions.SIGNATURE_ALGORITHMS);
        if (signatureAlgorithms == null) {
            throw QuicTlsHandshakeMessages.missingExtension(
                    "CertificateRequest missing mandatory signature_algorithms extension");
        }
        return signatureAlgorithms;
    }
}
