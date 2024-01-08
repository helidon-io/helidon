/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

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

    private volatile X509KeyManager keyManager;
    private volatile TlsReloadableX509KeyManager reloadableKeyManager;
    private volatile X509TrustManager trustManager;
    private volatile TlsReloadableX509TrustManager reloadableTrustManager;
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

    @Override // NamedService
    public String name() {
        return name;
    }

    @Override // NamedService
    public String type() {
        return type;
    }

    @Override // TlsManager
    public SSLContext sslContext() {
        return sslContext;
    }

    @Override // TlsManager
    public void init(TlsConfig tlsConfig) {
        sslContext(tlsConfig);
    }

    @Override // TlsManager
    public void reload(Tls tls) {
        reload(tls.keyManager(), tls.trustManager());
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
    protected void reload(Optional<X509KeyManager> keyManager, Optional<X509TrustManager> trustManager) {
        keyManager.ifPresent(reloadableKeyManager::reload);
        trustManager.ifPresent(reloadableTrustManager::reload);
    }

    /**
     * Initialize and set the {@link javax.net.ssl.SSLContext} on this manager instance.
     *
     * @param tlsConfig     the tls configuration
     * @param secureRandom  the secure random
     * @param keyManagers   the key managers
     * @param trustManagers the trust managers
     */
    protected void initSslContext(TlsConfig tlsConfig,
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

            SSLContext sslContext;

            if (tlsConfig.provider().isPresent()) {
                sslContext = SSLContext.getInstance(tlsConfig.protocol(), tlsConfig.provider().get());
            } else {
                sslContext = SSLContext.getInstance(tlsConfig.protocol());
            }
            sslContext.init(km, tm, secureRandom);

            SSLSessionContext serverSessionContext = sslContext.getServerSessionContext();
            if (serverSessionContext != null) {
                if (tlsConfig.sessionCacheSize() != TlsConfig.DEFAULT_SESSION_CACHE_SIZE) {
                    // To allow javax.net.ssl.sessionCacheSize system property usage
                    // see javax.net.ssl.SSLSessionContext.getSessionCacheSize doc
                    serverSessionContext.setSessionCacheSize(tlsConfig.sessionCacheSize());
                }
                // seconds
                serverSessionContext.setSessionTimeout((int) tlsConfig.sessionTimeout().toSeconds());
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
    protected SecureRandom secureRandom(TlsConfig tlsConfig) {
        if (tlsConfig.secureRandom().isPresent()) {
            return tlsConfig.secureRandom().get();
        }

        try {
            if (tlsConfig.secureRandomAlgorithm().isPresent() && tlsConfig.secureRandomProvider().isEmpty()) {
                return SecureRandom.getInstance(tlsConfig.secureRandomAlgorithm().get());
            }

            if (tlsConfig.secureRandomProvider().isPresent()) {
                if (tlsConfig.secureRandomAlgorithm().isEmpty()) {
                    throw new IllegalArgumentException("Invalid configuration of secure random. Provider is configured to "
                                                               + tlsConfig.secureRandomProvider().get()
                                                               + ", but algorithm is not specified");
                }
                return SecureRandom.getInstance(tlsConfig.secureRandomAlgorithm().get(), tlsConfig.secureRandomProvider().get());
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("invalid configuration for secure random, cannot create it", e);
        }

        return RANDOM.get();
    }

    /**
     * Build the key manager factory.
     *
     * @param target        the tls configuration
     * @param secureRandom  the secure random
     * @param privateKey    the private key for the key store
     * @param certificates the certificates for the keystore
     * @return a key manager factory instance
     */
    protected KeyManagerFactory buildKmf(TlsConfig target,
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
    protected KeyStore internalKeystore(TlsConfig tlsConfig) {
        try {
            String type = tlsConfig.internalKeystoreType().orElseGet(KeyStore::getDefaultType);

            KeyStore ks;
            if (tlsConfig.internalKeystoreProvider().isEmpty()) {
                ks = KeyStore.getInstance(type);
            } else {
                ks = KeyStore.getInstance(type, tlsConfig.internalKeystoreProvider().get());
            }

            ks.load(null, null);
            return ks;
        } catch (KeyStoreException
                 | NoSuchProviderException
                 | IOException
                 | NoSuchAlgorithmException
                 | CertificateException e) {
            throw new IllegalArgumentException("Invalid configuration of internal keystores. Provider: "
                                                       + tlsConfig.internalKeystoreProvider()
                                                       + ", type: " + tlsConfig.internalKeystoreType(), e);
        }
    }

    /**
     * Create a new trust manager factory based on the configuration (i.e., the algorithm and provider).
     *
     * @param tlsConfig TLS config
     * @return a new trust manager factory
     */
    protected TrustManagerFactory createTmf(TlsConfig tlsConfig) {
        try {
            String algorithm = tlsConfig.trustManagerFactoryAlgorithm().orElseGet(TrustManagerFactory::getDefaultAlgorithm);
            if (tlsConfig.trustManagerFactoryProvider().isEmpty()) {
                return TrustManagerFactory.getInstance(algorithm);
            } else {
                return TrustManagerFactory.getInstance(algorithm, tlsConfig.trustManagerFactoryProvider().get());
            }
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalArgumentException("Invalid configuration of trust manager factory. Provider: "
                                                       + tlsConfig.trustManagerFactoryProvider()
                                                       + ", algorithm: " + tlsConfig.trustManagerFactoryAlgorithm(), e);
        }
    }

    /**
     * Creates a trust all trust manager factory.
     *
     * @return a new trust manager factory trusting all
     */
    protected TrustManagerFactory trustAllTmf() {
        return new TrustAllManagerFactory();
    }

    // creates an internal keystore and initializes it with certificates discovered from configuration, then gets the trust
    // manager factory using tmf(TlsConfig)
    private TrustManagerFactory initTmf(TlsConfig tlsConfig) throws KeyStoreException {
        KeyStore ks = internalKeystore(tlsConfig);
        int i = 1;
        for (X509Certificate cert : tlsConfig.trust()) {
            ks.setCertificateEntry(String.valueOf(i), cert);
            i++;
        }
        TrustManagerFactory tmf = createTmf(tlsConfig);
        tmf.init(ks);
        return tmf;
    }

    // used by ConfiguredTlsManager to setup a TrustManagerFactory, that may be "trustAll", or based on configuration
    private TrustManagerFactory tmf(TlsConfig tlsConfig) throws KeyStoreException {
        if (tlsConfig.trustAll()) {
            return trustAllTmf();
        }

        if (!tlsConfig.trust().isEmpty()) {
            return initTmf(tlsConfig);
        }

        return null;
    }

    private void sslContext(TlsConfig tlsConfig) {
        if (tlsConfig.sslContext().isPresent()) {
            this.sslContext = tlsConfig.sslContext().get();
            return;
        }

        try {
            SecureRandom secureRandom = secureRandom(tlsConfig);
            KeyManagerFactory kmf = tlsConfig.privateKey()
                    .map(pk -> buildKmf(tlsConfig, secureRandom, pk, tlsConfig.privateKeyCertChain().toArray(new Certificate[0])))
                    .orElse(null);

            TrustManagerFactory tmf = tmf(tlsConfig);

            initSslContext(tlsConfig,
                           secureRandom,
                           kmf == null ? new KeyManager[0] : kmf.getKeyManagers(),
                           tmf == null ? new TrustManager[0] : tmf.getTrustManagers());
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Failed to create SSLContext", e);
        }
    }

    /**
     * Loads a key manager factory based on config.
     *
     * @param tlsConfig TLS configuration
     * @return a new key manager factory
     */
    private KeyManagerFactory kmf(TlsConfig tlsConfig) {
        try {
            String algorithm = tlsConfig.keyManagerFactoryAlgorithm().orElseGet(KeyManagerFactory::getDefaultAlgorithm);

            if (tlsConfig.keyManagerFactoryProvider().isPresent()) {
                return KeyManagerFactory.getInstance(algorithm, tlsConfig.keyManagerFactoryProvider().get());
            } else {
                return KeyManagerFactory.getInstance(algorithm);
            }
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalArgumentException("Invalid configuration of key manager factory. Provider: "
                                                       + tlsConfig.keyManagerFactoryProvider()
                                                       + ", algorithm: " + tlsConfig.keyManagerFactoryAlgorithm(), e);
        }
    }

    /**
     * Analyze the key managers and wrap the first X509 key manager with reloadable support.
     *
     * @param keyManagers used key managers
     * @return the same managers, except the first X509 one is wrapped
     */
    private KeyManager[] wrapX509KeyManagers(KeyManager[] keyManagers) {
        KeyManager[] toReturn = new KeyManager[keyManagers.length];
        System.arraycopy(keyManagers, 0, toReturn, 0, toReturn.length);
        for (int i = 0; i < keyManagers.length; i++) {
            KeyManager keyManager = keyManagers[i];
            if (keyManager instanceof X509KeyManager x509KeyManager) {
                this.keyManager = x509KeyManager;
                this.reloadableKeyManager = TlsReloadableX509KeyManager.create(x509KeyManager);
                toReturn[i] = reloadableKeyManager;
                return toReturn;
            }
        }
        this.reloadableKeyManager = new TlsReloadableX509KeyManager.NotReloadableKeyManager();
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
        System.arraycopy(trustManagers, 0, toReturn, 0, toReturn.length);
        for (int i = 0; i < trustManagers.length; i++) {
            TrustManager trustManager = trustManagers[i];
            if (trustManager instanceof X509TrustManager x509TrustManager) {
                this.trustManager = x509TrustManager;
                this.reloadableTrustManager = TlsReloadableX509TrustManager.create(x509TrustManager);
                toReturn[i] = reloadableTrustManager;
                return toReturn;
            }
        }
        this.reloadableTrustManager = new TlsReloadableX509TrustManager.NotReloadableTrustManager();
        return toReturn;
    }
}
