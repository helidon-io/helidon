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
    void reloadCannotUseExplicitSslContextAsReplacement() {
        Tls tls = Tls.create(it -> it.trustAll(true));
        SSLContext sslContext = createSslContext();
        Tls reload = Tls.create(it -> it.sslContext(sslContext));

        assertThat(reload.sslContext(), sameInstance(sslContext));

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                                                               () -> tls.reload(reload));

        assertThat(exception.getMessage(),
                   is("TLS cannot be reloaded when an explicit instance of SSL context was used to create it"));
    }

    @Test
    void failedInitialAttemptCanBeRetried() {
        ConfiguredTlsManager manager = new ConfiguredTlsManager();
        X509KeyManager keyManager = new TestKeyManager();
        X509TrustManager trustManager = new TestTrustManager();
        TlsConfig initialConfig = Tls.builder().buildPrototype();
        TlsConfig failingConfig = Tls.builder()
                .provider("missing-provider")
                .buildPrototype();

        assertThrows(IllegalArgumentException.class,
                     () -> manager.initSslContext(failingConfig,
                                                  new SecureRandom(),
                                                  new KeyManager[] {new TestKeyManager()},
                                                  new TrustManager[] {new TestTrustManager()}));
        assertThat(manager.generation(), is(0L));
        assertThat(manager.keyManager().isEmpty(), is(true));
        assertThat(manager.trustManager().isEmpty(), is(true));

        manager.initSslContext(initialConfig,
                               new SecureRandom(),
                               new KeyManager[] {keyManager},
                               new TrustManager[] {trustManager});
        SSLContext sslContext = manager.sslContext();

        assertThat(manager.sslContext(), sameInstance(sslContext));
        assertThat(manager.keyManager().orElseThrow(), sameInstance(keyManager));
        assertThat(manager.trustManager().orElseThrow(), sameInstance(trustManager));
        assertThat(manager.generation(), is(0L));

        X509KeyManager reloadedKeyManager = new TestKeyManager();
        X509TrustManager reloadedTrustManager = new TestTrustManager();
        manager.reload(Optional.of(reloadedKeyManager), Optional.of(reloadedTrustManager));

        assertThat(manager.keyManager().orElseThrow(), sameInstance(reloadedKeyManager));
        assertThat(manager.trustManager().orElseThrow(), sameInstance(reloadedTrustManager));
        assertThat(manager.generation(), is(1L));
    }

    @Test
    void repeatedInitializationKeepsInitialState() {
        ConfiguredTlsManager manager = new ConfiguredTlsManager();
        X509KeyManager initialKeyManager = new TestKeyManager();
        X509TrustManager initialTrustManager = new TestTrustManager();

        manager.initSslContext(Tls.builder().buildPrototype(),
                               new SecureRandom(),
                               new KeyManager[] {initialKeyManager},
                               new TrustManager[] {initialTrustManager});
        SSLContext initialSslContext = manager.sslContext();

        assertThat(manager.generation(), is(0L));
        assertThat(manager.keyManager().orElseThrow(), sameInstance(initialKeyManager));
        assertThat(manager.trustManager().orElseThrow(), sameInstance(initialTrustManager));

        X509KeyManager replacementKeyManager = new TestKeyManager();
        X509TrustManager replacementTrustManager = new TestTrustManager();
        manager.initSslContext(Tls.builder().buildPrototype(),
                               new SecureRandom(),
                               new KeyManager[] {replacementKeyManager},
                               new TrustManager[] {replacementTrustManager});
        manager.initSslContext(Tls.builder().provider("missing-provider").buildPrototype(),
                               new SecureRandom(),
                               new KeyManager[] {replacementKeyManager},
                               new TrustManager[] {replacementTrustManager});

        assertThrows(NullPointerException.class, () -> manager.init(null));
        assertThrows(NullPointerException.class,
                     () -> manager.initSslContext(null,
                                                  null,
                                                  new KeyManager[] {replacementKeyManager},
                                                  new TrustManager[] {replacementTrustManager}));
        assertThrows(NullPointerException.class,
                     () -> manager.initSslContext(Tls.builder().buildPrototype(),
                                                  null,
                                                  null,
                                                  new TrustManager[] {replacementTrustManager}));
        assertThrows(NullPointerException.class,
                     () -> manager.initSslContext(Tls.builder().buildPrototype(),
                                                  null,
                                                  new KeyManager[] {replacementKeyManager},
                                                  null));
        manager.initSslContext(Tls.builder().buildPrototype(),
                               null,
                               new KeyManager[] {replacementKeyManager},
                               new TrustManager[] {replacementTrustManager});

        assertThat(manager.sslContext(), sameInstance(initialSslContext));
        assertThat(manager.generation(), is(0L));
        assertThat(manager.keyManager().orElseThrow(), sameInstance(initialKeyManager));
        assertThat(manager.trustManager().orElseThrow(), sameInstance(initialTrustManager));

        X509KeyManager reloadedKeyManager = new TestKeyManager();
        X509TrustManager reloadedTrustManager = new TestTrustManager();
        manager.reload(Optional.of(reloadedKeyManager), Optional.of(reloadedTrustManager));

        assertThat(manager.generation(), is(1L));
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
        assertThat(manager.generation(), is(0L));
    }

    @Test
    void reloadMaterialUpdatesTrustManager() {
        ConfiguredTlsManager manager = new ConfiguredTlsManager();
        X509TrustManager initialTrustManager = new TestTrustManager();

        manager.initSslContext(Tls.builder().buildPrototype(),
                               new SecureRandom(),
                               new KeyManager[0],
                               new TrustManager[] {initialTrustManager});

        assertThat(manager.generation(), is(0L));
        manager.reload(TlsMaterial.builder()
                               .trustAll(true)
                               .build());

        assertNotSame(initialTrustManager, manager.trustManager().orElseThrow());
        assertThat(manager.generation(), is(1L));
    }

    @Test
    void generationAdvancesWhenReloadFailsAfterPublishingMaterial() {
        ConfiguredTlsManager manager = new FailingAfterReloadTlsManager();
        manager.initSslContext(Tls.builder().buildPrototype(),
                               new SecureRandom(),
                               new KeyManager[0],
                               new TrustManager[] {new TestTrustManager()});

        assertThrows(IllegalStateException.class,
                     () -> manager.reload(TlsMaterial.builder()
                             .trustAll(true)
                             .build()));

        assertThat(manager.generation(), is(1L));
    }

    @Test
    @SuppressWarnings("removal")
    void reloadTlsRetriesUntilSourceGenerationIsStable() {
        X509KeyManager reloadedKeyManager = new TestKeyManager();
        X509TrustManager initialTrustManager = new TestTrustManager();
        X509TrustManager reloadedTrustManager = new TestTrustManager();
        ChangingGenerationTlsManager sourceManager = new ChangingGenerationTlsManager(reloadedKeyManager,
                                                                                       initialTrustManager,
                                                                                       reloadedTrustManager);
        Tls source = Tls.create(it -> it.manager(sourceManager));

        ConfiguredTlsManager targetManager = new ConfiguredTlsManager();
        targetManager.initSslContext(Tls.builder().buildPrototype(),
                                     new SecureRandom(),
                                     new KeyManager[] {new TestKeyManager()},
                                     new TrustManager[] {new TestTrustManager()});

        targetManager.reload(source);

        assertThat(targetManager.keyManager().orElseThrow(), sameInstance(reloadedKeyManager));
        assertThat(targetManager.trustManager().orElseThrow(), sameInstance(reloadedTrustManager));
        assertThat(sourceManager.keyManagerReads, is(2));
        assertThat(sourceManager.trustManagerReads, is(2));
        assertThat(sourceManager.generationReads, is(4));
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

    private static final class FailingAfterReloadTlsManager extends ConfiguredTlsManager {
        @Override
        protected void reload(Optional<X509KeyManager> keyManager, Optional<X509TrustManager> trustManager) {
            super.reload(keyManager, trustManager);
            throw new IllegalStateException("reload failed after publication");
        }
    }

    private static final class ChangingGenerationTlsManager implements TlsManager {
        private final SSLContext sslContext = createSslContext();
        private final X509KeyManager keyManager;
        private final X509TrustManager initialTrustManager;
        private final X509TrustManager reloadedTrustManager;
        private int generationReads;
        private int keyManagerReads;
        private int trustManagerReads;

        private ChangingGenerationTlsManager(X509KeyManager keyManager,
                                             X509TrustManager initialTrustManager,
                                             X509TrustManager reloadedTrustManager) {
            this.keyManager = keyManager;
            this.initialTrustManager = initialTrustManager;
            this.reloadedTrustManager = reloadedTrustManager;
        }

        @Override
        public void init(TlsConfig tls) {
        }

        @Override
        public long generation() {
            return generationReads++ == 0 ? 0L : 1L;
        }

        @Override
        public SSLContext sslContext() {
            return sslContext;
        }

        @Override
        public Optional<X509KeyManager> keyManager() {
            keyManagerReads++;
            return Optional.of(keyManager);
        }

        @Override
        public Optional<X509TrustManager> trustManager() {
            return Optional.of(trustManagerReads++ == 0 ? initialTrustManager : reloadedTrustManager);
        }

        @Override
        public String name() {
            return "changing-generation";
        }

        @Override
        public String type() {
            return "changing-generation";
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
