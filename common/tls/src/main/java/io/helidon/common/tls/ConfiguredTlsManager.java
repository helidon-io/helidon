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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;

/**
 * The default configured {@link TlsManager} implementation.
 */
public class ConfiguredTlsManager implements TlsManager {
    // secure random cannot be stored in native image, it must
    // be initialized at runtime
    private static final LazyValue<SecureRandom> RANDOM = LazyValue.create(SecureRandom::new);

    private final String name;
    private final String type;
    private final AtomicInteger kmCreateCount = new AtomicInteger();
    private final AtomicInteger tmCreateCount = new AtomicInteger();
    private volatile TlsInfo tlsInfo;
    private volatile Tls tls;
    private volatile boolean enabled;
    private volatile Config config;

    ConfiguredTlsManager(TlsConfig.BuilderBase<?, ?> target) {
        this("@default",
             "tls-manager",
             target.config().orElse(null),
             sslContext(target),
             target.tlsInfo().orElseThrow(() -> new IllegalStateException("Expected to have a tls info")));
    }

    /**
     * Configured tls manager constructor.
     *
     * @param name the name
     * @param type the type
     * @param config the config (which can be null)
     * @param sslContext the ssl context (mutable/reloadable internally; and can be initially null)
     * @param tlsInfo the tls info (mutable/reloadable internally; and can be initially null)
     */
    protected ConfiguredTlsManager(String name,
                                   String type,
                                   Config config,
                                   SSLContext sslContext,
                                   TlsInfo tlsInfo) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.config = config;
        this.tlsInfo = tlsInfo;
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
    public void decorate(TlsConfig.BuilderBase<?, ?> target) {
        sslParameters(target);
        try {
            secureRandom(target);
            sslContext(target);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize", e);
        }
    }

    @Override // TlsManager
    public void init(Tls tls) {
        this.tls = tls;
        this.tlsInfo = tls.prototype().tlsInfo().orElseThrow();
        this.enabled = tls.enabled();
    }

    @Override // TlsManager
    public void reload(Tls tls) {
        this.enabled = tls.enabled();
        if (enabled) {
            for (TlsReloadableComponent reloadableComponent : reloadableComponents()) {
                reloadableComponent.reload(tls);
            }
        }
    }

    @Override // TlsManager
    public X509KeyManager keyManager() {
        if (tlsInfo != null) {
            return tlsInfo.keyManager();
        }

        if (1 != kmCreateCount.incrementAndGet()) {
            throw new IllegalStateException("Expected to only create once");
        }
        return TlsReloadableX509KeyManager.create();
    }

    @Override // TlsManager
    public X509TrustManager trustManager() {
        if (tlsInfo != null) {
            return tls.trustManager();
        }

        if (1 != tmCreateCount.incrementAndGet()) {
            throw new IllegalStateException("Expected to only create once");
        }
        return TlsReloadableX509TrustManager.create();
    }

    @Override // TlsManager
    public List<TlsReloadableComponent> reloadableComponents() {
        return List.copyOf(tlsInfo.reloadableComponents());
    }

    /**
     * Returns {@code true} if this manager is enabled.
     *
     * @return flag indicating whether this manager is enabled
     * @see TlsConfigBlueprint#enabled()
     */
    protected boolean enabled() {
        return enabled;
    }

    /**
     * The backing config for this manager. Note that the instance can change at any instance if the config is observed to change.
     *
     * @return backing config for this manager
     */
    protected Optional<Config> config() {
        return Optional.ofNullable(config);
    }

    /**
     * Sets the backing (possibly updated) configuration for this manager.
     *
     * @param config the new config
     */
    protected void config(Config config) {
        this.config = config;
        maybeReload();
    }

    /**
     * This method can be overridden to provide checks for reloading the underlying keys and certificates for the mutable
     * ssl context.
     */
    protected void maybeReload() {
        // nothing to do in the base
    }

