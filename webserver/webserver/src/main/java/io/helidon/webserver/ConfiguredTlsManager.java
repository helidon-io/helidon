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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
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
import io.helidon.common.pki.KeyConfig;

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

    /**
     * The manager name.
     *
     * @return manager name
     */
    // @Override // NamedService
    public String name() {
        return name;
    }

    /**
     * The manager type.
     *
     * @return manager type
     */
    // @Override // NamedService
    public String type() {
        return type;
    }

    @Override // TlsManager
    public SSLContext sslContext() {
        return sslContext;
    }

    @Override // TlsManager
    public void subscribe(Consumer<SSLContext> sslContextConsumer) {
        sslContextConsumers.add(Objects.requireNonNull(sslContextConsumer));
    }

    @Override // TlsManager
    public Optional<X509KeyManager> keyManager() {
        return Optional.empty();
    }

    @Override // TlsManager
    public Optional<X509TrustManager> trustManager() {
        return Optional.empty();
    }

    @Override // TlsManager
    public void init(WebServerTls tlsConfig) {
        SSLContext explicitSslContext = tlsConfig.explicitSslContext().orElse(null);
        if (explicitSslContext != null) {
            this.sslContext = explicitSslContext;
            return;
        }

        if (null == tlsConfig.privateKeyConfig()) {
            throw new IllegalStateException("Private key must be configured when SSL is enabled.");
        }

        try {
            SecureRandom secureRandom = secureRandom(tlsConfig);
            KeyManagerFactory kmf = buildKmf(tlsConfig.privateKeyConfig());
            TrustManagerFactory tmf = buildTmf(tlsConfig);

            initSslContext(tlsConfig,
                           secureRandom,
                           kmf.getKeyManagers(),
                           tmf.getTrustManagers());
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to build server SSL Context!", e);
        }
    }

    /**
     * Initialize and set the SSL context given the provided configuration.
     *
     * @param tlsConfig     the tls config
     * @param secureRandom  the secure random instance
     * @param keyManagers   the key managers
     * @param trustManagers the trust managers
     */
    protected void initSslContext(WebServerTls tlsConfig,
                                  SecureRandom secureRandom,
                                  KeyManager[] keyManagers,
                                  TrustManager[] trustManagers) {
        try {
            // Initialize the SSLContext to work with our key managers.
            SSLContext sslContext = SSLContext.getInstance(tlsConfig.protocol());
            sslContext.init(keyManagers, trustManagers, secureRandom);

            SSLSessionContext serverSessionContext = sslContext.getServerSessionContext();
            if (serverSessionContext != null) {
                int sessionCacheSize = tlsConfig.sessionCacheSize();
                if (sessionCacheSize > 0) {
                    serverSessionContext.setSessionCacheSize(sessionCacheSize);
                }
                int sessionTimeoutSecs = tlsConfig.sessionTimeoutSeconds();
                if (sessionTimeoutSecs > 0) {
                    serverSessionContext.setSessionTimeout(sessionTimeoutSecs);
                }
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
        return RANDOM.get();
    }

    /**
     * Creates a trust all trust manager factory.
     *
     * @return a new trust manager factory trusting all
     */
    protected TrustManagerFactory trustAllTmf() {
        return new TrustAllManagerFactory();
    }

    private KeyManagerFactory buildKmf(KeyConfig privateKeyConfig) throws IOException, GeneralSecurityException {
        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null) {
            algorithm = "SunX509";
        }

        byte[] passwordBytes = new byte[64];
        RANDOM.get().nextBytes(passwordBytes);
        char[] password = Base64.getEncoder().encodeToString(passwordBytes).toCharArray();

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setKeyEntry("key",
                       privateKeyConfig.privateKey().orElseThrow(() -> new RuntimeException("Private key not available")),
                       password,
                       privateKeyConfig.certChain().toArray(new Certificate[0]));

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
        kmf.init(ks, password);

        return kmf;
    }

    private TrustManagerFactory buildTmf(WebServerTls tlsConfig)
            throws IOException, GeneralSecurityException {
        if (tlsConfig.trustAll()) {
            return trustAllTmf();
        }

        KeyConfig trustConfig = tlsConfig.trustConfig();
        List<X509Certificate> certs;

        if (trustConfig == null) {
            certs = List.of();
        } else {
            certs = trustConfig.certs();
        }

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);

        int i = 1;
        for (X509Certificate cert : certs) {
            ks.setCertificateEntry(String.valueOf(i), cert);
            i++;
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        return tmf;
    }

}
