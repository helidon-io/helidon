/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.common.tls;

import java.io.IOException;
import java.io.UncheckedIOException;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.pki.KeyConfig;

/**
 * TLS configuration - common for server and client.
 */
public abstract sealed class Tls permits Tls.ExplicitContextTlsConfig, Tls.TlsConfigImpl {
    /**
     * HTTPS endpoint identification algorithm, verifies certificate cn against host name.
     *
     * @see io.helidon.nima.common.tls.Tls.Builder#endpointIdentificationAlgorithm(String)
     */
    public static final String ENDPOINT_IDENTIFICATION_HTTPS = "HTTPS";
    /**
     * Disable host name verification.
     *
     * @see io.helidon.nima.common.tls.Tls.Builder#endpointIdentificationAlgorithm(String)
     */
    public static final String ENDPOINT_IDENTIFICATION_NONE = "NONE";
    // secure random cannot be stored in native image, it must
    // be initialized at runtime
    private static final LazyValue<SecureRandom> RANDOM = LazyValue.create(SecureRandom::new);

    private final SSLContext sslContext;
    private final SSLParameters sslParameters;
    private final SSLSocketFactory sslSocketFactory;
    private final SSLServerSocketFactory sslServerSocketFactory;
    private final List<TlsReloadableComponent> reloadableComponents;
    private final X509TrustManager originalTrustManager;
    private final X509KeyManager originalKeyManager;
    private final boolean enabled;

    private Tls(Builder builder) {
        this.sslContext = builder.sslContext;
        this.sslParameters = builder.sslParameters;
        this.sslSocketFactory = sslContext.getSocketFactory();
        this.sslServerSocketFactory = sslContext.getServerSocketFactory();
        this.enabled = builder.enabled;
        this.reloadableComponents = List.copyOf(builder.reloadableComponents);
        this.originalTrustManager = builder.originalTrustManager;
        this.originalKeyManager = builder.originalKeyManager;
    }

    /**
     * A new fluent API builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create TLS configuration from config.
     *
     * @param config located on the node of the tls configuration (usually this is {@code ssl})
     * @return a new TLS configuration
     */
    public static Tls create(Config config) {
        return builder().config(config).build();
    }

