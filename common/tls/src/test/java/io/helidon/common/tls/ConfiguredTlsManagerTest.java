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

package io.helidon.common.tls;

import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Optional;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ConfiguredTlsManagerTest {
    @Test
    void reloadCannotAddTrustManagerAfterStartingWithoutOne() {
        Tls tls = Tls.create(it -> { });
        Tls reload = Tls.create(it -> it.trustAll(true));

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                                                               () -> tls.reload(reload));

        assertThat(exception.getMessage(), is("Cannot set trust manager if one was not set during server start"));
    }

    @Test
    void reloadMaterialCannotAddTrustManagerAfterStartingWithoutOne() {
        Tls tls = Tls.create(it -> { });
        TlsMaterial material = TlsMaterial.builder()
                .trustAll(true)
                .build();

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                                                               () -> tls.reload(material));

        assertThat(exception.getMessage(), is("Cannot set trust manager if one was not set during server start"));
    }

    @Test
    void reloadMaterialRejectsEmptyMaterial() {
        Tls tls = Tls.create(it -> it.trustAll(true));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> tls.reload(TlsMaterial.create()));

        assertThat(exception.getMessage(), is("TLS material must define private key or trust material"));
    }

    @Test
    void reloadMaterialRejectsTrustAllWithTrustCertificates() {
        Tls tls = Tls.create(it -> it.trustAll(true));
        TlsMaterial material = TlsMaterial.builder()
                .trustAll(true)
                .addTrust(mock(X509Certificate.class))
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> tls.reload(material));

        assertThat(exception.getMessage(), is("TLS material cannot combine trustAll and trust certificates"));
    }

    @Test
    void reloadMaterialRejectsPrivateKeyWithoutCertificateChain() {
        Tls tls = Tls.create(it -> it.trustAll(true));
        TlsMaterial material = TlsMaterial.builder()
                .privateKey(new TestPrivateKey("test"))
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> tls.reload(material));

        assertThat(exception.getMessage(), is("TLS material with private key must also define the certificate chain"));
    }

    @Test
    void reloadMaterialRejectsCertificateChainWithoutPrivateKey() {
        Tls tls = Tls.create(it -> it.trustAll(true));
        TlsMaterial material = TlsMaterial.builder()
                .addPrivateKeyCertChain(mock(X509Certificate.class))
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> tls.reload(material));

        assertThat(exception.getMessage(), is("TLS material certificate chain requires a private key"));
    }

    @Test
    void reloadCannotAddKeyManagerAfterStartingWithoutOne() {
        Tls tls = Tls.create(it -> { });
        Tls reload = Tls.create(it -> it.manager(new KeyManagerOnlyTlsManager()));

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                                                               () -> tls.reload(reload));

        assertThat(exception.getMessage(), is("Cannot reload key manager if one was not set during server start"));
    }

    @Test
    void reloadCannotAddTrustManagerAfterStartingWithExplicitSslContext() {
        SSLContext sslContext = createSslContext();
        Tls tls = Tls.create(it -> it.sslContext(sslContext));
        Tls reload = Tls.create(it -> it.trustAll(true));

        assertThat(tls.sslContext(), sameInstance(sslContext));

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                                                               () -> tls.reload(reload));

        assertThat(exception.getMessage(),
                   is("TLS cannot be reloaded when an explicit instance of SSL context was used to create it"));
    }

    @Test
    void reloadCannotAddKeyManagerAfterStartingWithExplicitSslContext() {
        SSLContext sslContext = createSslContext();
        Tls tls = Tls.create(it -> it.sslContext(sslContext));
        Tls reload = Tls.create(it -> it.manager(new KeyManagerOnlyTlsManager()));

        assertThat(tls.sslContext(), sameInstance(sslContext));

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                                                               () -> tls.reload(reload));

        assertThat(exception.getMessage(),
                   is("TLS cannot be reloaded when an explicit instance of SSL context was used to create it"));
    }

    @Test
    void failedReinitializationDoesNotClearReloadableManagers() {
        ConfiguredTlsManager manager = new ConfiguredTlsManager();
        X509KeyManager keyManager = new TestKeyManager();
        X509TrustManager trustManager = new TestTrustManager();
        TlsConfig initialConfig = Tls.builder().buildPrototype();
        TlsConfig failingConfig = Tls.builder()
                .provider("missing-provider")
                .buildPrototype();

        manager.initSslContext(initialConfig,
                               new SecureRandom(),
                               new KeyManager[] {keyManager},
                               new TrustManager[] {trustManager});
        SSLContext sslContext = manager.sslContext();

        assertThat(manager.sslContext(), sameInstance(sslContext));
        assertThat(manager.keyManager().orElseThrow(), sameInstance(keyManager));
        assertThat(manager.trustManager().orElseThrow(), sameInstance(trustManager));

        assertThrows(IllegalArgumentException.class,
                     () -> manager.initSslContext(failingConfig,
                                                  new SecureRandom(),
                                                  new KeyManager[] {new TestKeyManager()},
                                                  new TrustManager[] {new TestTrustManager()}));

        assertThat(manager.sslContext(), sameInstance(sslContext));
        assertThat(manager.keyManager().orElseThrow(), sameInstance(keyManager));
        assertThat(manager.trustManager().orElseThrow(), sameInstance(trustManager));

        X509KeyManager reloadedKeyManager = new TestKeyManager();
        X509TrustManager reloadedTrustManager = new TestTrustManager();
        manager.reload(Optional.of(reloadedKeyManager), Optional.of(reloadedTrustManager));

        assertThat(manager.keyManager().orElseThrow(), sameInstance(reloadedKeyManager));
        assertThat(manager.trustManager().orElseThrow(), sameInstance(reloadedTrustManager));
    }

    @Test
    void reloadDoesNotChangeKeyManagerWhenTrustManagerCannotReload() {
        ConfiguredTlsManager manager = new ConfiguredTlsManager();
        PrivateKey initialPrivateKey = new TestPrivateKey("initial");
        PrivateKey reloadPrivateKey = new TestPrivateKey("reload");

        manager.initSslContext(Tls.builder().buildPrototype(),
                               new SecureRandom(),
                               new KeyManager[] {new TestKeyManager(initialPrivateKey)},
                               new TrustManager[0]);

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                                                               () -> manager.reload(
                                                                       Optional.of(new TestKeyManager(reloadPrivateKey)),
                                                                       Optional.of(new TestTrustManager())));

        assertThat(exception.getMessage(), is("Cannot set trust manager if one was not set during server start"));
        assertThat(manager.reloadableKeyManager().getPrivateKey("test"), sameInstance(initialPrivateKey));
    }

    @Test
    void reloadMaterialUpdatesTrustManager() {
        ConfiguredTlsManager manager = new ConfiguredTlsManager();
        X509TrustManager initialTrustManager = new TestTrustManager();

        manager.initSslContext(Tls.builder().buildPrototype(),
                               new SecureRandom(),
                               new KeyManager[0],
                               new TrustManager[] {initialTrustManager});

        manager.reload(TlsMaterial.builder()
                               .trustAll(true)
                               .build());

        assertNotSame(initialTrustManager, manager.trustManager().orElseThrow());
    }

    private static SSLContext createSslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    private static final class KeyManagerOnlyTlsManager implements TlsManager {
        private SSLContext sslContext;

        @Override
        public String name() {
            return "test";
        }

        @Override
        public String type() {
            return "test";
        }

        @Override
        public void init(TlsConfig tls) {
            this.sslContext = createSslContext();
        }

        @Override
        @SuppressWarnings("removal")
        public void reload(Tls tls) {
        }

        @Override
        public SSLContext sslContext() {
            return sslContext;
        }

        @Override
        public Optional<X509KeyManager> keyManager() {
            return Optional.of(new TestKeyManager());
        }

        @Override
        public Optional<X509TrustManager> trustManager() {
            return Optional.empty();
        }
    }

    private static final class TestKeyManager implements X509KeyManager {
        private final PrivateKey privateKey;

        private TestKeyManager() {
            this(new TestPrivateKey("test"));
        }

        private TestKeyManager(PrivateKey privateKey) {
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
            return privateKey;
        }
    }

    private static final class TestPrivateKey implements PrivateKey {
        private static final long serialVersionUID = 1L;

        private final String algorithm;

        private TestPrivateKey(String algorithm) {
            this.algorithm = algorithm;
        }

        @Override
        public String getAlgorithm() {
            return algorithm;
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            return new byte[0];
        }
    }

    private static final class TestTrustManager implements X509TrustManager {
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
    }
}