    static SSLContext sslContext(TlsConfig.BuilderBase<?, ?> target) {
        if (target.sslContext().isPresent()) {
            if (target.tlsInfo().isEmpty()) {
                target.tlsInfo(new TlsInternalInfo(true, List.of(), null, null));
            }
            return target.sslContext().get();
        }

        try {
            SecureRandom secureRandom = secureRandom(target);
            KeyManagerFactory kmf = target.privateKey().map(pk -> buildKmf(target, secureRandom, pk)).orElse(null);

            TrustManagerFactory tmf;
            if (target.trustAll()) {
                tmf = buildTrustAllTmf();
            } else {
                if (target.trust().isEmpty()) {
                    tmf = null;
                } else {
                    tmf = buildTmf(target);
                }
            }

            SSLContext sslContext;

            if (target.provider().isPresent()) {
                sslContext = SSLContext.getInstance(target.protocol(), target.provider().get());
            } else {
                sslContext = SSLContext.getInstance(target.protocol());
            }

            List<TlsReloadableComponent> reloadable = new ArrayList<>();
            TrustManager[] tm = null;
            X509TrustManager tmOriginal = null;
            KeyManager[] km = null;
            X509KeyManager kmOriginal = null;

            ReloadableData<KeyManager, X509KeyManager> kmData;
            if (kmf != null) {
                kmData = wrapX509KeyManagers(reloadable, kmf.getKeyManagers());
            } else if (target.enabled() && target.manager().isPresent()) {
                kmData = wrapX509KeyManagers(reloadable,
                                             new X509KeyManager[] {target.manager().get().keyManager()});
            } else {
                kmData = null;
            }
            if (kmData != null) {
                km = kmData.result();
                kmOriginal = kmData.original();
            }

            ReloadableData<TrustManager, X509TrustManager> tmData;
            if (tmf != null) {
                tmData = wrapX509TrustManagers(reloadable, tmf.getTrustManagers());
            } else if (target.enabled() && target.manager().isPresent()) {
                tmData = wrapX509TrustManagers(reloadable,
                                               new X509TrustManager[] {target.manager().get().trustManager()});
            } else {
                tmData = null;
            }
            if (tmData != null) {
                tm = tmData.result();
                tmOriginal = tmData.original();
            }

            sslContext.init(km, tm, secureRandom);
            target.tlsInfo(TlsInfo.create(false, reloadable, tmOriginal, kmOriginal));

            SSLSessionContext serverSessionContext = sslContext.getServerSessionContext();
            if (serverSessionContext != null) {
                serverSessionContext.setSessionCacheSize(target.sessionCacheSize());
                // seconds
                serverSessionContext.setSessionTimeout((int) target.sessionTimeout().toSeconds());
            }

            target.sslContext(sslContext);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Failed to create SSLContext", e);
        }
    }

    static TrustManagerFactory buildTrustAllTmf() {
        return new TrustAllManagerFactory();
    }

    static TrustManagerFactory buildTmf(TlsConfig.BuilderBase<?, ?> target) throws KeyStoreException {
        KeyStore ks = keystore(target);
        int i = 1;
        for (X509Certificate cert : target.trust()) {
            ks.setCertificateEntry(String.valueOf(i), cert);
            i++;
        }
        TrustManagerFactory tmf = tmf(target);
        tmf.init(ks);
        return tmf;
    }

    static SecureRandom secureRandom(TlsConfig.BuilderBase<?, ?> target)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        if (target.secureRandom().isPresent()) {
            return target.secureRandom().get();
        }

        if (target.secureRandomAlgorithm().isPresent() && target.secureRandomProvider().isEmpty()) {
            return SecureRandom.getInstance(target.secureRandomAlgorithm().get());
        }

        if (target.secureRandomProvider().isPresent()) {
            if (target.secureRandomAlgorithm().isEmpty()) {
                throw new IllegalArgumentException("Invalid configuration of secure random. Provider is configured to "
                                                           + target.secureRandomProvider().get()
                                                           + ", but algorithm is not specified");
            }
            return SecureRandom.getInstance(target.secureRandomAlgorithm().get(), target.secureRandomProvider().get());
        }

