/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.LazyValue;

/**
 * The default configured {@link TlsManager} implementation.
 */
public class ConfiguredTlsManager implements TlsManager {
    // secure random cannot be stored in native image, it must
    // be initialized at runtime
    private static final LazyValue<SecureRandom> RANDOM = LazyValue.create(SecureRandom::new);

    private final String name;
    private final String type;
    private final Set<Consumer<SSLContext>> sslContextConsumers = new LinkedHashSet<>();

    private volatile X509KeyManager keyManager;
    private volatile X509TrustManager trustManager;
    private volatile SSLContext sslContext;

    ConfiguredTlsManager() {
        this("@default", "tls-manager");
    }

    /**
     * Configured tls manager constructor.
     *
     * @param name the manager name
     * @param type the manager type
     */
    protected ConfiguredTlsManager(String name,
                                   String type) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
    }

    // @Override // NamedService
    public String name() {
        return name;
    }

    // @Override // NamedService
    public String type() {
        return type;
    }

    @Override // TlsManager
    public SSLContext sslContext() {
        return sslContext;
    }

    @Override // TlsManager
    public void init(WebServerTls tlsConfig) {
        // TODO:
//        sslContext(tlsConfig);
    }

    @Override
    public void subscribe(Consumer<SSLContext> sslContextConsumer) {
        sslContextConsumers.add(Objects.requireNonNull(sslContextConsumer));
    }

    @Override // TlsManager
    public Optional<X509KeyManager> keyManager() {
        return Optional.ofNullable(keyManager);
    }

    @Override // TlsManager
    public Optional<X509TrustManager> trustManager() {
        return Optional.ofNullable(trustManager);
    }

    /**
     * Reload the current SSL context with the provided key manager and trust manager (if defined).
     *
     * @param keyManager   key manager to use
     * @param trustManager trust manager to use
     */
    // for extensibility it is more suitable to have a single method for reloading, hence the optional parameters,
    // if we need even one more, create an object with a builder
    protected void reload(Optional<X509KeyManager> keyManager,
                          Optional<X509TrustManager> trustManager) {
        // TODO:
//        keyManager.ifPresent(reloadableKeyManager::reload);
//        trustManager.ifPresent(reloadableTrustManager::reload);
    }

    protected void initSslContext(WebServerTls tlsConfig,
                                  SecureRandom secureRandom,
                                  KeyManager[] keyManagers,
                                  TrustManager[] trustManagers) {
        try {
            TrustManager[] tm;
            KeyManager[] km;

            if (keyManagers.length == 0) {
                km = null;
            } else {
                km = wrapX509KeyManagers(keyManagers);
            }

            if (trustManagers.length == 0) {
                tm = null;
            } else {
                tm = wrapX509TrustManagers(trustManagers);
            }

            SSLContext sslContext = null;

            // TODO:
//            if (tlsConfig.provider().isPresent()) {
//                sslContext = SSLContext.getInstance(tlsConfig.protocol(), tlsConfig.provider().get());
//            } else {
//                sslContext = SSLContext.getInstance(tlsConfig.protocol());
//            }
            sslContext.init(km, tm, secureRandom);

            SSLSessionContext serverSessionContext = sslContext.getServerSessionContext();
            if (serverSessionContext != null) {
                // TODO:
//                serverSessionContext.setSessionCacheSize(tlsConfig.sessionCacheSize());
//                // seconds
//                serverSessionContext.setSessionTimeout((int) tlsConfig.sessionTimeout().toSeconds());
            }

            this.sslContext = sslContext;
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Failed to create SSLContext", e);
        }
    }

    /**
     * Load secure random.
     *
     * @param tlsConfig TLS configuration
     * @return secure random
     */
    protected SecureRandom secureRandom(WebServerTls tlsConfig) {
        // TODO:
//        if (tlsConfig.secureRandom().isPresent()) {
//            return tlsConfig.secureRandom().get();
//        }
//
//        try {
//            if (tlsConfig.secureRandomAlgorithm().isPresent() && tlsConfig.secureRandomProvider().isEmpty()) {
//                return SecureRandom.getInstance(tlsConfig.secureRandomAlgorithm().get());
//            }
//
//            if (tlsConfig.secureRandomProvider().isPresent()) {
//                if (tlsConfig.secureRandomAlgorithm().isEmpty()) {
//                    throw new IllegalArgumentException("Invalid configuration of secure random. Provider is configured to "
//                                                               + tlsConfig.secureRandomProvider().get()
//                                                               + ", but algorithm is not specified");
//                }
//                return SecureRandom.getInstance(tlsConfig.secureRandomAlgorithm().get(), tlsConfig.secureRandomProvider().get());
//            }
//        } catch (GeneralSecurityException e) {
//            throw new IllegalArgumentException("invalid configuration for secure random, cannot create it", e);
//        }

        return RANDOM.get();
    }

    protected KeyManagerFactory buildKmf(WebServerTls target,
                                         SecureRandom secureRandom,
                                         PrivateKey privateKey,
                                         Certificate[] certificates) {
        byte[] passwordBytes = new byte[64];
        secureRandom.nextBytes(passwordBytes);
        char[] password = Base64.getEncoder().encodeToString(passwordBytes).toCharArray();

        try {
            KeyStore ks = internalKeystore(target);
            ks.setKeyEntry("key",
                           privateKey,
                           password,
                           certificates);

            KeyManagerFactory kmf = kmf(target);
            kmf.init(ks, password);
            return kmf;
        } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Invalid configuration for key management factory, cannot create factory", e);
        }
    }

    /**
     * Creates an internal keystore and loads it with no password and no data.
     *
     * @param tlsConfig TLS config
     * @return a new keystore
     */
    protected KeyStore internalKeystore(WebServerTls tlsConfig) {
        // TODO:
//        try {
//            String type = tlsConfig.internalKeystoreType().orElseGet(KeyStore::getDefaultType);

            KeyStore ks = null;
//            if (tlsConfig.internalKeystoreProvider().isEmpty()) {
//                ks = KeyStore.getInstance(type);
//            } else {
//                ks = KeyStore.getInstance(type, tlsConfig.internalKeystoreProvider().get());
//            }
//
//            ks.load(null, null);
            return ks;
//        } catch (KeyStoreException
//                 | NoSuchProviderException
//                 | IOException
//                 | NoSuchAlgorithmException
//                 | CertificateException e) {
//            throw new IllegalArgumentException("Invalid configuration of internal keystore", e);
//        }
    }

    /**
     * Create a new trust manager factory based on the configuration (i.e., the algorithm and provider).
     *
     * @param tlsConfig TLS config
     * @return a new trust manager factory
     */
    protected TrustManagerFactory createTmf(WebServerTls tlsConfig) {
        // TODO:
//        try {
//            String algorithm = tlsConfig.trustManagerFactoryAlgorithm().orElseGet(TrustManagerFactory::getDefaultAlgorithm);
//            if (tlsConfig.trustManagerFactoryProvider().isEmpty()) {
//                return TrustManagerFactory.getInstance(algorithm);
//            } else {
//                return TrustManagerFactory.getInstance(algorithm, tlsConfig.trustManagerFactoryProvider().get());
//            }
//        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
//            throw new IllegalArgumentException("Invalid configuration of trust manager factory", e);
//        }
        return null;
    }

    /**
     * Creates a trust all trust manager factory.
     *
     * @return a new trust manager factory trusting all
     */
    protected TrustManagerFactory trustAllTmf() {
        // TODO:
//        return new TrustAllManagerFactory();
        return null;
    }

    // creates an internal keystore and initializes it with certificates discovered from configuration, then gets the trust
    // manager factory using tmf(TlsConfig)
    private TrustManagerFactory initTmf(WebServerTls tlsConfig) throws KeyStoreException {
        KeyStore ks = internalKeystore(tlsConfig);
        int i = 1;
        // TODO:
//        for (X509Certificate cert : tlsConfig.trust()) {
//            ks.setCertificateEntry(String.valueOf(i), cert);
//            i++;
//        }
        TrustManagerFactory tmf = createTmf(tlsConfig);
        tmf.init(ks);
        return tmf;
    }

    // used by ConfiguredTlsManager to setup a TrustManagerFactory, that may be "trustAll", or based on configuration
    private TrustManagerFactory tmf(WebServerTls tlsConfig) throws KeyStoreException {
        // TODO:
//        if (tlsConfig.trustAll()) {
//            return trustAllTmf();
//        }
//
//        if (!tlsConfig.trust().isEmpty()) {
//            return initTmf(tlsConfig);
//        }

        return null;
    }

    private void sslContext(WebServerTls tlsConfig) {
        // TODO:
//        if (tlsConfig.sslContext().isPresent()) {
//            this.sslContext = tlsConfig.sslContext().get();
//            return;
//        }
//
//        try {
//            SecureRandom secureRandom = secureRandom(tlsConfig);
//            KeyManagerFactory kmf = tlsConfig.privateKey()
//                    .map(pk -> buildKmf(tlsConfig, secureRandom, pk, tlsConfig.privateKeyCertChain().toArray(new Certificate[0])))
//                    .orElse(null);
//
//            TrustManagerFactory tmf = tmf(tlsConfig);
//
//            initSslContext(tlsConfig,
//                           secureRandom,
//                           kmf == null ? new KeyManager[0] : kmf.getKeyManagers(),
//                           tmf == null ? new TrustManager[0] : tmf.getTrustManagers());
//        } catch (GeneralSecurityException e) {
//            throw new IllegalArgumentException("Failed to create SSLContext", e);
//        }
    }

    /**
     * Loads a key manager factory based on config.
     *
     * @param tlsConfig TLS configuration
     * @return a new key manager factory
     */
    private KeyManagerFactory kmf(WebServerTls tlsConfig) {
        // TODO:
//        try {
//            String algorithm = tlsConfig.keyManagerFactoryAlgorithm().orElseGet(KeyManagerFactory::getDefaultAlgorithm);
//
//            if (tlsConfig.keyManagerFactoryProvider().isPresent()) {
//                return KeyManagerFactory.getInstance(algorithm, tlsConfig.keyManagerFactoryProvider().get());
//            } else {
//                return KeyManagerFactory.getInstance(algorithm);
//            }
//        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
//            throw new IllegalArgumentException("Invalid configuration of key manager factory", e);
//        }
        return null;
    }

    /**
     * Analyze the key managers and wrap the first X509 key manager with reloadable support.
     *
     * @param keyManagers used key managers
     * @return the same managers, except the first X509 one is wrapped
     */
    private KeyManager[] wrapX509KeyManagers(KeyManager[] keyManagers) {
        // TODO:
        KeyManager[] toReturn = new KeyManager[keyManagers.length];
//        System.arraycopy(keyManagers, 0, toReturn, 0, toReturn.length);
//        for (int i = 0; i < keyManagers.length; i++) {
//            KeyManager keyManager = keyManagers[i];
//            if (keyManager instanceof X509KeyManager x509KeyManager) {
//                this.keyManager = x509KeyManager;
//                this.reloadableKeyManager = TlsReloadableX509KeyManager.create(x509KeyManager);
//                toReturn[i] = reloadableKeyManager;
//                return toReturn;
//            }
//        }
//        this.reloadableKeyManager = new TlsReloadableX509KeyManager.NotReloadableKeyManager();
        return toReturn;
    }

    /**
     * Analyze the trust managers and wrap the first X509 trust manager with reloadable support.
     *
     * @param trustManagers used trust managers
     * @return the same managers, except the first X509 one is wrapped
     */
    private TrustManager[] wrapX509TrustManagers(TrustManager[] trustManagers) {
        TrustManager[] toReturn = new TrustManager[trustManagers.length];
        // TODO:
//        System.arraycopy(trustManagers, 0, toReturn, 0, toReturn.length);
//        for (int i = 0; i < trustManagers.length; i++) {
//            TrustManager trustManager = trustManagers[i];
//            if (trustManager instanceof X509TrustManager x509TrustManager) {
//                this.trustManager = x509TrustManager;
//                this.reloadableTrustManager = TlsReloadableX509TrustManager.create(x509TrustManager);
//                toReturn[i] = reloadableTrustManager;
//                return toReturn;
//            }
//        }
//        this.reloadableTrustManager = new TlsReloadableX509TrustManager.NotReloadableTrustManager();
        return toReturn;
    }
}
