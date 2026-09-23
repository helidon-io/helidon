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
import java.security.cert.CertificateException;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicTlsCertificateMessagesTest {
    @Test
    void shouldRoundTripRfc8448CertificateMessage() throws Exception {
        QuicTlsCertificateMessage certificateMessage =
                QuicTlsCertificateMessage.decode(QuicTlsRfc8448Vectors.message(QuicTlsRfc8448Vectors.SIMPLE_SERVER_CERTIFICATE));

        assertThat(copy(certificateMessage.encode()),
                   equalTo(QuicTlsRfc8448Vectors.bytes(QuicTlsRfc8448Vectors.SIMPLE_SERVER_CERTIFICATE)));
        assertThat(certificateMessage.requestContext(), equalTo(new byte[0]));
        assertThat(certificateMessage.x509Certificates()[0].getSubjectX500Principal().getName(), containsString("CN=rsa"));
    }

    @Test
    void shouldRoundTripRfc8448CertificateVerifyAndFinishedMessages() throws Exception {
        QuicTlsCertificateVerifyMessage certificateVerify =
                QuicTlsCertificateVerifyMessage.decode(
                        QuicTlsRfc8448Vectors.message(QuicTlsRfc8448Vectors.SIMPLE_SERVER_CERTIFICATE_VERIFY));
        QuicTlsFinishedMessage serverFinished =
                QuicTlsFinishedMessage.decode(QuicTlsRfc8448Vectors.message(QuicTlsRfc8448Vectors.SIMPLE_SERVER_FINISHED));
        QuicTlsFinishedMessage clientFinished =
                QuicTlsFinishedMessage.decode(QuicTlsRfc8448Vectors.message(QuicTlsRfc8448Vectors.SIMPLE_CLIENT_FINISHED));

        assertThat(certificateVerify.signatureScheme(), is(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256));
        assertThat(copy(certificateVerify.encode()),
                   equalTo(QuicTlsRfc8448Vectors.bytes(QuicTlsRfc8448Vectors.SIMPLE_SERVER_CERTIFICATE_VERIFY)));
        assertThat(copy(serverFinished.encode()),
                   equalTo(QuicTlsRfc8448Vectors.bytes(QuicTlsRfc8448Vectors.SIMPLE_SERVER_FINISHED)));
        assertThat(copy(clientFinished.encode()),
                   equalTo(QuicTlsRfc8448Vectors.bytes(QuicTlsRfc8448Vectors.SIMPLE_CLIENT_FINISHED)));
    }

    @Test
    void shouldRoundTripCertificateRequestMessage() throws Exception {
        Principal authority = QuicTlsRfc8448Vectors.rsaCertificate().getSubjectX500Principal();
        QuicTlsCertificateRequestMessage certificateRequest = QuicTlsCertificateRequestMessage.decode(
                QuicTlsCertificateRequestMessage.create(
                                new byte[0],
                                List.of(
                                        QuicTlsExtension.create(QuicTlsExtensions.SIGNATURE_ALGORITHMS,
                                                                QuicTlsSignatureScheme.encodeCertificateVerifyVector(
                                                                        List.of(
                                                                                QuicTlsSignatureScheme
                                                                                        .RSA_PSS_RSAE_SHA256))),
                                        QuicTlsExtension.create(QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT,
                                                                QuicTlsSignatureScheme
                                                                        .encodeCertificateSignatureVector(List.of(
                                                                                QuicTlsSignatureScheme
                                                                                        .RSA_PKCS1_SHA256))),
                                        QuicTlsExtension.create(QuicTlsExtensions.CERTIFICATE_AUTHORITIES,
                                                                QuicTlsCertificateRequestMessage.encodeCertificateAuthorities(
                                                                        new Principal[] {authority}))))
                        .encode());

        assertThat(certificateRequest.requestContext(), equalTo(new byte[0]));
        assertThat(certificateRequest.signatureAlgorithms(),
                   equalTo(List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)));
        assertThat(certificateRequest.effectiveCertificateSignatureAlgorithms(),
                   equalTo(List.of(QuicTlsSignatureScheme.RSA_PKCS1_SHA256)));
        assertThat(certificateRequest.certificateAuthorities()[0].getName(), containsString("CN=rsa"));
    }

    @Test
    void shouldDecodeEmptyCertificateRequestExtensionsBeforeSemanticValidation() {
        byte[] encoded = QuicTlsRfc8448Vectors.bytes("0d000003000000");
        QuicTlsCertificateRequestMessage certificateRequest =
                QuicTlsCertificateRequestMessage.decode(ByteBuffer.wrap(encoded));

        assertThat(copy(certificateRequest.encode()), equalTo(encoded));
        assertThat(certificateRequest.requestContext(), equalTo(new byte[0]));
        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                                                     certificateRequest::signatureAlgorithms);
        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 109));
    }

    @Test
    void shouldApplyCertificateSignatureScopeWhenCertificateExtensionIsAbsent() {
        QuicTlsCertificateRequestMessage certificateRequest = QuicTlsCertificateRequestMessage.create(
                new byte[0],
                List.of(QuicTlsExtension.create(
                        QuicTlsExtensions.SIGNATURE_ALGORITHMS,
                        signatureVector(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256.codePoint(),
                                        QuicTlsSignatureScheme.RSA_PKCS1_SHA256.codePoint()))));

        assertThat(certificateRequest.signatureAlgorithms(),
                   equalTo(List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)));
        assertThat(certificateRequest.effectiveCertificateSignatureAlgorithms(),
                   equalTo(List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256,
                                   QuicTlsSignatureScheme.RSA_PKCS1_SHA256)));
    }

    @Test
    void shouldNotFallbackWhenCertificateSignatureExtensionHasNoSupportedSchemes() {
        QuicTlsCertificateRequestMessage certificateRequest = QuicTlsCertificateRequestMessage.create(
                new byte[0],
                List.of(QuicTlsExtension.create(
                                QuicTlsExtensions.SIGNATURE_ALGORITHMS,
                                signatureVector(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256.codePoint())),
                        QuicTlsExtension.create(QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT,
                                               signatureVector(0x1234))));

        assertThat(certificateRequest.effectiveCertificateSignatureAlgorithms(), empty());
    }

    @Test
    void shouldRejectEmptySignatureAlgorithmsInCertificateRequest() {
        QuicTlsCertificateRequestMessage certificateRequest = QuicTlsCertificateRequestMessage.decode(
                QuicTlsCertificateRequestMessage.create(
                                new byte[0],
                                List.of(QuicTlsExtension.create(QuicTlsExtensions.SIGNATURE_ALGORITHMS,
                                                               signatureVector())))
                        .encode());

        QuicTransportException thrown =
                assertThrows(QuicTransportException.class, certificateRequest::signatureAlgorithms);

        assertThat(thrown.reason(), containsString("signature_algorithms"));
        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 50));
    }

    @Test
    void shouldRejectEmptyCertificateSignatureAlgorithmsInCertificateRequest() {
        QuicTlsCertificateRequestMessage certificateRequest = QuicTlsCertificateRequestMessage.decode(
                QuicTlsCertificateRequestMessage.create(
                                new byte[0],
                                List.of(
                                        QuicTlsExtension.create(
                                                QuicTlsExtensions.SIGNATURE_ALGORITHMS,
                                                signatureVector(
                                                        QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256.codePoint())),
                                        QuicTlsExtension.create(QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT,
                                                               signatureVector())))
                        .encode());

        QuicTransportException thrown =
                assertThrows(QuicTransportException.class,
                             certificateRequest::effectiveCertificateSignatureAlgorithms);

        assertThat(thrown.reason(), containsString("signature_algorithms_cert"));
        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 50));
    }

    @Test
    void shouldRejectCertificateOnlySignatureSchemeInCertificateVerify() {
        QuicTransportException thrown = assertThrows(
                QuicTransportException.class,
                () -> QuicTlsCertificateVerifyMessage.decode(
                        certificateVerify(QuicTlsSignatureScheme.RSA_PKCS1_SHA256.codePoint())));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 47));
        assertThrows(IllegalArgumentException.class,
                     () -> QuicTlsCertificateVerifyMessage.create(QuicTlsSignatureScheme.RSA_PKCS1_SHA256,
                                                                  new byte[] {1}));
    }

    @Test
    void shouldRejectRfc9963LegacyClientSignatureScheme() {
        assertThrows(QuicTransportException.class,
                     () -> QuicTlsCertificateVerifyMessage.decode(certificateVerify(0x0420)));
    }

    @Test
    void shouldRejectInvalidCertificateVerifyAsDecryptError() {
        QuicTlsCertificateVerifyMessage certificateVerify =
                QuicTlsCertificateVerifyMessage.create(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256, new byte[] {1});

        QuicTransportException thrown = assertThrows(
                QuicTransportException.class,
                () -> certificateVerify.verify(QuicTlsRfc8448Vectors.rsaCertificate().getPublicKey(),
                                                new byte[32],
                                                false));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 51));
    }

    @Test
    void shouldRejectMalformedPeerCertificateAsBadCertificate() {
        QuicTlsCertificateMessage certificateMessage = QuicTlsCertificateMessage.create(
                new byte[0],
                List.of(new QuicTlsCertificateMessage.CertificateEntry(new byte[] {1, 2, 3}, List.of())));

        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                                                        certificateMessage::x509Certificates);

        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 42));
        assertThat(thrown.getCause(), instanceOf(CertificateException.class));
    }

    private static byte[] copy(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    private static ByteBuffer signatureVector(int... codePoints) {
        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsCodecSupport.UINT16_LENGTH
                                                         + (QuicTlsCodecSupport.UINT16_LENGTH * codePoints.length));
        encoded.putShort((short) (QuicTlsCodecSupport.UINT16_LENGTH * codePoints.length));
        for (int codePoint : codePoints) {
            encoded.putShort((short) codePoint);
        }
        return encoded.flip();
    }

    private static ByteBuffer certificateVerify(int codePoint) {
        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsHandshakeMessages.HEADER_LENGTH
                                                         + (QuicTlsCodecSupport.UINT16_LENGTH * 2)
                                                         + 1);
        QuicTlsCodecSupport.putHandshakeHeader(encoded,
                                               QuicTlsHandshakeMessages.CERTIFICATE_VERIFY,
                                               (QuicTlsCodecSupport.UINT16_LENGTH * 2) + 1);
        encoded.putShort((short) codePoint);
        encoded.putShort((short) 1);
        encoded.put((byte) 1);
        return encoded.flip();
    }
}
