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

import java.security.InvalidKeyException;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CRLReason;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.CertificateRevokedException;
import java.security.cert.PKIXReason;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.security.auth.x500.X500Principal;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuicTlsCertificateFailureTest {
    @Test
    void shouldMapRevokedCertificates() {
        CertificateRevokedException revoked = new CertificateRevokedException(new Date(0),
                                                                                CRLReason.KEY_COMPROMISE,
                                                                                new X500Principal("CN=issuer"),
                                                                                Map.of());
        List<Throwable> failures = List.of(
                revoked,
                new CertPathValidatorException("revoked", null, null, -1,
                                               CertPathValidatorException.BasicReason.REVOKED));

        for (Throwable failure : failures) {
            QuicTransportException translated = QuicTlsHandshakeMessages.certificateFailure("rejected", failure);

            assertThat(translated.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 44));
            assertThat(translated.getCause(), sameInstance(failure));
        }
    }

    @Test
    void shouldMapExpiredAndNotYetValidCertificates() {
        List<Throwable> failures = List.of(
                new CertificateExpiredException("expired"),
                new CertificateNotYetValidException("not yet valid"),
                new CertPathValidatorException("expired", null, null, -1,
                                               CertPathValidatorException.BasicReason.EXPIRED),
                new CertPathValidatorException("not yet valid", null, null, -1,
                                               CertPathValidatorException.BasicReason.NOT_YET_VALID));

        for (Throwable failure : failures) {
            QuicTransportException translated = QuicTlsHandshakeMessages.certificateFailure("rejected", failure);

            assertThat(translated.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 45));
            assertThat(translated.getCause(), sameInstance(failure));
        }
    }

    @Test
    void shouldMapMalformedCertificatesAndInvalidSignatures() {
        List<Throwable> failures = List.of(
                new CertificateParsingException("malformed"),
                new CertificateEncodingException("malformed"),
                new SignatureException("invalid signature"),
                new CertPathValidatorException("invalid signature", null, null, -1,
                                               CertPathValidatorException.BasicReason.INVALID_SIGNATURE));

        for (Throwable failure : failures) {
            QuicTransportException translated = QuicTlsHandshakeMessages.certificateFailure("rejected", failure);

            assertThat(translated.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 42));
            assertThat(translated.getCause(), sameInstance(failure));
        }
    }

    @Test
    void shouldMapMissingTrustAnchorsAndPathBuildFailures() {
        List<Throwable> failures = List.of(
                new CertPathBuilderException("path not found"),
                new CertPathValidatorException("trust anchor not found", null, null, -1,
                                               PKIXReason.NO_TRUST_ANCHOR));

        for (Throwable failure : failures) {
            QuicTransportException translated = QuicTlsHandshakeMessages.certificateFailure("rejected", failure);

            assertThat(translated.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 48));
            assertThat(translated.getCause(), sameInstance(failure));
        }
    }

    @Test
    void shouldMapOtherCertificateFailuresToCertificateUnknown() {
        List<Throwable> failures = List.of(
                new CertificateException("rejected"),
                new InvalidKeyException("ambiguous invalid key"),
                new CertPathValidatorException("algorithm constrained", null, null, -1,
                                               CertPathValidatorException.BasicReason.ALGORITHM_CONSTRAINED),
                new CertPathValidatorException("invalid policy", null, null, -1,
                                               PKIXReason.INVALID_POLICY));

        for (Throwable failure : failures) {
            QuicTransportException translated = QuicTlsHandshakeMessages.certificateFailure("rejected", failure);

            assertThat(translated.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 46));
            assertThat(translated.getCause(), sameInstance(failure));
        }
    }

    @Test
    void shouldTranslatePeerCertificateProviderFailures() {
        X509Certificate certificate = mock(X509Certificate.class);
        ProviderException publicKeyFailure = new ProviderException("public key provider failure");
        when(certificate.getPublicKey()).thenThrow(publicKeyFailure);

        QuicTransportException publicKeyThrown = assertThrows(
                QuicTransportException.class,
                () -> QuicTlsHandshakeMessages.peerCertificatePublicKey(certificate));

        assertThat(publicKeyThrown.errorCode(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
        assertThat(publicKeyThrown.getCause(), sameInstance(publicKeyFailure));

        X509Certificate algorithmCertificate = mock(X509Certificate.class);
        PublicKey publicKey = mock(PublicKey.class);
        ProviderException algorithmFailure = new ProviderException("algorithm provider failure");
        when(algorithmCertificate.getPublicKey()).thenReturn(publicKey);
        when(publicKey.getAlgorithm()).thenThrow(algorithmFailure);

        QuicTransportException algorithmThrown = assertThrows(
                QuicTransportException.class,
                () -> QuicTlsHandshakeMessages.peerCertificateAuthType(algorithmCertificate));

        assertThat(algorithmThrown.errorCode(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
        assertThat(algorithmThrown.getCause(), sameInstance(algorithmFailure));
    }

    @Test
    void shouldUseStructuredFailurePrecedenceAcrossCauseChain() {
        CertificateParsingException malformed = new CertificateParsingException("malformed");
        malformed.initCause(new CertificateExpiredException("expired"));
        CertificateException expiredChain = new CertificateException(
                "rejected",
                new CertPathBuilderException("path not found", malformed));

        QuicTransportException expired = QuicTlsHandshakeMessages.certificateFailure("rejected", expiredChain);

        assertThat(expired.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 45));
        assertThat(expired.getCause(), sameInstance(expiredChain));

        CertificateException badCertificateChain = new CertificateException(
                "rejected",
                new CertPathBuilderException("path not found", new CertificateParsingException("malformed")));

        QuicTransportException badCertificate =
                QuicTlsHandshakeMessages.certificateFailure("rejected", badCertificateChain);

        assertThat(badCertificate.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 42));
        assertThat(badCertificate.getCause(), sameInstance(badCertificateChain));

        CertificateRevokedException revoked = new CertificateRevokedException(new Date(0),
                                                                                CRLReason.KEY_COMPROMISE,
                                                                                new X500Principal("CN=issuer"),
                                                                                Map.of());
        CertificateExpiredException expiredWithRevokedCause = new CertificateExpiredException("expired");
        expiredWithRevokedCause.initCause(revoked);
        CertificateException revokedChain = new CertificateException("rejected", expiredWithRevokedCause);

        QuicTransportException revokedFailure = QuicTlsHandshakeMessages.certificateFailure("rejected", revokedChain);

        assertThat(revokedFailure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 44));
        assertThat(revokedFailure.getCause(), sameInstance(revokedChain));
    }
}
