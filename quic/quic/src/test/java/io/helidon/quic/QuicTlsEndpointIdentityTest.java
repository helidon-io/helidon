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

import java.io.InputStream;
import java.security.KeyStore;
import java.security.ProviderException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class QuicTlsEndpointIdentityTest {
    private static X509Certificate[] sanChain;
    private static X509Certificate[] cnChain;
    private static X509ExtendedTrustManager jdkTrustManager;

    @BeforeAll
    static void setUpCertificates() throws Exception {
        sanChain = new X509Certificate[] {certificate("tls-identity-san.pem")};
        cnChain = new X509Certificate[] {certificate("tls-identity-cn.pem")};
        var anchors = KeyStore.getInstance(KeyStore.getDefaultType());
        anchors.load(null, null);
        anchors.setCertificateEntry("san", sanChain[0]);
        anchors.setCertificateEntry("cn", cnChain[0]);
        var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(anchors);
        jdkTrustManager = Arrays.stream(factory.getTrustManagers())
                .filter(X509ExtendedTrustManager.class::isInstance)
                .map(X509ExtendedTrustManager.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(strings = {"server.example.test", "SERVER.EXAMPLE.TEST", "192.0.2.10", "2001:db8::10",
            "2001:db8:0:0:0:0:0:10", "[2001:db8::10]"})
    void acceptsMatchingDnsAndIpIdentities(String host) throws Exception {
        var trustManager = new PlainTrustManager(jdkTrustManager);
        var engine = engine(host, "HTTPS");
        jdkTrustManager.checkServerTrusted(sanChain, "RSA", engine);

        QuicTlsManagerCallbacks.checkServerTrusted(trustManager, sanChain, "RSA", engine);

        assertAll(() -> assertThat(trustManager.serverChecks, is(1)),
                  () -> assertThat(trustManager.checkedChain, sameInstance(sanChain)),
                  () -> assertThat(trustManager.checkedAuthType, is("RSA")),
                  () -> assertThat(trustManager.acceptedIssuerReads, is(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong.example.test", "192.0.2.11", "2001:db8::11"})
    void rejectsTrustedCertificateForWrongDnsAndIpIdentities(String host) throws Exception {
        var trustManager = new PlainTrustManager(jdkTrustManager);
        var engine = engine(host, "HTTPS");
        // The same real certificate is trusted without endpoint identification.
        jdkTrustManager.checkServerTrusted(sanChain, "RSA");
        assertThrows(CertificateException.class, () -> jdkTrustManager.checkServerTrusted(sanChain, "RSA", engine));

        var failure = assertThrows(QuicTransportException.class,
                                   () -> QuicTlsManagerCallbacks.checkServerTrusted(trustManager, sanChain, "RSA", engine),
                                   "Trusted certificate must not authenticate a different peer: " + host);

        assertAll(() -> assertThat(failure.getCause(), instanceOf(CertificateException.class)),
                  () -> assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 46)),
                  () -> assertThat(trustManager.serverChecks, is(1)),
                  () -> assertThat(trustManager.acceptedIssuerReads, is(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTPS", "https", "HtTpS", "LDAP", "ldap", "LDAPS", "ldaps"})
    void supportsStandardIdentificationAlgorithms(String algorithm) throws Exception {
        var trustManager = new PlainTrustManager(jdkTrustManager);
        var engine = engine("server.example.test", algorithm);
        jdkTrustManager.checkServerTrusted(sanChain, "RSA", engine);

        QuicTlsManagerCallbacks.checkServerTrusted(trustManager, sanChain, "RSA", engine);

        assertThat(trustManager.serverChecks, is(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOM", " ", " HTTPS "})
    void rejectsUnknownIdentificationAlgorithms(String algorithm) {
        var trustManager = new PlainTrustManager(jdkTrustManager);

        assertCertificateFailure(trustManager, sanChain, engine("server.example.test", algorithm));

        assertThat(trustManager.serverChecks, is(1));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void disabledIdentificationPreservesChainOnlyPolicy(String algorithm) {
        var trustManager = new PlainTrustManager(jdkTrustManager);

        QuicTlsManagerCallbacks.checkServerTrusted(trustManager, sanChain, "RSA", engine("wrong.example.test", algorithm));

        assertAll(() -> assertThat(trustManager.serverChecks, is(1)),
                  () -> assertThat(trustManager.checkedChain, sameInstance(sanChain)),
                  () -> assertThat(trustManager.acceptedIssuerReads, is(0)));
    }

    @ParameterizedTest
    @CsvSource({"server.example.test, wrong.example.test", "wrong.example.test, server.example.test"})
    void acceptsMatchingSniOrPeerHost(String sni, String peerHost) throws Exception {
        var trustManager = new PlainTrustManager(jdkTrustManager);
        var engine = engine(peerHost, "HTTPS");
        var parameters = engine.getSSLParameters();
        parameters.setServerNames(List.of(new SNIHostName(sni)));
        engine.setSSLParameters(parameters);
        jdkTrustManager.checkServerTrusted(sanChain, "RSA", engine);

        QuicTlsManagerCallbacks.checkServerTrusted(trustManager, sanChain, "RSA", engine);

        assertThat(trustManager.serverChecks, is(1));
    }

    @Test
    void rejectsWhenNeitherSniNorPeerHostMatches() throws Exception {
        var trustManager = new PlainTrustManager(jdkTrustManager);
        var engine = engine("wrong.example.test", "HTTPS");
        var parameters = engine.getSSLParameters();
        parameters.setServerNames(List.of(new SNIHostName("other.example.test")));
        engine.setSSLParameters(parameters);
        assertThrows(CertificateException.class, () -> jdkTrustManager.checkServerTrusted(sanChain, "RSA", engine));

        assertCertificateFailure(trustManager, sanChain, engine);

        assertThat(trustManager.serverChecks, is(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"one.wild.example.test", "TWO.WILD.EXAMPLE.TEST"})
    void acceptsWildcardWithinOneDnsLabel(String host) throws Exception {
        var trustManager = new PlainTrustManager(jdkTrustManager);
        var engine = engine(host, "HTTPS");
        jdkTrustManager.checkServerTrusted(sanChain, "RSA", engine);

        QuicTlsManagerCallbacks.checkServerTrusted(trustManager, sanChain, "RSA", engine);

        assertThat(trustManager.serverChecks, is(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wild.example.test", "one.two.wild.example.test", "one.wild.example.test.invalid"})
    void rejectsWildcardOutsideOneDnsLabel(String host) {
        var trustManager = new PlainTrustManager(jdkTrustManager);
        var engine = engine(host, "HTTPS");
        assertThrows(CertificateException.class, () -> jdkTrustManager.checkServerTrusted(sanChain, "RSA", engine));

        assertCertificateFailure(trustManager, sanChain, engine);
    }

    @Test
    void dnsSubjectAlternativeNamesSuppressCommonNameFallback() {
        var engine = engine("cn.example.test", "HTTPS");
        assertThrows(CertificateException.class, () -> jdkTrustManager.checkServerTrusted(sanChain, "RSA", engine));

        assertCertificateFailure(new PlainTrustManager(jdkTrustManager), sanChain, engine);
    }

    @Test
    void acceptsCommonNameWhenDnsSubjectAlternativeNamesAreAbsent() throws Exception {
        var trustManager = new PlainTrustManager(jdkTrustManager);
        var engine = engine("cn.example.test", "HTTPS");
        jdkTrustManager.checkServerTrusted(cnChain, "RSA", engine);

        QuicTlsManagerCallbacks.checkServerTrusted(trustManager, cnChain, "RSA", engine);

        assertThat(trustManager.serverChecks, is(1));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsMissingPeerIdentity(String peerHost) {
        var trustManager = new PlainTrustManager(jdkTrustManager);

        assertCertificateFailure(trustManager, sanChain, engine(peerHost, "HTTPS"));
    }

    @Test
    void rejectsEmptyChainEvenWhenPlainManagerAcceptsIt() {
        assertCertificateFailure(mock(X509TrustManager.class), new X509Certificate[0], engine("server.example.test", "HTTPS"));
    }

    @Test
    void rejectsMissingLeafEvenWhenPlainManagerAcceptsIt() {
        assertCertificateFailure(mock(X509TrustManager.class),
                                 new X509Certificate[] {null},
                                 engine("server.example.test", "HTTPS"));
    }

    @Test
    void preservesExtendedManagerPolicyAndDeliversOriginalEngine() throws Exception {
        var trustManager = mock(X509ExtendedTrustManager.class);
        // An extended manager owns identity checking, including its own algorithm names.
        var engine = engine("wrong.example.test", "CUSTOM-EXTENDED");

        QuicTlsManagerCallbacks.checkServerTrusted(trustManager, sanChain, "RSA", engine);

        verify(trustManager).checkServerTrusted(sanChain, "RSA", engine);
        verifyNoMoreInteractions(trustManager);
    }

    @Test
    void preservesPlainManagerCertificateFailure() throws Exception {
        var delegate = mock(X509TrustManager.class);
        var rejection = new CertificateException("Custom chain policy rejected the server");
        doThrow(rejection).when(delegate).checkServerTrusted(sanChain, "RSA");
        var trustManager = new PlainTrustManager(delegate);

        var failure = assertThrows(QuicTransportException.class,
                                   () -> QuicTlsManagerCallbacks.checkServerTrusted(trustManager,
                                                                                    sanChain,
                                                                                    "RSA",
                                                                                    engine("server.example.test", "HTTPS")));

        assertAll(() -> assertThat(failure.getCause(), sameInstance(rejection)),
                  () -> assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 46)),
                  () -> assertThat(trustManager.serverChecks, is(1)),
                  () -> assertThat(trustManager.acceptedIssuerReads, is(0)));
    }

    @Test
    void preservesPlainManagerProviderFailure() throws Exception {
        var delegate = mock(X509TrustManager.class);
        var rejection = new ProviderException("Custom provider unavailable");
        doThrow(rejection).when(delegate).checkServerTrusted(sanChain, "RSA");
        var trustManager = new PlainTrustManager(delegate);

        var failure = assertThrows(QuicTransportException.class,
                                   () -> QuicTlsManagerCallbacks.checkServerTrusted(trustManager,
                                                                                    sanChain,
                                                                                    "RSA",
                                                                                    engine("server.example.test", "HTTPS")));

        assertAll(() -> assertThat(failure.getCause(), sameInstance(rejection)),
                  () -> assertThat(failure.errorCode(), is(QuicTransportErrors.INTERNAL_ERROR.code())),
                  () -> assertThat(trustManager.serverChecks, is(1)),
                  () -> assertThat(trustManager.acceptedIssuerReads, is(0)));
    }

    @Test
    void acceptsPeerHostWithTrailingDot() {
        var trustManager = new PlainTrustManager(jdkTrustManager);

        QuicTlsManagerCallbacks.checkServerTrusted(trustManager, sanChain, "RSA", engine("server.example.test.", "HTTPS"));

        assertThat(trustManager.serverChecks, is(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.0.2.10", "2001:db8::10"})
    void rejectsIpIdentityWithoutSubjectAlternativeIpAddress(String host) {
        assertCertificateFailure(new PlainTrustManager(jdkTrustManager), cnChain, engine(host, "HTTPS"));
    }

    @Test
    void rejectsEngineWithoutHandshakeSession() throws Exception {
        var trustManager = new PlainTrustManager(jdkTrustManager);
        var engine = unstartedEngine("HTTPS");
        assertThat(engine.getHandshakeSession(), nullValue());

        assertThrows(CertificateException.class,
                     () -> QuicTlsManagers.extended(trustManager).checkServerTrusted(sanChain, "RSA", engine));

        assertThat(trustManager.serverChecks, is(1));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void disabledIdentificationDoesNotRequireHandshakeSession(String algorithm) throws Exception {
        var trustManager = new PlainTrustManager(jdkTrustManager);
        var engine = unstartedEngine(algorithm);
        assertThat(engine.getHandshakeSession(), nullValue());

        QuicTlsManagers.extended(trustManager).checkServerTrusted(sanChain, "RSA", engine);

        assertThat(trustManager.serverChecks, is(1));
    }

    @Test
    void absentEnginePreservesChainOnlyCallback() throws Exception {
        var trustManager = new PlainTrustManager(jdkTrustManager);

        QuicTlsManagers.extended(trustManager).checkServerTrusted(sanChain, "RSA", (SSLEngine) null);

        assertAll(() -> assertThat(trustManager.serverChecks, is(1)),
                  () -> assertThat(trustManager.checkedChain, sameInstance(sanChain)),
                  () -> assertThat(trustManager.acceptedIssuerReads, is(0)));
    }

    @Test
    void translatesRealExtendedManagerWrongHostRejection() {
        assertCertificateFailure(jdkTrustManager, sanChain, engine("wrong.example.test", "HTTPS"));
    }

    @ParameterizedTest
    @CsvSource({"bücher.example.test, xn--bcher-kva.example.test",
            "xn--bcher-kva.example.test, bücher.example.test",
            "first.example.test, f*.example.test",
            "first-middle-last.example.test, f*middle*last.example.test"})
    void acceptsInternationalizedAndFirstLabelWildcardIdentities(String host, String certificateName) throws Exception {
        var trustManager = new PlainTrustManager(mock(X509TrustManager.class));
        var chain = dnsIdentityChain(certificateName);

        QuicTlsManagerCallbacks.checkServerTrusted(trustManager, chain, "RSA", engine(host, "HTTPS"));

        assertThat(trustManager.serverChecks, is(1));
    }

    @ParameterizedTest
    @CsvSource({"one.example.test, *.example.*", "one.two.example.test, one.*.example.test"})
    void rejectsWildcardsOutsideFirstLabel(String host, String certificateName) throws Exception {
        var trustManager = new PlainTrustManager(mock(X509TrustManager.class));

        assertCertificateFailure(trustManager, dnsIdentityChain(certificateName), engine(host, "HTTPS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OU=note\\,CN=wrong.example.test,CN=server.example.test,O=Example",
            "OU=unit+CN=server.example.test,O=Example",
            "CN=server.example.test,OU=unit,CN=wrong.example.test,O=Example"})
    void readsEscapedMultivaluedAndMostSpecificCommonNames(String subject) {
        var trustManager = new PlainTrustManager(mock(X509TrustManager.class));
        var chain = commonNameIdentityChain(subject);

        QuicTlsManagerCallbacks.checkServerTrusted(trustManager, chain, "RSA", engine("server.example.test", "HTTPS"));

        assertThat(trustManager.serverChecks, is(1));
    }

    @Test
    void doesNotMatchLessSpecificCommonName() {
        var chain = commonNameIdentityChain("CN=wrong.example.test,OU=unit,CN=server.example.test,O=Example");

        assertCertificateFailure(mock(X509TrustManager.class), chain, engine("server.example.test", "HTTPS"));
    }

    private static void assertCertificateFailure(X509TrustManager trustManager,
                                                 X509Certificate[] chain,
                                                 QuicTlsCallbackEngine engine) {
        var failure = assertThrows(QuicTransportException.class,
                                   () -> QuicTlsManagerCallbacks.checkServerTrusted(trustManager, chain, "RSA", engine));

        assertAll(() -> assertThat(failure.getCause(), instanceOf(CertificateException.class)),
                  () -> assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 46)));
    }

    private static SSLEngine unstartedEngine(String algorithm) throws Exception {
        var engine = SSLContext.getDefault().createSSLEngine("server.example.test", 443);
        engine.setUseClientMode(true);
        var parameters = engine.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm(algorithm);
        engine.setSSLParameters(parameters);
        return engine;
    }

    private static X509Certificate[] dnsIdentityChain(String dnsName) throws CertificateException {
        // Identity-only edge cases use the certificate's public API; trust regressions above use real certificates.
        var certificate = mock(X509Certificate.class);
        when(certificate.getSubjectAlternativeNames()).thenReturn(List.of(List.of(2, dnsName)));
        return new X509Certificate[] {certificate};
    }

    private static X509Certificate[] commonNameIdentityChain(String subject) {
        var certificate = mock(X509Certificate.class);
        when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal(subject));
        return new X509Certificate[] {certificate};
    }

    private static X509Certificate certificate(String name) throws Exception {
        try (InputStream input = Objects.requireNonNull(
                QuicTlsEndpointIdentityTest.class.getResourceAsStream("/io/helidon/quic/" + name), name)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
    }

    private static QuicTlsCallbackEngine engine(String host, String identificationAlgorithm) {
        var parameters = new SSLParameters(new String[] {"TLS_AES_128_GCM_SHA256"}, new String[] {"TLSv1.3"});
        parameters.setEndpointIdentificationAlgorithm(identificationAlgorithm);
        return QuicTlsManagerCallbacks.callbackEngine(true,
                                                       parameters,
                                                       new String[] {"rsa_pkcs1_sha256", "rsa_pss_rsae_sha256"},
                                                       new String[0],
                                                       host,
                                                       443);
    }

    private static final class PlainTrustManager implements X509TrustManager {
        private final X509TrustManager delegate;

        private int serverChecks;
        private int acceptedIssuerReads;
        private X509Certificate[] checkedChain;
        private String checkedAuthType;

        private PlainTrustManager(X509TrustManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            serverChecks++;
            checkedChain = chain;
            checkedAuthType = authType;
            delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            acceptedIssuerReads++;
            // The delegate's chain policy is authoritative, not this advisory issuer list.
            return new X509Certificate[0];
        }
    }
}
