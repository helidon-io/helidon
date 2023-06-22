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
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;

/**
 * TLS configuration - common for server and client.
 */
@RuntimeType.PrototypedBy(TlsConfig.class)
public abstract sealed class Tls implements RuntimeType.Api<TlsConfig> permits Tls.ExplicitContextTlsConfig, Tls.TlsConfigImpl {

    /**
     * HTTPS endpoint identification algorithm, verifies certificate cn against host name.
     *
     * @see TlsConfig#endpointIdentificationAlgorithm()
     */
    public static final String ENDPOINT_IDENTIFICATION_HTTPS = "HTTPS";
    /**
     * Disable host name verification.
     *
     * @see TlsConfig#endpointIdentificationAlgorithm()
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
    private final TlsConfig tlsConfig;

    private Tls(TlsConfig config) {
        // at this time, the TlsConfigInterceptor should have created SSL parameters, and an SSL context
        this.sslContext = config.sslContext().get();
        this.sslParameters = config.sslParameters().get();
        this.sslSocketFactory = sslContext.getSocketFactory();
        this.sslServerSocketFactory = sslContext.getServerSocketFactory();
        this.enabled = config.enabled();
        TlsInternalInfo internalInfo = config.internalInfo();
        this.reloadableComponents = List.copyOf(internalInfo.reloadableComponents());
        this.originalTrustManager = internalInfo.originalTrustManager();
        this.originalKeyManager = internalInfo.originalKeyManager();
        this.tlsConfig = config;
    }

    /**
     * A new fluent API builder.
     *
     * @return builder
     */
    public static TlsConfig.Builder builder() {
        return TlsConfig.builder();
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
     * Create TLS with custom configuration.
     *
     * @param tlsConfig TLS configuration
     * @return a new TLS instance
     */
    public static Tls create(TlsConfig tlsConfig) {
        if (tlsConfig.internalInfo().explicitContext()) {
            return new ExplicitContextTlsConfig(tlsConfig);
        }
        return new TlsConfigImpl(tlsConfig);
    }

    /**
     * Create TLS customizing its configuration.
     *
     * @param consumer configuration builder consumer
     * @return a new TLS instance
     */
    public static Tls create(Consumer<TlsConfig.Builder> consumer) {
        TlsConfig.Builder builder = TlsConfig.builder();
        consumer.accept(builder);
        return create(builder.buildPrototype());
    }

    @Override
    public TlsConfig prototype() {
        return tlsConfig;
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

        TlsConfigImpl(TlsConfig config) {
            super(config);

            this.protocol = config.protocol();
            this.provider = config.provider().orElse(null);
            this.sessionTimeout = config.sessionTimeout();
            this.sessionCacheSize = config.sessionCacheSize();
            this.secureRandomAlgorithm = config.secureRandomAlgorithm().orElse(null);
            this.secureRandomProvider = config.secureRandomProvider().orElse(null);
            this.internalKeystoreType = config.internalKeystoreType().orElse(null);
            this.internalKeystoreProvider = config.internalKeystoreProvider().orElse(null);
            this.privateKey = config.privateKey().orElse(null);
            this.privateKeyCertChain = config.privateKeyCertChain();
            this.kmfAlgorithm = config.keyManagerFactoryAlgorithm().orElse(null);
            this.kmfProvider = config.keyManagerFactoryProvider().orElse(null);
            this.trustCertificates = config.trust();
            this.tmfAlgorithm = config.trustManagerFactoryAlgorithm().orElse(null);
            this.tmfProvider = config.trustManagerFactoryProvider().orElse(null);
            this.trustAll = config.trustAll();
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
        private ExplicitContextTlsConfig(TlsConfig config) {
            super(config);
        }
    }
}
