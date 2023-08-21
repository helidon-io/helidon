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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

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

/**
 * The default configured {@link TlsManager} implementation.
 */
public class ConfiguredTlsManager implements TlsManager {
    // secure random cannot be stored in native image, it must
    // be initialized at runtime
    private static final LazyValue<SecureRandom> RANDOM = LazyValue.create(SecureRandom::new);

    private Set<Consumer<Tls>> consumers = new LinkedHashSet<>();

    // TODO: ideally we would overload to also take a Tls instance
    public ConfiguredTlsManager(TlsConfig.BuilderBase<?, ?> target) {
        sslContext(target);
        sslParameters(target);
    }

    @Override // TlsManager
    public String name() {
        return "@default";
    }

    @Override // TlsManager
    public String type() {
        return "tls-manager";
    }

    @Override // TlsManager
    public boolean reload() {
        // TODO: should this loop over reloadable consumers?
        return false;
    }

    @Override // TlsManager
    public void register(Consumer<Tls> tlsConsumer) {
        Objects.requireNonNull(tlsConsumer);
        consumers.add(tlsConsumer);
    }

    @Override // TlsManager
    public Tls tls() {
        // TODO:
        return null;
    }

    private void sslContext(TlsConfig.BuilderBase<?, ?> target) {
        if (target.sslContext().isPresent()) {
            target.tlsInfo(new TlsInternalInfo(true, List.of(), null, null));
            return;
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

            if (kmf != null) {
                var kmData = wrapX509KeyManagers(reloadable, kmf.getKeyManagers());
                km = kmData.result();
                kmOriginal = kmData.original();
            }
            if (tmf != null) {
                var tmData = wrapX509TrustManagers(reloadable,
                                                   tmf.getTrustManagers());
                tm = tmData.result();
                tmOriginal = tmData.original();
            }
            sslContext.init(km, tm, secureRandom);
            target.tlsInfo(new TlsInternalInfo(false, reloadable, tmOriginal, kmOriginal));

            SSLSessionContext serverSessionContext = sslContext.getServerSessionContext();
            if (serverSessionContext != null) {
                serverSessionContext.setSessionCacheSize(target.sessionCacheSize());
                // seconds
                serverSessionContext.setSessionTimeout((int) target.sessionTimeout().toSeconds());
            }
            target.sslContext(sslContext);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Failed to create SSLContext", e);
        }
    }

    private TrustManagerFactory buildTrustAllTmf() {
        return new TrustAllManagerFactory();
    }

    private TrustManagerFactory buildTmf(TlsConfig.BuilderBase<?, ?> target) throws KeyStoreException {
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

    private SecureRandom secureRandom(TlsConfig.BuilderBase<?, ?> target)
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

        return RANDOM.get();
    }

    private void sslParameters(TlsConfig.BuilderBase<?, ?> target) {
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

    private KeyManagerFactory buildKmf(TlsConfig.BuilderBase<?, ?> target, SecureRandom secureRandom, PrivateKey privateKey) {
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

    private KeyManagerFactory kmf(TlsConfig.BuilderBase<?, ?> target) {
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

    private KeyStore keystore(TlsConfig.BuilderBase<?, ?> target) {
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

    private TrustManagerFactory tmf(TlsConfig.BuilderBase<?, ?> target) {
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

    private ReloadableData<KeyManager, X509KeyManager> wrapX509KeyManagers(List<TlsReloadableComponent> reloadable,
                                                                           KeyManager[] keyManagers) {
        KeyManager[] toReturn = new KeyManager[keyManagers.length];
        System.arraycopy(keyManagers, 0, toReturn, 0, toReturn.length);
        for (int i = 0; i < keyManagers.length; i++) {
            KeyManager keyManager = keyManagers[i];
            if (keyManager instanceof X509KeyManager x509KeyManager) {
                ReloadableX509KeyManager wrappedKeyManager = new ReloadableX509KeyManager(x509KeyManager);
                reloadable.add(wrappedKeyManager);
                toReturn[i] = wrappedKeyManager;
                return new ReloadableData<>(toReturn, x509KeyManager);
            }
        }
        reloadable.add(new ReloadableX509KeyManager.NotReloadableKeyManager());
        return new ReloadableData<>(toReturn, null);
    }

    private ReloadableData<TrustManager, X509TrustManager> wrapX509TrustManagers(List<TlsReloadableComponent> reloadable,
                                                                                 TrustManager[] trustManagers) {
        TrustManager[] toReturn = new TrustManager[trustManagers.length];
        System.arraycopy(trustManagers, 0, toReturn, 0, toReturn.length);
        for (int i = 0; i < trustManagers.length; i++) {
            TrustManager trustManager = trustManagers[i];
            if (trustManager instanceof X509TrustManager x509TrustManager) {
                var wrappedTrustManager = new ReloadableX509TrustManager(x509TrustManager);
                reloadable.add(wrappedTrustManager);
                toReturn[i] = wrappedTrustManager;
                return new ReloadableData<>(toReturn, x509TrustManager);
            }
        }
        reloadable.add(new ReloadableX509TrustManager.NotReloadableTrustManager());
        return new ReloadableData<>(toReturn, null);
    }

    record ReloadableData<T, U>(T[] result, U original) {
    }

}
