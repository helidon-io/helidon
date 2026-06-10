/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertPathBuilder;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.CertPathTrustManagerParameters;
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
    private static final System.Logger LOGGER = System.getLogger(ConfiguredTlsManager.class.getName());
    // secure random cannot be stored in native image, it must
    // be initialized at runtime
    private static final LazyValue<SecureRandom> RANDOM = LazyValue.create(SecureRandom::new);
    private final String name;
    private final String type;
    private final ReentrantLock stateLock = new ReentrantLock();

    private volatile TlsManagerState state = new TlsManagerState();

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
        return state.sslContext;
    }

    @Override // TlsManager
    public void init(TlsConfig tlsConfig) {
        sslContext(tlsConfig);
    }

    @Override // TlsManager
    @Deprecated(forRemoval = true, since = "27.0.0")
    @SuppressWarnings("removal")
    public void reload(Tls tls) {
        Tls.validateReloadSource(tls);
        reload(tls.keyManager(), tls.trustManager());
    }

    @Override // TlsManager
    public void reload(TlsMaterial material) {
        Objects.requireNonNull(material, "material");
        validateMaterial(material);
        try {
            reload(keyManager(material), trustManager(material));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Failed to create TLS material", e);
        }
    }

    @Override // TlsManager
    public Optional<X509KeyManager> keyManager() {
        return Optional.ofNullable(state.keyManager);
    }

    @Override // TlsManager
    public Optional<X509TrustManager> trustManager() {
        return Optional.ofNullable(state.trustManager);
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
        stateLock.lock();
        try {
            TlsManagerState current = state;
            X509KeyManager keyManagerToReload = keyManager.orElse(null);
            X509TrustManager trustManagerToReload = trustManager.orElse(null);

            if (keyManagerToReload != null) {
                validateReload(current.reloadableKeyManager, keyManagerToReload);
            }
            if (trustManagerToReload != null) {
                validateReload(current.reloadableTrustManager, trustManagerToReload);
            }

            if (keyManagerToReload != null) {
                current.reloadableKeyManager.reload(keyManagerToReload);
            }
            if (trustManagerToReload != null) {
                current.reloadableTrustManager.reload(trustManagerToReload);
            }

            if (keyManagerToReload != null || trustManagerToReload != null) {
                state = new TlsManagerState(current.sslContext,
                                            keyManagerToReload == null ? current.keyManager : keyManagerToReload,
                                            current.reloadableKeyManager,
                                            trustManagerToReload == null ? current.trustManager : trustManagerToReload,
                                            current.reloadableTrustManager);
            }
        } finally {
            stateLock.unlock();
        }
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
        stateLock.lock();
        try {
            initSslContextLocked(tlsConfig, secureRandom, keyManagers, trustManagers);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Failed to create SSLContext", e);
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Load secure random.
     *
     * @param tlsConfig TLS configuration
     * @return secure random
     */
    protected SecureRandom secureRandom(TlsConfig tlsConfig) {
        return secureRandom(tlsConfig.secureRandom(),
                            tlsConfig.secureRandomAlgorithm(),
                            tlsConfig.secureRandomProvider());
    }

    /**
     * Load secure random.
     *
     * @param material TLS material
     * @return secure random
     */
    protected SecureRandom secureRandom(TlsMaterial material) {
        return secureRandom(material.secureRandom(),
                            material.secureRandomAlgorithm(),
                            material.secureRandomProvider());
    }

    private SecureRandom secureRandom(Optional<SecureRandom> configured,
                                      Optional<String> algorithm,
                                      Optional<String> provider) {
        if (configured.isPresent()) {
            return configured.get();
        }
        try {
            if (algorithm.isPresent() && provider.isEmpty()) {
                return SecureRandom.getInstance(algorithm.get());
            }

            if (provider.isPresent()) {
                if (algorithm.isEmpty()) {
                    throw new IllegalArgumentException("Invalid configuration of secure random. Provider is configured to "
                                                               + provider.get()
                                                               + ", but algorithm is not specified");
                }
                return SecureRandom.getInstance(algorithm.get(), provider.get());
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("invalid configuration for secure random, cannot create it", e);
        }

        return RANDOM.get();
    }

    /**
     * Build the key manager factory.
     *
     * @param target       the tls configuration
     * @param secureRandom the secure random
     * @param privateKey   the private key for the key store
     * @param certificates the certificates for the keystore
     * @return a key manager factory instance
     */
    protected KeyManagerFactory buildKmf(TlsConfig target,
                                         SecureRandom secureRandom,
                                         PrivateKey privateKey,
                                         Certificate[] certificates) {
        return buildKmf(secureRandom,
                        privateKey,
                        certificates,
                        internalKeystore(target),
                        kmf(target));
    }

    /**
     * Build the key manager factory.
     *
     * @param target       the TLS material
     * @param secureRandom the secure random
     * @param privateKey   the private key for the key store
     * @param certificates the certificates for the keystore
     * @return a key manager factory instance
     */
    protected KeyManagerFactory buildKmf(TlsMaterial target,
                                         SecureRandom secureRandom,
                                         PrivateKey privateKey,
                                         Certificate[] certificates) {
        return buildKmf(secureRandom,
                        privateKey,
                        certificates,
                        internalKeystore(target),
                        kmf(target));
    }

    private KeyManagerFactory buildKmf(SecureRandom secureRandom,
                                       PrivateKey privateKey,
                                       Certificate[] certificates,
                                       KeyStore keyStore,
                                       KeyManagerFactory keyManagerFactory) {
        byte[] passwordBytes = new byte[64];
        secureRandom.nextBytes(passwordBytes);
        char[] password = Base64.getEncoder().encodeToString(passwordBytes).toCharArray();

        try {
            keyStore.setKeyEntry("key",
                                 privateKey,
                                 password,
                                 certificates);

            keyManagerFactory.init(keyStore, password);
            return keyManagerFactory;
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
        return internalKeystore(tlsConfig.internalKeystoreType(),
                                tlsConfig.internalKeystoreProvider());
    }

    /**
     * Creates an internal keystore and loads it with no password and no data.
     *
     * @param material TLS material
     * @return a new keystore
     */
    protected KeyStore internalKeystore(TlsMaterial material) {
        return internalKeystore(material.internalKeystoreType(),
                                material.internalKeystoreProvider());
    }

    private KeyStore internalKeystore(Optional<String> typeOption, Optional<String> providerOption) {
        try {
            String type = typeOption.orElseGet(KeyStore::getDefaultType);

            KeyStore ks;
            if (providerOption.isEmpty()) {
                ks = KeyStore.getInstance(type);
            } else {
                ks = KeyStore.getInstance(type, providerOption.get());
            }

            ks.load(null, null);
            return ks;
        } catch (KeyStoreException
                 | NoSuchProviderException
                 | IOException
                 | NoSuchAlgorithmException
                 | CertificateException e) {
            throw new IllegalArgumentException("Invalid configuration of internal keystores. Provider: "
                                                       + providerOption
                                                       + ", type: " + typeOption, e);
        }
    }

    /**
     * Create a new trust manager factory based on the configuration (i.e., the algorithm and provider).
     *
     * @param tlsConfig TLS config
     * @return a new trust manager factory
     */
    protected TrustManagerFactory createTmf(TlsConfig tlsConfig) {
        return createTmf(tlsConfig.trustManagerFactoryAlgorithm(),
                         tlsConfig.trustManagerFactoryProvider());
    }

    /**
     * Create a new trust manager factory based on the TLS material (i.e., the algorithm and provider).
     *
     * @param material TLS material
     * @return a new trust manager factory
     */
    protected TrustManagerFactory createTmf(TlsMaterial material) {
        return createTmf(material.trustManagerFactoryAlgorithm(),
                         material.trustManagerFactoryProvider());
    }

    private TrustManagerFactory createTmf(Optional<String> algorithmOption, Optional<String> providerOption) {
        try {
            String algorithm = algorithmOption.orElseGet(TrustManagerFactory::getDefaultAlgorithm);
            if (providerOption.isEmpty()) {
                return TrustManagerFactory.getInstance(algorithm);
            } else {
                return TrustManagerFactory.getInstance(algorithm, providerOption.get());
            }
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalArgumentException("Invalid configuration of trust manager factory. Provider: "
                                                       + providerOption
                                                       + ", algorithm: " + algorithmOption, e);
        }
    }

    /**
     * Perform initialization of the {@link TrustManagerFactory} based on the provided TLS configuration.
     *
     * @param tmf       trust manager factory to be initialized
     * @param keyStore  keystore
     * @param tlsConfig tls configuration
     */
    protected void initializeTmf(TrustManagerFactory tmf, KeyStore keyStore, TlsConfig tlsConfig) {
        initializeTmf(tmf, keyStore, tlsConfig.revocation());
    }

    /**
     * Perform initialization of the {@link TrustManagerFactory} based on the provided TLS material.
     *
     * @param tmf      trust manager factory to be initialized
     * @param keyStore keystore
     * @param material TLS material
     */
    protected void initializeTmf(TrustManagerFactory tmf, KeyStore keyStore, TlsMaterial material) {
        initializeTmf(tmf, keyStore, material.revocation());
    }

    private void initializeTmf(TrustManagerFactory tmf, KeyStore keyStore, Optional<RevocationConfig> revocation) {
        try {
            if (revocation.isPresent()) {
                RevocationConfig revocationConfig = revocation.get();
                if (revocationConfig.enabled()) {
                    CertPathBuilder cpb = null;
                    cpb = CertPathBuilder.getInstance("PKIX");
                    PKIXRevocationChecker rc = (PKIXRevocationChecker) cpb.getRevocationChecker();
                    Set<PKIXRevocationChecker.Option> options = new HashSet<>();
                    if (revocationConfig.preferCrlOverOcsp()) {
                        options.add(PKIXRevocationChecker.Option.PREFER_CRLS);
                    }
                    if (revocationConfig.checkOnlyEndEntity()) {
                        options.add(PKIXRevocationChecker.Option.ONLY_END_ENTITY);
                    }
                    if (!revocationConfig.fallbackEnabled()) {
                        options.add(PKIXRevocationChecker.Option.NO_FALLBACK);
                    }
                    if (revocationConfig.softFailEnabled()) {
                        options.add(PKIXRevocationChecker.Option.SOFT_FAIL);
                    }
                    rc.setOptions(options);
                    revocationConfig.ocspResponderUri().ifPresent(rc::setOcspResponder);
                    PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(keyStore, new X509CertSelector());
                    pkixParams.addCertPathChecker(rc);
                    tmf.init(new CertPathTrustManagerParameters(pkixParams));
                    return;
                }
            }
            tmf.init(keyStore);
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | KeyStoreException e) {
            throw new IllegalArgumentException("Failed to initialize TrustManagerFactory", e);
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

    // for tests
    TlsReloadableX509KeyManager reloadableKeyManager() {
        return state.reloadableKeyManager;
    }

    private static void validateMaterial(TlsMaterial material) {
        boolean hasPrivateKey = material.privateKey().isPresent();
        boolean hasPrivateKeyCertChain = !material.privateKeyCertChain().isEmpty();
        boolean hasTrust = !material.trust().isEmpty();

        if (hasPrivateKey && !hasPrivateKeyCertChain) {
            throw new IllegalArgumentException("TLS material with private key must also define the certificate chain");
        }
        if (!hasPrivateKey && hasPrivateKeyCertChain) {
            throw new IllegalArgumentException("TLS material certificate chain requires a private key");
        }
        if (material.trustAll() && hasTrust) {
            throw new IllegalArgumentException("TLS material cannot combine trustAll and trust certificates");
        }
        if (!hasPrivateKey && !hasTrust && !material.trustAll()) {
            throw new IllegalArgumentException("TLS material must define private key or trust material");
        }
    }

    private Optional<X509KeyManager> keyManager(TlsMaterial material) {
        Optional<PrivateKey> privateKey = material.privateKey();
        if (privateKey.isEmpty()) {
            return Optional.empty();
        }

        SecureRandom secureRandom = secureRandom(material);
        KeyManagerFactory kmf = buildKmf(material,
                                         secureRandom,
                                         privateKey.get(),
                                         material.privateKeyCertChain().toArray(new Certificate[0]));
        return Optional.of(x509KeyManager(kmf.getKeyManagers())
                                   .orElseThrow(() -> new IllegalArgumentException(
                                           "Configured key manager factory did not provide an X509KeyManager")));
    }

    private Optional<X509TrustManager> trustManager(TlsMaterial material) throws KeyStoreException {
        TrustManagerFactory tmf = tmf(material);
        if (tmf == null) {
            return Optional.empty();
        }
        return Optional.of(x509TrustManager(tmf.getTrustManagers())
                                   .orElseThrow(() -> new IllegalArgumentException(
                                           "Configured trust manager factory did not provide an X509TrustManager")));
    }

    private static void validateReload(TlsReloadableX509KeyManager reloadableKeyManager, X509KeyManager keyManager) {
        if (reloadableKeyManager instanceof TlsReloadableX509KeyManager.NotReloadableKeyManager) {
            reloadableKeyManager.reload(keyManager);
        }
        TlsReloadableX509KeyManager.assertValid(keyManager);
    }

    private static void validateReload(TlsReloadableX509TrustManager reloadableTrustManager,
                                       X509TrustManager trustManager) {
        if (reloadableTrustManager instanceof TlsReloadableX509TrustManager.NotReloadableTrustManager) {
            reloadableTrustManager.reload(trustManager);
        }
        TlsReloadableX509TrustManager.assertValid(trustManager);
    }

    private static Optional<X509KeyManager> x509KeyManager(KeyManager[] keyManagers) {
        for (KeyManager keyManager : keyManagers) {
            if (keyManager instanceof X509KeyManager x509KeyManager) {
                return Optional.of(x509KeyManager);
            }
        }
        return Optional.empty();
    }

    private static Optional<X509TrustManager> x509TrustManager(TrustManager[] trustManagers) {
        for (TrustManager trustManager : trustManagers) {
            if (trustManager instanceof X509TrustManager x509TrustManager) {
                return Optional.of(x509TrustManager);
            }
        }
        return Optional.empty();
    }

    private void initSslContextLocked(TlsConfig tlsConfig,
                                      SecureRandom secureRandom,
                                      KeyManager[] keyManagers,
                                      TrustManager[] trustManagers) throws GeneralSecurityException {
        TrustManager[] tm;
        KeyManager[] km;
        KeyManagerState keyManagerState;
        TrustManagerState trustManagerState;

        if (keyManagers.length == 0) {
            keyManagerState = noReloadableKeyManagerState(null);
            km = null;
        } else {
            keyManagerState = wrapX509KeyManagers(keyManagers);
            km = keyManagerState.keyManagers;
        }

        if (trustManagers.length == 0) {
            trustManagerState = noReloadableTrustManagerState(null);
            tm = null;
        } else {
            trustManagerState = wrapX509TrustManagers(trustManagers);
            tm = trustManagerState.trustManagers;
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

        setSslContext(sslContext, keyManagerState, trustManagerState);
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
        initializeTmf(tmf, ks, tlsConfig);
        return tmf;
    }

    // creates an internal keystore and initializes it with certificates discovered from TLS material, then gets the trust
    // manager factory using tmf(TlsMaterial)
    private TrustManagerFactory initTmf(TlsMaterial material) throws KeyStoreException {
        KeyStore ks = internalKeystore(material);
        int i = 1;
        for (X509Certificate cert : material.trust()) {
            ks.setCertificateEntry(String.valueOf(i), cert);
            i++;
        }
        TrustManagerFactory tmf = createTmf(material);
        initializeTmf(tmf, ks, material);
        return tmf;
    }

    // used by ConfiguredTlsManager to setup a TrustManagerFactory, that may be "trustAll", or based on configuration
    private TrustManagerFactory tmf(TlsConfig tlsConfig) throws KeyStoreException {
        if (tlsConfig.trustAll()) {
            logTrustAllWarning();
            return trustAllTmf();
        }

        if (!tlsConfig.trust().isEmpty()) {
            return initTmf(tlsConfig);
        }

        return null;
    }

    // used by ConfiguredTlsManager to setup a TrustManagerFactory, that may be "trustAll", or based on TLS material
    private TrustManagerFactory tmf(TlsMaterial material) throws KeyStoreException {
        if (material.trustAll()) {
            logTrustAllWarning();
            return trustAllTmf();
        }

        if (!material.trust().isEmpty()) {
            return initTmf(material);
        }

        return null;
    }

    private static void logTrustAllWarning() {
        LOGGER.log(System.Logger.Level.WARNING,
                   "Using a trust manager that trusts ANY certificate. Never use this in production. "
                           + "This is a significant warning, do not ignore.");
    }

    private void sslContext(TlsConfig tlsConfig) {
        if (tlsConfig.sslContext().isPresent()) {
            stateLock.lock();
            try {
                setSslContext(tlsConfig.sslContext().get(),
                              noReloadableKeyManagerState(null),
                              noReloadableTrustManagerState(null));
            } finally {
                stateLock.unlock();
            }
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
        return kmf(tlsConfig.keyManagerFactoryAlgorithm(),
                   tlsConfig.keyManagerFactoryProvider());
    }

    /**
     * Loads a key manager factory based on TLS material.
     *
     * @param material TLS material
     * @return a new key manager factory
     */
    private KeyManagerFactory kmf(TlsMaterial material) {
        return kmf(material.keyManagerFactoryAlgorithm(),
                   material.keyManagerFactoryProvider());
    }

    private KeyManagerFactory kmf(Optional<String> algorithmOption, Optional<String> providerOption) {
        try {
            String algorithm = algorithmOption.orElseGet(KeyManagerFactory::getDefaultAlgorithm);

            if (providerOption.isPresent()) {
                return KeyManagerFactory.getInstance(algorithm, providerOption.get());
            } else {
                return KeyManagerFactory.getInstance(algorithm);
            }
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalArgumentException("Invalid configuration of key manager factory. Provider: "
                                                       + providerOption
                                                       + ", algorithm: " + algorithmOption, e);
        }
    }

    /**
     * Analyze the key managers and wrap the first X509 key manager with reloadable support.
     *
     * @param keyManagers used key managers
     * @return the same managers, except the first X509 one is wrapped
     */
    private KeyManagerState wrapX509KeyManagers(KeyManager[] keyManagers) {
        KeyManager[] toReturn = new KeyManager[keyManagers.length];
        System.arraycopy(keyManagers, 0, toReturn, 0, toReturn.length);
        for (int i = 0; i < keyManagers.length; i++) {
            KeyManager keyManager = keyManagers[i];
            if (keyManager instanceof X509KeyManager x509KeyManager) {
                TlsReloadableX509KeyManager reloadableKeyManager = TlsReloadableX509KeyManager.create(x509KeyManager);
                toReturn[i] = reloadableKeyManager;
                return new KeyManagerState(toReturn, x509KeyManager, reloadableKeyManager);
            }
        }
        return noReloadableKeyManagerState(toReturn);
    }

    /**
     * Analyze the trust managers and wrap the first X509 trust manager with reloadable support.
     *
     * @param trustManagers used trust managers
     * @return the same managers, except the first X509 one is wrapped
     */
    private TrustManagerState wrapX509TrustManagers(TrustManager[] trustManagers) {
        TrustManager[] toReturn = new TrustManager[trustManagers.length];
        System.arraycopy(trustManagers, 0, toReturn, 0, toReturn.length);
        for (int i = 0; i < trustManagers.length; i++) {
            TrustManager trustManager = trustManagers[i];
            if (trustManager instanceof X509TrustManager x509TrustManager) {
                TlsReloadableX509TrustManager reloadableTrustManager = TlsReloadableX509TrustManager.create(x509TrustManager);
                toReturn[i] = reloadableTrustManager;
                return new TrustManagerState(toReturn, x509TrustManager, reloadableTrustManager);
            }
        }
        return noReloadableTrustManagerState(toReturn);
    }

    private KeyManagerState noReloadableKeyManagerState(KeyManager[] keyManagers) {
        return new KeyManagerState(keyManagers, null, new TlsReloadableX509KeyManager.NotReloadableKeyManager());
    }

    private TrustManagerState noReloadableTrustManagerState(TrustManager[] trustManagers) {
        return new TrustManagerState(trustManagers, null, new TlsReloadableX509TrustManager.NotReloadableTrustManager());
    }

    private void setSslContext(SSLContext sslContext,
                               KeyManagerState keyManagerState,
                               TrustManagerState trustManagerState) {
        this.state = new TlsManagerState(sslContext,
                                         keyManagerState.keyManager,
                                         keyManagerState.reloadableKeyManager,
                                         trustManagerState.trustManager,
                                         trustManagerState.reloadableTrustManager);
    }

    private static final class TlsManagerState {
        private final SSLContext sslContext;
        private final X509KeyManager keyManager;
        private final TlsReloadableX509KeyManager reloadableKeyManager;
        private final X509TrustManager trustManager;
        private final TlsReloadableX509TrustManager reloadableTrustManager;

        private TlsManagerState() {
            this(null,
                 null,
                 new TlsReloadableX509KeyManager.NotReloadableKeyManager(),
                 null,
                 new TlsReloadableX509TrustManager.NotReloadableTrustManager());
        }

        private TlsManagerState(SSLContext sslContext,
                                X509KeyManager keyManager,
                                TlsReloadableX509KeyManager reloadableKeyManager,
                                X509TrustManager trustManager,
                                TlsReloadableX509TrustManager reloadableTrustManager) {
            this.sslContext = sslContext;
            this.keyManager = keyManager;
            this.reloadableKeyManager = reloadableKeyManager;
            this.trustManager = trustManager;
            this.reloadableTrustManager = reloadableTrustManager;
        }
    }

    private static final class KeyManagerState {
        private final KeyManager[] keyManagers;
        private final X509KeyManager keyManager;
        private final TlsReloadableX509KeyManager reloadableKeyManager;

        private KeyManagerState(KeyManager[] keyManagers,
                                X509KeyManager keyManager,
                                TlsReloadableX509KeyManager reloadableKeyManager) {
            this.keyManagers = keyManagers;
            this.keyManager = keyManager;
            this.reloadableKeyManager = reloadableKeyManager;
        }
    }

    private static final class TrustManagerState {
        private final TrustManager[] trustManagers;
        private final X509TrustManager trustManager;
        private final TlsReloadableX509TrustManager reloadableTrustManager;

        private TrustManagerState(TrustManager[] trustManagers,
                                  X509TrustManager trustManager,
                                  TlsReloadableX509TrustManager reloadableTrustManager) {
            this.trustManagers = trustManagers;
            this.trustManager = trustManager;
            this.reloadableTrustManager = reloadableTrustManager;
        }
    }
}
