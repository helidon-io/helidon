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
import java.security.KeyManagementException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.tls.Tls;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicTLSContextTest {
    @Test
    void createsFromTls() throws Exception {
        Tls tls = Tls.builder()
                .trust(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                .build();

        assertThat(QuicTLSContext.isQuicCompatible(tls), is(true));
        QuicTLSContext context = QuicTLSContext.create(tls);
        assertThat(context, notNullValue());
        QuicTLSEngine engine = context.createEngine("localhost", 443);
        engine.clientMode(true);
        assertThat(engine.handshakeState(), is(QuicTLSEngine.HandshakeState.NEED_SEND_CRYPTO));
    }

    @Test
    void createsClientFromTlsWithPlatformTrust() {
        Tls tls = Tls.builder().build();

        assertThat(QuicTLSContext.isQuicCompatible(tls), is(true));
        QuicTLSEngine engine = QuicTLSContext.create(tls).createEngine("localhost", 443);
        engine.clientMode(true);

        assertThat(engine.handshakeState(), is(QuicTLSEngine.HandshakeState.NEED_SEND_CRYPTO));
    }

    @Test
    void rejectsDisabledTls() {
        Tls tls = Tls.builder()
                .enabled(false)
                .build();

        assertThat(QuicTLSContext.isQuicCompatible(tls), is(false));
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> QuicTLSContext.create(tls));
        assertThat(thrown.getMessage(), is("Cannot construct a QUIC TLS context with disabled TLS"));
    }

    @Test
    void rejectsTlsWithoutTls13() throws Exception {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.2"});
        Tls tls = Tls.builder()
                .trust(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                .sslParameters(sslParameters)
                .build();

        assertThat(QuicTLSContext.isQuicCompatible(tls), is(false));
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> QuicTLSContext.create(tls));
        assertThat(thrown.getMessage(), is("Cannot construct a QUIC TLS context with the given TLS configuration"));
    }

    @Test
    void rejectsTlsWithoutSupportedNamedGroups() throws Exception {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setNamedGroups(new String[] {"ffdhe2048"});
        Tls tls = Tls.builder()
                .trust(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                .sslParameters(sslParameters)
                .build();

        assertThat(QuicTLSContext.isQuicCompatible(tls), is(false));
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> QuicTLSContext.create(tls));
        assertThat(thrown.getMessage(), is("Cannot construct a QUIC TLS context with the given TLS configuration"));
    }

    @Test
    void rejectsUnsupportedAlgorithmConstraints() throws Exception {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setAlgorithmConstraints(QuicTlsTestSupport.allPermittingAlgorithmConstraints());
        Tls tls = Tls.builder()
                .trust(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                .sslParameters(sslParameters)
                .build();

        assertThat(QuicTLSContext.isQuicCompatible(tls), is(false));
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> QuicTLSContext.create(tls));
        assertThat(thrown.getMessage(), is("Cannot construct a QUIC TLS context with the given TLS configuration"));
        assertThat(thrown.getCause(), instanceOf(IllegalArgumentException.class));
        assertThat(thrown.getCause().getMessage(), containsString("AlgorithmConstraints"));
    }

    @Test
    void rejectsUnsupportedSniMatchers() throws Exception {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setSNIMatchers(List.of(SNIHostName.createSNIMatcher("example\\.com")));
        Tls tls = Tls.builder()
                .trust(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                .sslParameters(sslParameters)
                .build();

        assertThat(QuicTLSContext.isQuicCompatible(tls), is(false));
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> QuicTLSContext.create(tls));
        assertThat(thrown.getMessage(), is("Cannot construct a QUIC TLS context with the given TLS configuration"));
        assertThat(thrown.getCause(), instanceOf(IllegalArgumentException.class));
        assertThat(thrown.getCause().getMessage(), containsString("SNIMatchers"));
    }

    @Test
    void acceptsEmptySniMatchers() throws Exception {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setSNIMatchers(List.of());
        Tls tls = Tls.builder()
                .trust(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                .sslParameters(sslParameters)
                .build();

        assertThat(QuicTLSContext.isQuicCompatible(tls), is(true));
        assertThat(QuicTLSContext.create(tls), notNullValue());
    }

    @Test
    void routingEngineRejectsUnsupportedPoliciesAtomically() throws Exception {
        Tls tls = Tls.builder()
                .trust(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                .build();
        QuicTLSEngine engine = QuicTLSContext.create(tls).createEngine("localhost", 443);

        SSLParameters constrained = engine.sslParameters();
        constrained.setAlgorithmConstraints(QuicTlsTestSupport.allPermittingAlgorithmConstraints());
        IllegalArgumentException constraintsFailure =
                assertThrows(IllegalArgumentException.class, () -> engine.sslParameters(constrained));
        assertThat(constraintsFailure.getMessage(), containsString("AlgorithmConstraints"));
        assertThat(engine.sslParameters().getAlgorithmConstraints(), is((Object) null));

        engine.clientMode(true);
        assertThat(engine.handshakeState(), is(QuicTLSEngine.HandshakeState.NEED_SEND_CRYPTO));

        SSLParameters matched = engine.sslParameters();
        matched.setSNIMatchers(List.of(SNIHostName.createSNIMatcher("localhost")));
        IllegalArgumentException matchersFailure =
                assertThrows(IllegalArgumentException.class, () -> engine.sslParameters(matched));
        assertThat(matchersFailure.getMessage(), containsString("SNIMatchers"));
        assertThat(engine.sslParameters().getSNIMatchers(), is((Object) null));
    }

    @Test
    void rejectsInvalidSslParametersAsInvalidConfiguration() throws Exception {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.3"});
        sslParameters.setCipherSuites(new String[] {"TLS_UNKNOWN_CIPHER_SUITE"});
        Tls tls = Tls.builder()
                .trust(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                .sslParameters(sslParameters)
                .build();

        assertThat(QuicTLSContext.isQuicCompatible(tls), is(false));
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> QuicTLSContext.create(tls));
        assertThat(thrown.getMessage(), is("Cannot construct a QUIC TLS context with the given TLS configuration"));
        assertThat(thrown.getCause(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void reportsSslProviderFailureAsIllegalState() throws Exception {
        SSLContext sslContext = new ProviderFailingSslContext(emptySslContext());
        X509TrustManager trustManager = QuicTlsTestSupport.defaultTrustManager();
        Tls tls = Tls.builder()
                .manager(QuicTlsTestSupport.manager(sslContext, null, trustManager))
                .build();

        IllegalStateException compatibleFailure =
                assertThrows(IllegalStateException.class, () -> QuicTLSContext.isQuicCompatible(tls));
        assertThat(compatibleFailure.getMessage(), is("Failed to validate SSLContext for QUIC TLS"));
        assertThat(compatibleFailure.getCause(), instanceOf(ProviderException.class));

        IllegalStateException createFailure =
                assertThrows(IllegalStateException.class, () -> QuicTLSContext.create(tls));
        assertThat(createFailure.getMessage(), is("Failed to validate SSLContext for QUIC TLS"));
        assertThat(createFailure.getCause(), instanceOf(ProviderException.class));
    }

    @Test
    void reportsPublicCompatibilityForNonSunJsseContext() throws Exception {
        SSLContext sslContext = delegatingSslContext();
        X509TrustManager trustManager = QuicTlsTestSupport.defaultTrustManager();
        Tls tls = Tls.builder()
                .manager(QuicTlsTestSupport.manager(sslContext, null, trustManager))
                .build();

        assertThat(QuicTLSContext.isQuicCompatible(tls), is(true));
        QuicTLSContext context = QuicTLSContext.create(tls);
        QuicTLSEngine clientEngine = context.createEngine("localhost", 443);
        clientEngine.clientMode(true);
        assertThat(clientEngine.handshakeState(), is(QuicTLSEngine.HandshakeState.NEED_SEND_CRYPTO));

        QuicTLSEngine serverEngine = context.createEngine("localhost", 443);
        UnsupportedOperationException thrown =
                assertThrows(UnsupportedOperationException.class, () -> serverEngine.clientMode(false));
        assertThat(thrown.getMessage(), containsString("requires an accessible X509KeyManager"));
    }

    @Test
    void rejectsOpaqueTlsWithoutAccessibleClientOrServerMaterial() throws Exception {
        Tls tls = Tls.builder()
                .sslContext(delegatingSslContext())
                .build();

        assertThat(QuicTLSContext.isQuicCompatible(tls), is(false));
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> QuicTLSContext.create(tls));
        assertThat(thrown.getMessage(), is("Cannot construct a QUIC TLS context with the given TLS configuration"));
    }

    @Test
    void rejectsCustomManagerWithoutAccessibleClientOrServerMaterial() throws Exception {
        Tls tls = Tls.builder()
                .manager(QuicTlsTestSupport.manager(delegatingSslContext(), null, null))
                .build();

        assertThat(QuicTLSContext.isQuicCompatible(tls), is(false));
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> QuicTLSContext.create(tls));
        assertThat(thrown.getMessage(), is("Cannot construct a QUIC TLS context with the given TLS configuration"));
    }

    @Test
    void supportsServerModeForNonSunJsseContextWithAccessibleKeyManager() throws Exception {
        SSLContext sslContext = delegatingSslContext();
        X509KeyManager keyManager = staticKeyManager();
        Tls tls = Tls.builder()
                .manager(QuicTlsTestSupport.manager(sslContext, keyManager, null))
                .build();

        assertThat(QuicTLSContext.isQuicCompatible(tls), is(true));
        QuicTLSContext context = QuicTLSContext.create(tls);
        QuicTLSEngine serverEngine = context.createEngine("localhost", 443);
        serverEngine.clientMode(false);

        assertThat(serverEngine.handshakeState(), is(QuicTLSEngine.HandshakeState.NEED_RECV_CRYPTO));
    }

    @Test
    void supportsServerClientAuthForNonSunJsseContextWithAccessibleTrustManager() throws Exception {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.3"});
        sslParameters.setNeedClientAuth(true);

        SSLContext sslContext = delegatingSslContext();
        X509KeyManager keyManager = staticKeyManager();
        X509TrustManager trustManager = QuicTlsTestSupport.defaultTrustManager();
        Tls tls = Tls.builder()
                .manager(QuicTlsTestSupport.manager(sslContext, keyManager, trustManager))
                .sslParameters(sslParameters)
                .build();

        QuicTLSContext context = QuicTLSContext.create(tls);
        QuicTLSEngine serverEngine = context.createEngine("localhost", 443);
        serverEngine.clientMode(false);

        assertThat(serverEngine.handshakeState(), is(QuicTLSEngine.HandshakeState.NEED_RECV_CRYPTO));
    }

    @Test
    void rejectsServerClientAuthForNonSunJsseContextWithoutAccessibleTrustManager() throws Exception {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.3"});
        sslParameters.setNeedClientAuth(true);

        SSLContext sslContext = delegatingSslContext();
        X509KeyManager keyManager = staticKeyManager();
        Tls tls = Tls.builder()
                .manager(QuicTlsTestSupport.manager(sslContext, keyManager, null))
                .sslParameters(sslParameters)
                .build();

        assertThat(QuicTLSContext.isQuicCompatible(tls), is(false));
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> QuicTLSContext.create(tls));
        assertThat(thrown.getMessage(), is("Cannot construct a QUIC TLS context with the given TLS configuration"));
    }

    private static SSLContext emptySslContext() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, new SecureRandom());
        return context;
    }

    private static SSLContext delegatingSslContext() throws Exception {
        return new DelegatingSslContext(emptySslContext());
    }

    private static X509KeyManager staticKeyManager() throws Exception {
        return new StaticKeyManager(QuicTlsRfc8448Vectors.rsaCertificate(), QuicTlsRfc8448Vectors.rsaPrivateKey());
    }

    private static final class DelegatingSslContext extends SSLContext {
        private DelegatingSslContext(SSLContext delegate) {
            super(new DelegatingSslContextSpi(delegate), delegate.getProvider(), delegate.getProtocol());
        }
    }

    private static final class ProviderFailingSslContext extends SSLContext {
        private ProviderFailingSslContext(SSLContext delegate) {
            super(new ProviderFailingSslContextSpi(delegate), delegate.getProvider(), delegate.getProtocol());
        }
    }

    private static class DelegatingSslContextSpi extends SSLContextSpi {
        private final SSLContext delegate;

        private DelegatingSslContextSpi(SSLContext delegate) {
            this.delegate = delegate;
        }

        @Override
        protected void engineInit(KeyManager[] keyManagers,
                                  TrustManager[] trustManagers,
                                  SecureRandom secureRandom) throws KeyManagementException {
            delegate.init(keyManagers, trustManagers, secureRandom);
        }

        @Override
        protected SSLSocketFactory engineGetSocketFactory() {
            return delegate.getSocketFactory();
        }

        @Override
        protected SSLServerSocketFactory engineGetServerSocketFactory() {
            return delegate.getServerSocketFactory();
        }

        @Override
        protected SSLEngine engineCreateSSLEngine() {
            return delegate.createSSLEngine();
        }

        @Override
        protected SSLEngine engineCreateSSLEngine(String host, int port) {
            return delegate.createSSLEngine(host, port);
        }

        @Override
        protected SSLSessionContext engineGetServerSessionContext() {
            return delegate.getServerSessionContext();
        }

        @Override
        protected SSLSessionContext engineGetClientSessionContext() {
            return delegate.getClientSessionContext();
        }

        @Override
        protected SSLParameters engineGetDefaultSSLParameters() {
            return delegate.getDefaultSSLParameters();
        }

        @Override
        protected SSLParameters engineGetSupportedSSLParameters() {
            return delegate.getSupportedSSLParameters();
        }
    }

    private static final class ProviderFailingSslContextSpi extends DelegatingSslContextSpi {
        private ProviderFailingSslContextSpi(SSLContext delegate) {
            super(delegate);
        }

        @Override
        protected SSLEngine engineCreateSSLEngine() {
            throw new ProviderException("Provider failed to create an SSLEngine");
        }
    }

    private static final class StaticKeyManager extends X509ExtendedKeyManager {
        private static final String ALIAS = "server";

        private final X509Certificate[] certificateChain;
        private final PrivateKey privateKey;

        private StaticKeyManager(X509Certificate certificate, PrivateKey privateKey) {
            this.certificateChain = new X509Certificate[] {certificate};
            this.privateKey = privateKey;
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
            return supports(keyType) ? new String[] {ALIAS} : new String[0];
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return supports(keyType) ? ALIAS : null;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return ALIAS.equals(alias) ? certificateChain.clone() : null;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return ALIAS.equals(alias) ? privateKey : null;
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
            return null;
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            return supports(keyType) ? ALIAS : null;
        }

        private boolean supports(String keyType) {
            return certificateChain[0].getPublicKey().getAlgorithm().equalsIgnoreCase(keyType);
        }
    }
}