    /**
     * SSL engine from this configuration.
     *
     * @return SSL Engine
     */
    public final SSLEngine newEngine() {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setSSLParameters(sslParameters);
        return sslEngine;
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(sslContext) + hashCode(sslParameters);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Tls tlsConfig)) {
            return false;
        }

        return sslContext.equals(tlsConfig.sslContext) && equals(sslParameters, tlsConfig.sslParameters);
    }

    /**
     * Create a TLS socket for a server.
     *
     * @return a new server socket ready for TLS communication
     */
    public SSLServerSocket createServerSocket() {
        try {
            SSLServerSocket socket = (SSLServerSocket) sslServerSocketFactory.createServerSocket();
            socket.setSSLParameters(sslParameters);
            return socket;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Create a socket for the chosen protocol.
     *
     * @param alpnProtocol protocol to use
     * @return a new socket ready for TLS communication
     */
    public SSLSocket createSocket(String alpnProtocol) {
        try {
            SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket();
            sslParameters.setApplicationProtocols(new String[] {alpnProtocol});
            socket.setSSLParameters(sslParameters);
            return socket;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * SSL context based on the configured values.
     *
     * @return SSL context
     */
    public SSLContext sslContext() {
        return sslContext;
    }

    /**
     * SSL parameters.
     *
     * @return SSL parameters
     */
    public SSLParameters sslParameters() {
        return sslParameters;
    }

    /**
     * Reload reloadable TLS components with the new configuration.
     *
     * @param tls new TLS configuration
     */
    public void reload(Tls tls) {
        for (TlsReloadableComponent reloadableComponent : reloadableComponents) {
            reloadableComponent.reload(tls);
        }
    }

    X509TrustManager originalTrustManager() {
        return originalTrustManager;
    }

    X509KeyManager originalKeyManager() {
        return originalKeyManager;
    }

    /**
     * Whether this TLS configuration is enabled or not.
     *
     * @return whether TLS is enabled
     */
    public boolean enabled() {
        return enabled;
    }

    private static int hashCode(SSLParameters first) {
        int result = Objects.hash(first.getAlgorithmConstraints(),
                                  first.getEnableRetransmissions(),
                                  first.getEndpointIdentificationAlgorithm(),
                                  first.getMaximumPacketSize(),
                                  first.getNeedClientAuth(),
                                  first.getUseCipherSuitesOrder(),
                                  first.getWantClientAuth(),
                                  first.getServerNames(),
                                  first.getSNIMatchers());
        result = 31 * result + Arrays.hashCode(first.getApplicationProtocols());
        result = 31 * result + Arrays.hashCode(first.getCipherSuites());
        result = 31 * result + Arrays.hashCode(first.getProtocols());

        return result;
    }

    private static boolean equals(SSLParameters first, SSLParameters second) {
        return first.getAlgorithmConstraints().equals(second.getAlgorithmConstraints())
                && Arrays.equals(first.getApplicationProtocols(), second.getApplicationProtocols())
                && Arrays.equals(first.getCipherSuites(), second.getCipherSuites())
                && (first.getEnableRetransmissions() == second.getEnableRetransmissions())
                && Objects.equals(first.getEndpointIdentificationAlgorithm(), second.getEndpointIdentificationAlgorithm())
                && (first.getMaximumPacketSize() == second.getMaximumPacketSize())
                && (first.getNeedClientAuth() == second.getNeedClientAuth())
                && Arrays.equals(first.getProtocols(), second.getProtocols())
                && (first.getUseCipherSuitesOrder() == second.getUseCipherSuitesOrder())
                && (first.getWantClientAuth() == second.getWantClientAuth())
                && first.getServerNames().equals(second.getServerNames())
                && first.getSNIMatchers().equals(second.getSNIMatchers());
    }

    /**
     * Fluent API builder for {@link io.helidon.nima.common.tls.Tls}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, Tls> {
        private static final String DEFAULT_PROTOCOL = "TLS";
        private static final int DEFAULT_SESSION_CACHE_SIZE = 1024;

        private String protocol = DEFAULT_PROTOCOL;
        private String provider;
        private Duration sessionTimeout = Duration.ofMinutes(30);
        private int sessionCacheSize = DEFAULT_SESSION_CACHE_SIZE;
        private List<String> enabledCipherSuites;
        private List<String> enabledProtocols;
        private List<String> applicationProtocols;
        private SSLContext sslContext;
        private SecureRandom secureRandom;
        private String secureRandomAlgorithm;
        private String secureRandomProvider;
        private TlsClientAuth tlsClientAuth = TlsClientAuth.NONE;
        private String internalKeystoreType = KeyStore.getDefaultType();
        private String internalKeystoreProvider;

        /*
         Private key related options
         */
        private PrivateKey privateKey;
        private List<X509Certificate> privateKeyCertChain;
        private String kmfAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
        private String kmfProvider;

        /*
         Trust related options
         */
        private List<X509Certificate> trustCertificates;
        private String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        private String tmfProvider;
        private boolean trustAll = false;
        private SSLParameters sslParameters;
        private String endpointIdentificationAlgorithm = ENDPOINT_IDENTIFICATION_HTTPS;
        private boolean enabled = true;

        /*
         * TLS reloading
         */
        private final List<TlsReloadableComponent> reloadableComponents = new ArrayList<>();
        private X509TrustManager originalTrustManager;
        private X509KeyManager originalKeyManager;

        private Builder() {
        }

        @Override
        public Tls build() {
            if (sslParameters == null) {
                this.sslParameters = createSslParameters();
            }

            if (sslContext == null) {
                this.sslContext = createSslContext();
                return new TlsConfigImpl(this);
            }

            return new ExplicitContextTlsConfig(this);
        }

        /**
         * Configure the protocol used to obtain an instance of {@link javax.net.ssl.SSLContext}.
         *
         * @param protocol protocol to use, defaults to {@value DEFAULT_PROTOCOL}
         * @return updated builder
         */
        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        /**
         * Use explicit provider to obtain an instance of {@link javax.net.ssl.SSLContext}.
         *
         * @param provider provider to use, defaults to none (only {@link #protocol(String)} is used by default)
         * @return updated builder
         */
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        /**
         * SSL session timeout.
         *
         * @param sessionTimeout session timeout, defaults to 30 minutes
         * @return updated builder
         */
        public Builder sessionTimeout(Duration sessionTimeout) {
            this.sessionTimeout = sessionTimeout;
            return this;
        }

        /**
         * SSL session cache size.
         *
         * @param sessionCacheSize session cache size, defaults to {@value DEFAULT_SESSION_CACHE_SIZE}
         * @return updated builder
         */
        public Builder sessionCacheSize(int sessionCacheSize) {
            this.sessionCacheSize = sessionCacheSize;
            return this;
        }

        /**
         * Enabled cipher suites for TLS communication.
         *
         * @param enabledCipherSuites cipher suits to enable, by default (or if list is empty), all available cipher suites
         *                            are enabled
         * @return updated builder
         */
        public Builder enabledCipherSuites(List<String> enabledCipherSuites) {
            this.enabledCipherSuites = enabledCipherSuites;
            return this;
        }

        /**
         * Enabled protocols for TLS communication.
         * Example of valid values for {@code TLS} protocol: {@code TLSv1.3}, {@code TLSv1.2}
         *
         * @param enabledProtocols protocols to enable, by default (or if list is empty), all available protocols are enabled
         * @return updated builder
         */
        public Builder enabledProtocols(List<String> enabledProtocols) {
            this.enabledProtocols = enabledProtocols;
            return this;
        }

        /**
         * Provide a fully configured {@link javax.net.ssl.SSLContext}. If defined, context related configuration
         * is ignored.
         *
         * @param sslContext SSL context to use
         * @return updated builder
         */
        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /**
         * Private key to use. For server side TLS, this is required.
         * For client side TLS, this is optional (used when mutual TLS is enabled).
         *
         * @param privateKey private key to use
         * @return updated builder
         */
        public Builder privateKey(PrivateKey privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        /**
         * Certificate chain of the private key.
         *
         * @param privateKeyCertChain private key certificate chain, only used when private key is configured
         * @return updated builder
         */
        public Builder privateKeyCertChain(List<X509Certificate> privateKeyCertChain) {
            this.privateKeyCertChain = privateKeyCertChain;
            return this;
        }

        /**
         * Algorithm of the key manager factory used when private key is defined.
         * Defaults to {@link javax.net.ssl.KeyManagerFactory#getDefaultAlgorithm()}.
         *
         * @param keyManagerFactoryAlgorithm algorithm to use
         * @return updated builder
         */
        public Builder keyManagerFactoryAlgorithm(String keyManagerFactoryAlgorithm) {
            this.kmfAlgorithm = keyManagerFactoryAlgorithm;
            return this;
        }

        /**
         * List of certificates that form the trust manager.
         *
         * @param trustCertificates certificates to be trusted
         * @return updated builder
         */
        public Builder trustCertificates(List<X509Certificate> trustCertificates) {
            this.trustCertificates = trustCertificates;
            return this;
        }

        /**
         * Algorithm to use when creating a new secure random.
         *
         * @param secureRandomAlgorithm algorithm to use, by default uses {@link java.security.SecureRandom} constructor
         * @return updated builder
         */
        public Builder secureRandomAlgorithm(String secureRandomAlgorithm) {
            this.secureRandomAlgorithm = secureRandomAlgorithm;
            return this;
        }

        /**
         * Provider to use when creating a new secure random.
         *
         * @param secureRandomProvider provider to use, by default no provider is specified
         * @return updated builder
         */
        public Builder secureRandomProvider(String secureRandomProvider) {
            this.secureRandomProvider = secureRandomProvider;
            return this;
        }

        /**
         * Explicit secure random to use.
         *
         * @param secureRandom secure random to use
         * @return updated builder
         */
        public Builder secureRandom(SecureRandom secureRandom) {
            this.secureRandom = secureRandom;
            return this;
        }

        /**
         * Configure requirement for mutual TLS.
         *
         * @param tlsClientAuth what type of mutual TLS to use, defaults to {@link TlsClientAuth#NONE}
         * @return updated builder
         */
        public Builder tlsClientAuth(TlsClientAuth tlsClientAuth) {
            this.tlsClientAuth = tlsClientAuth;
            return this;
        }

        /**
         * Trust any certificate provided by the other side of communication.
         * <p>
         * <b>This is a dangerous setting: </b> if set to {@code true}, any certificate will be accepted, throwing away
         * most of the security advantages of TLS. <b>NEVER</b> do this in production.
         *
         * @param trustAll whether to trust all certificates, do not use in production
         * @return updated builder
         */
        public Builder trustAll(boolean trustAll) {
            this.trustAll = trustAll;
            return this;
        }

        /**
         * Type of the key stores used internally to create a key and trust manager factories.
         *
         * @param internalKeystoreType keystore type, defaults to {@link java.security.KeyStore#getDefaultType()}
         * @return updated builder
         */
        public Builder internalKeystoreType(String internalKeystoreType) {
            this.internalKeystoreType = internalKeystoreType;
            return this;
        }

        /**
         * Provider of the key stores used internally to create a key and trust manager factories.
         *
         * @param internalKeystoreProvider keystore provider, if not defined, provider is not specified
         * @return updated builder
         */
        public Builder internalKeystoreProvider(String internalKeystoreProvider) {
            this.internalKeystoreProvider = internalKeystoreProvider;
            return this;
        }

        /**
         * Key manager factory provider.
         *
         * @param keyManagerFactoryProvider provider to use
         * @return updated builder
         */
        public Builder keyManagerFactoryProvider(String keyManagerFactoryProvider) {
            this.kmfProvider = keyManagerFactoryProvider;
            return this;
        }

        /**
         * Trust manager factory algorithm.
         *
         * @param trustManagerFactoryAlgorithm algorithm to use
         * @return updated builder
         */
        public Builder trustManagerFactoryAlgorithm(String trustManagerFactoryAlgorithm) {
            this.tmfAlgorithm = trustManagerFactoryAlgorithm;
            return this;
        }

        /**
         * Trust manager factory provider to use.
         *
         * @param trustManagerFactoryProvider provider
         * @return updated builder
         */
        public Builder trustManagerFactoryProvider(String trustManagerFactoryProvider) {
            this.tmfProvider = trustManagerFactoryProvider;
            return this;
        }

        /**
         * Configure SSL parameters.
         *
         * @param sslParameters SSL parameters to use
         * @return updated builder
         */
        public Builder sslParameters(SSLParameters sslParameters) {
            this.sslParameters = sslParameters;
            return this;
        }

        /**
         * Identification algorithm for SSL endpoints.
         *
         * @param endpointIdentificationAlgorithm configure endpoint identification algorithm, or set to {@code NONE}
         *                                        to disable endpoint identification (equivalent to hostname verification).
         *                                        Defaults to {@value ENDPOINT_IDENTIFICATION_HTTPS}
         * @return updated builder
         */
        public Builder endpointIdentificationAlgorithm(String endpointIdentificationAlgorithm) {
            this.endpointIdentificationAlgorithm = endpointIdentificationAlgorithm;
            return this;
        }

        /**
         * Configure list of supported application protocols (such as {@code h2}).
         *
         * @param applicationProtocols application protocols
         * @return updated builder
         */
        public Builder applicationProtocols(List<String> applicationProtocols) {
            this.applicationProtocols = applicationProtocols;
            return this;
        }

        /**
         * Update this builder from configuration.
         *
         * @param config config on the node of SSL configuration
         * @return this builder
         */
        public Builder config(Config config) {
            config.get("client-auth").asString().as(TlsClientAuth::valueOf).ifPresent(this::tlsClientAuth);
            config.get("private-key")
                    .map(KeyConfig::create)
                    .ifPresent(keyConfig -> {
                        privateKey(keyConfig.privateKey().get());
                        privateKeyCertChain(keyConfig.certChain());
                    });

            config.get("trust").map(KeyConfig::create)
                    .map(KeyConfig::certs)
                    .ifPresent(this::trustCertificates);

            config.get("protocols").asList(String.class).ifPresent(this::enabledProtocols);
            config.get("session-cache-size").asInt().ifPresent(this::sessionCacheSize);
            config.get("cipher-suite").asList(String.class).ifPresent(this::enabledCipherSuites);
            config.get("session-timeout-seconds").asInt().ifPresent(this::sessionTimeoutSeconds);

            config.get("enabled").asBoolean().ifPresent(this::enabled);

            return this;
        }

        /**
         * Whether the TLS config should be enabled or not.
         *
         * @param enabled configure to {@code false} to disable SSL context (and SSL support on the server)
         * @return this builder
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        private SSLParameters createSslParameters() {
            SSLParameters sslParameters = new SSLParameters();

            if (applicationProtocols != null) {
                sslParameters.setApplicationProtocols(applicationProtocols.toArray(new String[0]));
            }
            if (enabledProtocols != null) {
                sslParameters.setProtocols(enabledProtocols.toArray(new String[0]));
            }
            if (enabledCipherSuites != null) {
                sslParameters.setCipherSuites(enabledCipherSuites.toArray(new String[0]));
            }
            switch (tlsClientAuth) {
            case REQUIRED -> {
                sslParameters.setNeedClientAuth(true);
                sslParameters.setWantClientAuth(true);
            }
            case OPTIONAL -> sslParameters.setWantClientAuth(true);
            case NONE -> {
            }
            default -> {

            }
            }

            if (endpointIdentificationAlgorithm != null) {
                if (ENDPOINT_IDENTIFICATION_NONE.equals(endpointIdentificationAlgorithm)) {
                    sslParameters.setEndpointIdentificationAlgorithm("");
                } else {
                    sslParameters.setEndpointIdentificationAlgorithm(endpointIdentificationAlgorithm);
                }
            }

            return sslParameters;
        }

        private SSLContext createSslContext() {
            try {
                return sslContext();
            } catch (GeneralSecurityException | IOException e) {
                throw new IllegalArgumentException("Failed to create SSL engine", e);
            }
        }

        private SSLContext sslContext() throws GeneralSecurityException, IOException {
            SecureRandom secureRandom = secureRandom();

            KeyManagerFactory kmf;
            if (privateKey == null) {
                kmf = null;
            } else {
                kmf = buildKmf(secureRandom);
            }

            TrustManagerFactory tmf;
            if (trustAll) {
                tmf = buildTrustAllTmf();
            } else {
                if (trustCertificates == null) {
                    tmf = null;
                } else {
                    tmf = buildTmf();
                }
            }

            SSLContext sslContext;
            if (provider == null) {
                sslContext = SSLContext.getInstance(protocol);
            } else {
                sslContext = SSLContext.getInstance(protocol, provider);
            }

            sslContext.init(kmf == null ? null : wrapX509KeyManagers(kmf.getKeyManagers()),
                            tmf == null ? null : wrapX509TrustManagers(tmf.getTrustManagers()),
                            secureRandom);

            SSLSessionContext serverSessionContext = sslContext.getServerSessionContext();
            if (serverSessionContext != null) {
                serverSessionContext.setSessionCacheSize(sessionCacheSize);
                // seconds
                serverSessionContext.setSessionTimeout((int) sessionTimeout.toSeconds());
            }

            return sslContext;
        }

        private TrustManager[] wrapX509TrustManagers(TrustManager[] trustManagers) {
            TrustManager[] toReturn = new TrustManager[trustManagers.length];
            System.arraycopy(trustManagers, 0, toReturn, 0, toReturn.length);
            for (int i = 0; i < trustManagers.length; i++) {
                TrustManager trustManager = trustManagers[i];
                if (trustManager instanceof X509TrustManager x509TrustManager) {
                    originalTrustManager = x509TrustManager;
                    var wrappedTrustManager = new ReloadableX509TrustManager(x509TrustManager);
                    reloadableComponents.add(wrappedTrustManager);
                    toReturn[i] = wrappedTrustManager;
                    return toReturn;
                }
            }
            reloadableComponents.add(new ReloadableX509TrustManager.NotReloadableTrustManager());
            return toReturn;
        }

        private KeyManager[] wrapX509KeyManagers(KeyManager[] keyManagers) {
            KeyManager[] toReturn = new KeyManager[keyManagers.length];
            System.arraycopy(keyManagers, 0, toReturn, 0, toReturn.length);
            for (int i = 0; i < keyManagers.length; i++) {
                KeyManager keyManager = keyManagers[i];
                if (keyManager instanceof X509KeyManager x509KeyManager) {
                    originalKeyManager = x509KeyManager;
                    ReloadableX509KeyManager wrappedKeyManager = new ReloadableX509KeyManager(x509KeyManager);
                    reloadableComponents.add(wrappedKeyManager);
                    toReturn[i] = wrappedKeyManager;
                    return toReturn;
                }
            }
            reloadableComponents.add(new ReloadableX509KeyManager.NotReloadableKeyManager());
            return toReturn;
        }

        private void sessionTimeoutSeconds(int seconds) {
            this.sessionTimeout(Duration.ofSeconds(seconds));
        }

        private SecureRandom secureRandom() {
            if (secureRandom != null) {
                return secureRandom;
            }
            try {
                if (secureRandomAlgorithm != null) {
                    if (secureRandomProvider == null) {
                        SecureRandom.getInstance(secureRandomAlgorithm);
                    } else {
                        return SecureRandom.getInstance(secureRandomAlgorithm, secureRandomProvider);
                    }
                }
            } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
                throw new IllegalArgumentException("Invalid configuration of secure random. Provider: " + secureRandomProvider
                                                           + ", algorithm: " + secureRandomAlgorithm, e);
            }
            return RANDOM.get();
        }

        private KeyManagerFactory buildKmf(SecureRandom secureRandom)
                throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException {
            byte[] passwordBytes = new byte[64];
            secureRandom.nextBytes(passwordBytes);
            char[] password = Base64.getEncoder().encodeToString(passwordBytes).toCharArray();

            KeyStore ks = keystore();
            ks.setKeyEntry("key",
                           privateKey,
                           password,
                           privateKeyCertChain.toArray(new Certificate[0]));

            KeyManagerFactory kmf = kmf();
            kmf.init(ks, password);
            return kmf;
        }

        private TrustManagerFactory buildTrustAllTmf() {
            return new TrustAllManagerFactory();
        }

        private TrustManagerFactory buildTmf() throws KeyStoreException {
            KeyStore ks = keystore();
            int i = 1;
            for (X509Certificate cert : trustCertificates) {
                ks.setCertificateEntry(String.valueOf(i), cert);
                i++;
            }
            TrustManagerFactory tmf = tmf();
            tmf.init(ks);
            return tmf;
        }

        private KeyManagerFactory kmf() {
            try {
                return kmfProvider == null
                        ? KeyManagerFactory.getInstance(kmfAlgorithm)
                        : KeyManagerFactory.getInstance(kmfAlgorithm, kmfProvider);
            } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
                throw new IllegalArgumentException("Invalid configuration of key manager factory. Provider: "
                                                           + kmfProvider
                                                           + ", algorithm: " + kmfAlgorithm, e);
            }
        }

        private TrustManagerFactory tmf() {
            try {
                return tmfProvider == null
                        ? TrustManagerFactory.getInstance(tmfAlgorithm)
                        : TrustManagerFactory.getInstance(tmfAlgorithm, tmfProvider);
            } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
                throw new IllegalArgumentException("Invalid configuration of trust manager factory. Provider: "
                                                           + tmfProvider
                                                           + ", algorithm: " + tmfAlgorithm, e);
            }
        }

        private KeyStore keystore() {
            try {
                KeyStore ks;
                if (internalKeystoreProvider == null) {
                    ks = KeyStore.getInstance(internalKeystoreType);
                } else {
                    ks = KeyStore.getInstance(internalKeystoreType, internalKeystoreProvider);
                }

                ks.load(null, null);

                return ks;
            } catch (KeyStoreException
                     | NoSuchProviderException
                     | IOException
                     | NoSuchAlgorithmException
                     | CertificateException e) {
                throw new IllegalArgumentException("Invalid configuration of internal keystores. Provider: "
                                                           + internalKeystoreProvider
                                                           + ", type: " + internalKeystoreType, e);
            }
        }
    }

    static final class TlsConfigImpl extends Tls {
        private final String protocol;
        private final String provider;
        private final Duration sessionTimeout;
        private final int sessionCacheSize;
        private final String secureRandomAlgorithm;
        private final String secureRandomProvider;
        private final String internalKeystoreType;
        private final String internalKeystoreProvider;

        /*
         Private key related options
         */
        private final PrivateKey privateKey;
        private final List<X509Certificate> privateKeyCertChain;
        private final String kmfAlgorithm;
        private final String kmfProvider;

        /*
         Trust related options
         */
        private final List<X509Certificate> trustCertificates;
        private final String tmfAlgorithm;
        private final String tmfProvider;
        private final boolean trustAll;

        TlsConfigImpl(Builder builder) {
            super(builder);

            this.protocol = builder.protocol;
            this.provider = builder.provider;
            this.sessionTimeout = builder.sessionTimeout;
            this.sessionCacheSize = builder.sessionCacheSize;
            this.secureRandomAlgorithm = builder.secureRandomAlgorithm;
            this.secureRandomProvider = builder.secureRandomProvider;
            this.internalKeystoreType = builder.internalKeystoreType;
            this.internalKeystoreProvider = builder.internalKeystoreProvider;
            this.privateKey = builder.privateKey;
            this.privateKeyCertChain = builder.privateKeyCertChain;
            this.kmfAlgorithm = builder.kmfAlgorithm;
            this.kmfProvider = builder.kmfProvider;
            this.trustCertificates = builder.trustCertificates;
            this.tmfAlgorithm = builder.tmfAlgorithm;
            this.tmfProvider = builder.tmfProvider;
            this.trustAll = builder.trustAll;
        }

        @Override
        public int hashCode() {
            return 31 * Tls.hashCode(super.sslParameters())
                    + Objects.hash(protocol,
                                   provider,
                                   sessionTimeout,
                                   sessionCacheSize,
                                   secureRandomAlgorithm,
                                   secureRandomProvider,
                                   internalKeystoreType,
                                   internalKeystoreProvider,
                                   privateKey,
                                   privateKeyCertChain,
                                   kmfAlgorithm,
                                   kmfProvider,
                                   trustCertificates,
                                   tmfAlgorithm,
                                   tmfProvider,
                                   trustAll);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof Tls)) {
                return false;
            }

            if (super.equals(o)) {
                return true;
            }

            if (!(o instanceof TlsConfigImpl tlsConfig)) {
                return false;
            }

            return sessionCacheSize == tlsConfig.sessionCacheSize
                    && trustAll == tlsConfig.trustAll
                    && Objects.equals(protocol, tlsConfig.protocol)
                    && Objects.equals(provider, tlsConfig.provider)
                    && Objects.equals(sessionTimeout, tlsConfig.sessionTimeout)
                    && Objects.equals(secureRandomAlgorithm, tlsConfig.secureRandomAlgorithm)
                    && Objects.equals(secureRandomProvider, tlsConfig.secureRandomProvider)
                    && Objects.equals(internalKeystoreType, tlsConfig.internalKeystoreType)
                    && Objects.equals(internalKeystoreProvider, tlsConfig.internalKeystoreProvider)
                    && Objects.equals(privateKey, tlsConfig.privateKey)
                    && Objects.equals(privateKeyCertChain, tlsConfig.privateKeyCertChain)
                    && Objects.equals(kmfAlgorithm, tlsConfig.kmfAlgorithm)
                    && Objects.equals(kmfProvider, tlsConfig.kmfProvider)
                    && Objects.equals(trustCertificates, tlsConfig.trustCertificates)
                    && Objects.equals(tmfAlgorithm, tlsConfig.tmfAlgorithm)
                    && Objects.equals(tmfProvider, tlsConfig.tmfProvider);
        }
    }

    static final class ExplicitContextTlsConfig extends Tls {
        private ExplicitContextTlsConfig(Builder builder) {
            super(builder);
        }
    }
}
