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

import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuicTlsManagerCallbacksTest {
    @Test
    void exposesHandshakeContextToExtendedKeyManagers() {
        RecordingExtendedKeyManager keyManager = new RecordingExtendedKeyManager();
        QuicTlsCallbackEngine callbackEngine =
                QuicTlsManagerCallbacks.callbackEngine(true,
                                                        sslParameters(),
                                                        new String[] {"rsa_pss_rsae_sha256"},
                                                        new String[] {"rsa_pkcs1_sha256"},
                                                        "example.com",
                                                        443);

        String alias = QuicTlsManagerCallbacks.chooseClientAlias(keyManager,
                                                                 new String[] {"RSA"},
                                                                 new Principal[0],
                                                                 callbackEngine);

        assertThat(alias, is("client-alias"));
        assertThat(keyManager.engine, notNullValue());
        assertThat(keyManager.engine.getPeerHost(), is("example.com"));
        assertThat(keyManager.engine.getPeerPort(), is(443));
        assertThat(keyManager.engine.getUseClientMode(), is(true));
        assertThat(keyManager.engine.getSSLParameters().getEndpointIdentificationAlgorithm(), is("HTTPS"));
        ExtendedSSLSession session = (ExtendedSSLSession) keyManager.engine.getHandshakeSession();
        assertThat(session.getRequestedServerNames().size(), is(1));
        assertThat(session.getRequestedServerNames().get(0), instanceOf(SNIHostName.class));
        assertThat(((SNIHostName) session.getRequestedServerNames().get(0)).getAsciiName(), is("example.com"));
        assertThat(session.getLocalSupportedSignatureAlgorithms(), arrayContaining("RSASSA-PSS"));
        assertThat(session.getPeerSupportedSignatureAlgorithms(), arrayContaining("SHA256withRSA"));
    }

    @Test
    void exposesHandshakeContextToExtendedTrustManagers() {
        RecordingExtendedTrustManager trustManager = new RecordingExtendedTrustManager();
        QuicTlsCallbackEngine callbackEngine =
                QuicTlsManagerCallbacks.callbackEngine(true,
                                                        sslParameters(),
                                                        new String[] {"rsa_pss_rsae_sha256"},
                                                        new String[0],
                                                        "example.com",
                                                        443);

        QuicTlsManagerCallbacks.checkServerTrusted(trustManager,
                                                   new X509Certificate[0],
                                                   "RSA",
                                                   callbackEngine);

        assertThat(trustManager.engine, notNullValue());
        assertThat(trustManager.engine.getPeerHost(), is("example.com"));
        assertThat(trustManager.engine.getUseClientMode(), is(true));
        ExtendedSSLSession session = (ExtendedSSLSession) trustManager.engine.getHandshakeSession();
        assertThat(session.getRequestedServerNames().size(), is(1));
        assertThat(((SNIHostName) session.getRequestedServerNames().get(0)).getAsciiName(), is("example.com"));
        assertThat(session.getLocalSupportedSignatureAlgorithms(), arrayContaining("RSASSA-PSS"));
        assertThat(session.getPeerSupportedSignatureAlgorithms(), is(new String[0]));
    }

    @Test
    void adaptsLegacyManagersForEngineCallbacks() {
        LegacyKeyManager keyManager = new LegacyKeyManager();
        LegacyTrustManager trustManager = new LegacyTrustManager();
        QuicTlsCallbackEngine serverEngine =
                QuicTlsManagerCallbacks.callbackEngine(false,
                                                        sslParameters(),
                                                        new String[] {"rsa_pss_rsae_sha256"},
                                                        new String[] {"rsa_pss_rsae_sha256"},
                                                        "example.com",
                                                        443);
        QuicTlsCallbackEngine clientEngine =
                QuicTlsManagerCallbacks.callbackEngine(true,
                                                        sslParameters(),
                                                        new String[] {"rsa_pss_rsae_sha256"},
                                                        new String[0],
                                                        "example.com",
                                                        443);

        String alias = QuicTlsManagerCallbacks.chooseServerAlias(keyManager,
                                                                 "RSA",
                                                                 new Principal[0],
                                                                 serverEngine);
        QuicTlsManagerCallbacks.checkServerTrusted(trustManager,
                                                   new X509Certificate[0],
                                                   "RSA",
                                                   clientEngine);

        assertThat(alias, is("legacy-server"));
        assertThat(keyManager.chooseServerAliasCalls, is(1));
        assertThat(trustManager.checkServerTrustedCalls, is(1));
    }

    @Test
    void exposesConvertedPeerSignatureAlgorithmsToJdkKeyManagers() throws Exception {
        X509KeyManager keyManager = jdkKeyManager();
        QuicTlsCallbackEngine callbackEngine =
                QuicTlsManagerCallbacks.callbackEngine(false,
                                                        serverAliasSslParameters("rsa_pkcs1_sha256"),
                                                        new String[] {"rsa_pkcs1_sha256"},
                                                        new String[] {"rsa_pkcs1_sha256"},
                                                        null,
                                                        -1);

        String alias = QuicTlsManagerCallbacks.chooseServerAlias(keyManager,
                                                                 "RSA",
                                                                 new Principal[0],
                                                                 callbackEngine);

        assertThat(alias, notNullValue());
        assertThat(keyManager.getCertificateChain(alias)[0].getPublicKey().getAlgorithm(), is("RSA"));
    }

    @Test
    void translatesCertificateFailureFromServerTrustManager() {
        CertificateException failure = new CertificateException("untrusted server certificate");
        QuicTlsCallbackEngine callbackEngine =
                QuicTlsManagerCallbacks.callbackEngine(true,
                                                        sslParameters(),
                                                        new String[0],
                                                        new String[0],
                                                        "example.com",
                                                        443);

        QuicTransportException thrown = assertThrows(
                QuicTransportException.class,
                () -> QuicTlsManagerCallbacks.checkServerTrusted(new FailingExtendedTrustManager(failure),
                                                                  new X509Certificate[0],
                                                                  "RSA",
                                                                  callbackEngine));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 46));
        assertThat(thrown.getCause(), sameInstance(failure));
    }

    @Test
    void translatesProviderFailureFromClientTrustManager() {
        ProviderException failure = new ProviderException("provider failure");
        QuicTlsCallbackEngine callbackEngine =
                QuicTlsManagerCallbacks.callbackEngine(false,
                                                        sslParameters(),
                                                        new String[0],
                                                        new String[0],
                                                        "example.com",
                                                        443);

        QuicTransportException thrown = assertThrows(
                QuicTransportException.class,
                () -> QuicTlsManagerCallbacks.checkClientTrusted(new FailingExtendedTrustManager(failure),
                                                                  new X509Certificate[0],
                                                                  "RSA",
                                                                  callbackEngine));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
        assertThat(thrown.getCause(), sameInstance(failure));
    }

    @Test
    void translatesProviderFailureFromKeyManagerAliasSelection() {
        X509ExtendedKeyManager keyManager = mock(X509ExtendedKeyManager.class);
        ProviderException failure = new ProviderException("alias provider failure");
        when(keyManager.chooseEngineClientAlias(any(), any(), any())).thenThrow(failure);
        QuicTlsCallbackEngine callbackEngine =
                QuicTlsManagerCallbacks.callbackEngine(true,
                                                        sslParameters(),
                                                        new String[0],
                                                        new String[0],
                                                        "example.com",
                                                        443);

        QuicTransportException thrown = assertThrows(
                QuicTransportException.class,
                () -> QuicTlsManagerCallbacks.chooseClientAlias(keyManager,
                                                                 new String[] {"RSA"},
                                                                 new Principal[0],
                                                                 callbackEngine));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
        assertThat(thrown.getCause(), sameInstance(failure));
    }

    @Test
    void rejectsCallbackEngineModeMismatch() {
        QuicTlsCallbackEngine serverEngine =
                QuicTlsManagerCallbacks.callbackEngine(false, sslParameters(), new String[0], new String[0], null, -1);
        QuicTlsCallbackEngine clientEngine =
                QuicTlsManagerCallbacks.callbackEngine(true, sslParameters(), new String[0], new String[0], null, -1);

        assertThrows(IllegalArgumentException.class,
                     () -> QuicTlsManagerCallbacks.chooseClientAlias(new RecordingExtendedKeyManager(),
                                                                     new String[] {"RSA"},
                                                                     null,
                                                                     serverEngine));
        assertThrows(IllegalArgumentException.class,
                     () -> QuicTlsManagerCallbacks.checkClientTrusted(new RecordingExtendedTrustManager(),
                                                                      new X509Certificate[0],
                                                                      "RSA",
                                                                      clientEngine));
    }

    @Test
    void translatesProviderFailureFromKeyManagerCredentialLookup() {
        X509ExtendedKeyManager keyManager = mock(X509ExtendedKeyManager.class);
        ProviderException privateKeyFailure = new ProviderException("private key provider failure");
        ProviderException certificateChainFailure = new ProviderException("certificate chain provider failure");
        when(keyManager.getPrivateKey("alias")).thenThrow(privateKeyFailure);
        when(keyManager.getCertificateChain("alias")).thenThrow(certificateChainFailure);

        QuicTransportException privateKeyThrown = assertThrows(
                QuicTransportException.class,
                () -> QuicTlsManagerCallbacks.privateKey(keyManager, "alias"));
        QuicTransportException certificateChainThrown = assertThrows(
                QuicTransportException.class,
                () -> QuicTlsManagerCallbacks.certificateChain(keyManager, "alias"));

        assertThat(privateKeyThrown.errorCode(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
        assertThat(privateKeyThrown.getCause(), sameInstance(privateKeyFailure));
        assertThat(certificateChainThrown.errorCode(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
        assertThat(certificateChainThrown.getCause(), sameInstance(certificateChainFailure));
    }

    private static SSLParameters sslParameters() {
        SSLParameters sslParameters = new SSLParameters(new String[] {"TLSv1.3"});
        sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
        sslParameters.setServerNames(List.of(new SNIHostName("example.com")));
        sslParameters.setSignatureSchemes(new String[] {"rsa_pss_rsae_sha256"});
        return sslParameters;
    }

    private static SSLParameters serverAliasSslParameters(String... signatureSchemes) {
        SSLParameters sslParameters = new SSLParameters(new String[] {"TLSv1.3"});
        sslParameters.setSignatureSchemes(signatureSchemes);
        return sslParameters;
    }

    private static X509KeyManager jdkKeyManager() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server",
                             QuicTlsRfc8448Vectors.rsaPrivateKey(),
                             "changeit".toCharArray(),
                             new X509Certificate[] {QuicTlsRfc8448Vectors.rsaCertificate()});

        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore, "changeit".toCharArray());
        for (KeyManager keyManager : factory.getKeyManagers()) {
            if (keyManager instanceof X509KeyManager x509KeyManager) {
                return x509KeyManager;
            }
        }
        throw new IllegalStateException("Default KeyManagerFactory did not provide an X509KeyManager");
    }

    private static final class RecordingExtendedKeyManager extends X509ExtendedKeyManager {
        private SSLEngine engine;

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
            this.engine = engine;
            return "client-alias";
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return new String[0];
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return null;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return new String[0];
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return null;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return new X509Certificate[0];
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return null;
        }
    }

    private static final class RecordingExtendedTrustManager extends X509ExtendedTrustManager {
        private SSLEngine engine;

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            this.engine = engine;
        }
    }

    private static final class FailingExtendedTrustManager extends X509ExtendedTrustManager {
        private final CertificateException certificateFailure;
        private final ProviderException providerFailure;

        private FailingExtendedTrustManager(CertificateException certificateFailure) {
            this.certificateFailure = certificateFailure;
            this.providerFailure = null;
        }

        private FailingExtendedTrustManager(ProviderException providerFailure) {
            this.certificateFailure = null;
            this.providerFailure = providerFailure;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            fail();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            fail();
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            fail();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            fail();
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            fail();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            fail();
        }

        private void fail() throws CertificateException {
            if (certificateFailure != null) {
                throw certificateFailure;
            }
            throw providerFailure;
        }
    }

    private static final class LegacyKeyManager implements X509KeyManager {
        private int chooseServerAliasCalls;

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return new String[0];
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return "legacy-client";
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return new String[0];
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            chooseServerAliasCalls++;
            return "legacy-server";
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return new X509Certificate[0];
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return null;
        }
    }

    private static final class LegacyTrustManager implements X509TrustManager {
        private int checkServerTrustedCalls;

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            checkServerTrustedCalls++;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
