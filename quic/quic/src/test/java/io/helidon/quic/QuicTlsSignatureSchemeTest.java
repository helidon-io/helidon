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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicTlsSignatureSchemeTest {
    @Test
    void shouldIgnoreUnsupportedSignatureSchemesInNegotiationVector() {
        List<QuicTlsSignatureScheme> signatureSchemes =
                QuicTlsSignatureScheme.decodeCertificateVerifyVector(
                        vector(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256.codePoint(),
                               QuicTlsSignatureScheme.RSA_PKCS1_SHA256.codePoint(),
                               0x0420,
                               0x1234,
                               QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256.codePoint(),
                               QuicTlsSignatureScheme.ED25519.codePoint()),
                        "supported_signature_algorithms",
                        "ClientHello");

        assertThat(signatureSchemes,
                   equalTo(List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256, QuicTlsSignatureScheme.ED25519)));
    }

    @Test
    void shouldReturnEmptyListWhenNegotiationVectorHasNoSupportedSchemes() {
        List<QuicTlsSignatureScheme> signatureSchemes =
                QuicTlsSignatureScheme.decodeCertificateVerifyVector(vector(0x1234, 0x5678),
                                                                     "supported_signature_algorithms",
                                                                     "ClientHello");

        assertThat(signatureSchemes, empty());
    }

    @Test
    void shouldKeepLegacyRsaOnlyInCertificateSignaturePolicy() {
        List<QuicTlsSignatureScheme> certificateVerifySchemes =
                QuicTlsSignatureScheme.certificateVerifySchemes(null);
        List<QuicTlsSignatureScheme> certificateSignatureSchemes =
                QuicTlsSignatureScheme.certificateSignatureSchemes(null);

        assertThat(certificateVerifySchemes, not(hasItem(QuicTlsSignatureScheme.RSA_PKCS1_SHA256)));
        assertThat(certificateSignatureSchemes, hasItem(QuicTlsSignatureScheme.RSA_PKCS1_SHA256));
    }

    @Test
    void shouldSplitConfiguredCertificateVerifyAndCertificateSignatureSchemes() {
        String[] configuredSchemes = {
                "rsa_pkcs1_sha256",
                "rsa_pss_rsae_sha256",
                "rsa_pkcs1_sha256"
        };

        assertThat(QuicTlsSignatureScheme.certificateVerifySchemes(configuredSchemes),
                   equalTo(List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)));
        assertThat(QuicTlsSignatureScheme.certificateSignatureSchemes(configuredSchemes),
                   equalTo(List.of(QuicTlsSignatureScheme.RSA_PKCS1_SHA256,
                                   QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)));
    }

    @Test
    void shouldRejectConfigurationWithoutTls13CertificateVerifyScheme() {
        assertThrows(IllegalArgumentException.class,
                     () -> QuicTlsSignatureScheme.certificateVerifySchemes(
                             new String[] {"rsa_pkcs1_sha256"}));
        assertThrows(IllegalArgumentException.class,
                     () -> QuicTlsSignatureScheme.certificateVerifySchemes(
                             new String[] {"rsa_pkcs1_sha256_legacy"}));
    }

    @Test
    void shouldSignCertificateVerifyWithRsaRsaPssEcdsaAndEdDsaKeys() throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        assertCertificateVerifyRoundTrip(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256, rsa.generateKeyPair());
        KeyPairGenerator rsaPss = KeyPairGenerator.getInstance("RSASSA-PSS");
        rsaPss.initialize(2048);
        assertCertificateVerifyRoundTrip(QuicTlsSignatureScheme.RSA_PSS_PSS_SHA256, rsaPss.generateKeyPair());
        assertCertificateVerifyRoundTrip(QuicTlsSignatureScheme.ECDSA_SECP256R1_SHA256,
                                         QuicTlsNamedGroup.SECP256_R1.generateKeyPair(new SecureRandom()));
        assertCertificateVerifyRoundTrip(QuicTlsSignatureScheme.ED25519,
                                         KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
    }

    @Test
    void shouldReportIncompatibleCandidateKeyAsUnsupported() {
        KeyPair keyPair = QuicTlsNamedGroup.SECP256_R1.generateKeyPair(new SecureRandom());

        assertThat(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256.supports(keyPair.getPrivate(), keyPair.getPublic()),
                   is(false));
    }

    @Test
    void shouldReportInvalidLocalSigningKeyAsInternalError() {
        KeyPair keyPair = QuicTlsNamedGroup.SECP256_R1.generateKeyPair(new SecureRandom());

        QuicTransportException thrown = assertThrows(
                QuicTransportException.class,
                () -> QuicTlsCertificateVerifyMessage.sign(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256,
                                                            keyPair.getPrivate(),
                                                            new byte[32],
                                                            false));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
    }

    @Test
    void shouldMatchCertificateChainRsaPssParameters() {
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           QuicTlsCertificateChainFixtures.chain("leaf-rsae-sha256", "rsae-root"),
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)),
                   is(true));
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           QuicTlsCertificateChainFixtures.chain("leaf-rsae-sha384", "rsae-root"),
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA384)),
                   is(true));
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           QuicTlsCertificateChainFixtures.chain("leaf-rsae-sha512", "rsae-root"),
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA512)),
                   is(true));

        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           QuicTlsCertificateChainFixtures.chain("leaf-rsae-sha256", "rsae-root"),
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA384)),
                   is(false));
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           QuicTlsCertificateChainFixtures.chain("leaf-rsae-mgf384", "rsae-root"),
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)),
                   is(false));
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           QuicTlsCertificateChainFixtures.chain("leaf-rsae-salt20", "rsae-root"),
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)),
                   is(false));
    }

    @Test
    void shouldValidateFinalCertificateWhenRootIsOmitted() {
        X509Certificate[] firstChain =
                QuicTlsCertificateChainFixtures.chain("leaf-rsae-sha256", "rsae-root");
        X509Certificate[] secondChain =
                QuicTlsCertificateChainFixtures.chain("leaf-rsae-pkcs1-sha256", "rsae-root");
        // Path validation is the trust manager's responsibility. This policy-only chain makes the final supplied
        // certificate a non-self-signed, PKCS#1-signed certificate whose issuer was omitted.
        X509Certificate[] rootOmittedChain = {firstChain[0], secondChain[0]};

        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           rootOmittedChain,
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)),
                   is(false));
    }

    @Test
    void shouldValidateSingleNonAnchorCertificate() {
        X509Certificate[] completeChain =
                QuicTlsCertificateChainFixtures.chain("leaf-rsae-pkcs1-sha256", "rsae-root");
        X509Certificate[] singleCertificateChain = {completeChain[0]};

        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           singleCertificateChain,
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)),
                   is(false));
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           singleCertificateChain,
                           List.of(QuicTlsSignatureScheme.RSA_PKCS1_SHA256)),
                   is(true));
    }

    @Test
    void shouldNotGuessRsaPssIssuerKeyTypeWhenIssuerIsOmitted() {
        X509Certificate[] rsaeChain =
                QuicTlsCertificateChainFixtures.chain("leaf-rsae-sha256", "rsae-root");
        X509Certificate[] pssChain =
                QuicTlsCertificateChainFixtures.chain("leaf-pss-sha256", "pss-root");
        X509Certificate[] rsaeRootOmitted = {rsaeChain[0]};
        X509Certificate[] pssRootOmitted = {pssChain[0]};

        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           rsaeRootOmitted,
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)),
                   is(false));
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           rsaeRootOmitted,
                           List.of(QuicTlsSignatureScheme.RSA_PSS_PSS_SHA256)),
                   is(false));
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           pssRootOmitted,
                           List.of(QuicTlsSignatureScheme.RSA_PSS_PSS_SHA256)),
                   is(false));
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           pssRootOmitted,
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)),
                   is(false));
    }

    @Test
    void shouldHonorRsaPssIssuerKeyParameters() {
        // The PSS root fixture restricts its public key to SHA-256. The second certificate's SHA-384 signature makes
        // that otherwise-compatible PSS issuer key unusable; path validation itself happens separately.
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           QuicTlsCertificateChainFixtures.chain("leaf-pss-sha256", "pss-root"),
                           List.of(QuicTlsSignatureScheme.RSA_PSS_PSS_SHA256)),
                   is(true));
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           QuicTlsCertificateChainFixtures.chain("leaf-rsae-sha384", "pss-root"),
                           List.of(QuicTlsSignatureScheme.RSA_PSS_PSS_SHA384)),
                   is(false));
    }

    @Test
    void shouldDistinguishRsaPssIssuerKeyOidsAndIgnoreTrustAnchorSignature() {
        // The RSAE trust anchor is self-signed with PKCS#1, but only the leaf-to-issuer link is constrained.
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           QuicTlsCertificateChainFixtures.chain("leaf-rsae-sha256", "rsae-root"),
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)),
                   is(true));
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           QuicTlsCertificateChainFixtures.chain("leaf-rsae-sha256", "rsae-root"),
                           List.of(QuicTlsSignatureScheme.RSA_PSS_PSS_SHA256)),
                   is(false));
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           QuicTlsCertificateChainFixtures.chain("leaf-pss-sha256", "pss-root"),
                           List.of(QuicTlsSignatureScheme.RSA_PSS_PSS_SHA256)),
                   is(true));
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           QuicTlsCertificateChainFixtures.chain("leaf-pss-sha256", "pss-root"),
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)),
                   is(false));

        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           QuicTlsCertificateChainFixtures.chain("leaf-rsae-pkcs1-sha256", "rsae-root"),
                           List.of(QuicTlsSignatureScheme.RSA_PKCS1_SHA256)),
                   is(true));
    }

    private static void assertCertificateVerifyRoundTrip(QuicTlsSignatureScheme signatureScheme, KeyPair keyPair) {
        byte[] transcriptHash = new byte[32];
        new SecureRandom().nextBytes(transcriptHash);
        QuicTlsCertificateVerifyMessage certificateVerify =
                QuicTlsCertificateVerifyMessage.sign(signatureScheme,
                                                      keyPair.getPrivate(),
                                                      transcriptHash,
                                                      false);

        assertThat(certificateVerify.verify(keyPair.getPublic(), transcriptHash, false), is(true));
    }

    private static ByteBuffer vector(int... codePoints) {
        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsCodecSupport.UINT16_LENGTH
                                                         + (QuicTlsCodecSupport.UINT16_LENGTH * codePoints.length));
        encoded.putShort((short) (QuicTlsCodecSupport.UINT16_LENGTH * codePoints.length));
        for (int codePoint : codePoints) {
            encoded.putShort((short) codePoint);
        }
        return encoded.flip();
    }
}