        SecureRandom secureRandom = RANDOM.get();
        target.secureRandom(secureRandom);
        return secureRandom;
    }

    static void sslParameters(TlsConfig.BuilderBase<?, ?> target) {
        if (target.sslParameters().isPresent()) {
            return;
        }
        SSLParameters parameters = new SSLParameters();

        if (!target.applicationProtocols().isEmpty()) {
            parameters.setApplicationProtocols(target.applicationProtocols().toArray(new String[0]));
        }
        if (!target.enabledProtocols().isEmpty()) {
            parameters.setProtocols(target.enabledProtocols().toArray(new String[0]));
        }
        if (!target.enabledCipherSuites().isEmpty()) {
            parameters.setCipherSuites(target.enabledCipherSuites().toArray(new String[0]));
        }
        if (Tls.ENDPOINT_IDENTIFICATION_NONE.equals(target.endpointIdentificationAlgorithm())) {
            parameters.setEndpointIdentificationAlgorithm("");
        } else {
            parameters.setEndpointIdentificationAlgorithm(target.endpointIdentificationAlgorithm());
        }

        switch (target.clientAuth()) {
        case REQUIRED -> {
            parameters.setNeedClientAuth(true);
        }
        case OPTIONAL -> {
            parameters.setWantClientAuth(true);
        }
        default -> {
        }
        }

        target.sslParameters(parameters);
    }

    static KeyManagerFactory buildKmf(TlsConfig.BuilderBase<?, ?> target,
                                      SecureRandom secureRandom,
                                      PrivateKey privateKey) {
        byte[] passwordBytes = new byte[64];
        secureRandom.nextBytes(passwordBytes);
        char[] password = Base64.getEncoder().encodeToString(passwordBytes).toCharArray();

        try {
            KeyStore ks = keystore(target);
            ks.setKeyEntry("key",
                           privateKey,
                           password,
                           target.privateKeyCertChain().toArray(new Certificate[0]));

            KeyManagerFactory kmf = kmf(target);
            kmf.init(ks, password);
            return kmf;
        } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("invalid configuration for key management factory, cannot create factory", e);
        }
    }

    static KeyManagerFactory kmf(TlsConfig.BuilderBase<?, ?> target) {
        try {
            String algorithm = target.keyManagerFactoryAlgorithm().orElseGet(KeyManagerFactory::getDefaultAlgorithm);

            if (target.keyManagerFactoryProvider().isPresent()) {
                return KeyManagerFactory.getInstance(algorithm, target.keyManagerFactoryProvider().get());
            } else {
                return KeyManagerFactory.getInstance(algorithm);
            }
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalArgumentException("Invalid configuration of key manager factory. Provider: "
                                                       + target.keyManagerFactoryProvider()
                                                       + ", algorithm: " + target.keyManagerFactoryAlgorithm(), e);
        }
    }

    static KeyStore keystore(TlsConfig.BuilderBase<?, ?> target) {
        try {
            String type = target.internalKeystoreType().orElseGet(KeyStore::getDefaultType);

            KeyStore ks;
            if (target.internalKeystoreProvider().isEmpty()) {
                ks = KeyStore.getInstance(type);
            } else {
                ks = KeyStore.getInstance(type, target.internalKeystoreProvider().get());
            }

            ks.load(null, null);
            return ks;
        } catch (KeyStoreException
                 | NoSuchProviderException
                 | IOException
                 | NoSuchAlgorithmException
                 | CertificateException e) {
            throw new IllegalArgumentException("Invalid configuration of internal keystores. Provider: "
                                                       + target.internalKeystoreProvider()
                                                       + ", type: " + target.internalKeystoreType(), e);
        }
    }

    static TrustManagerFactory tmf(TlsConfig.BuilderBase<?, ?> target) {
        try {
            String algorithm = target.trustManagerFactoryAlgorithm().orElseGet(TrustManagerFactory::getDefaultAlgorithm);
            if (target.trustManagerFactoryProvider().isEmpty()) {
                return TrustManagerFactory.getInstance(algorithm);
            } else {
                return TrustManagerFactory.getInstance(algorithm, target.trustManagerFactoryProvider().get());
            }
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalArgumentException("Invalid configuration of trust manager factory. Provider: "
                                                       + target.trustManagerFactoryProvider()
                                                       + ", algorithm: " + target.trustManagerFactoryAlgorithm(), e);
        }
    }

    static ReloadableData<KeyManager, X509KeyManager> wrapX509KeyManagers(List<TlsReloadableComponent> reloadable,
                                                                          KeyManager[] keyManagers) {
        KeyManager[] toReturn = new KeyManager[keyManagers.length];
        System.arraycopy(keyManagers, 0, toReturn, 0, toReturn.length);
        for (int i = 0; i < keyManagers.length; i++) {
            KeyManager keyManager = keyManagers[i];
            if (keyManager instanceof X509KeyManager x509KeyManager) {
                TlsReloadableX509KeyManager wrappedKeyManager = TlsReloadableX509KeyManager.create(x509KeyManager);
                reloadable.add(wrappedKeyManager);
                toReturn[i] = wrappedKeyManager;
                return new ReloadableData<>(toReturn, x509KeyManager);
            }
        }
        reloadable.add(new TlsInternalReloadableX509KeyManager.NotReloadableKeyManager());
        return new ReloadableData<>(toReturn, null);
    }

    static ReloadableData<TrustManager, X509TrustManager> wrapX509TrustManagers(List<TlsReloadableComponent> reloadable,
                                                                                TrustManager[] trustManagers) {
        TrustManager[] toReturn = new TrustManager[trustManagers.length];
        System.arraycopy(trustManagers, 0, toReturn, 0, toReturn.length);
        for (int i = 0; i < trustManagers.length; i++) {
            TrustManager trustManager = trustManagers[i];
            if (trustManager instanceof X509TrustManager x509TrustManager) {
                var wrappedTrustManager = TlsReloadableX509TrustManager.create(x509TrustManager);
                reloadable.add(wrappedTrustManager);
                toReturn[i] = wrappedTrustManager;
                return new ReloadableData<>(toReturn, x509TrustManager);
            }
        }
        reloadable.add(new TlsInternalReloadableX509TrustManager.NotReloadableTrustManager());
        return new ReloadableData<>(toReturn, null);
    }

    record ReloadableData<T, U>(T[] result, U original) {
    }

}
