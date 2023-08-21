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

package io.helidon.common.tls;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.PrivateKey;
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
import io.helidon.common.config.Config;

/**
 * TLS configuration - common for server and client.
 */
@RuntimeType.PrototypedBy(TlsConfig.class)
public abstract sealed class Tls implements RuntimeType.Api<TlsConfig>, TlsInfo
        permits Tls.ExplicitContextTlsConfig, Tls.TlsConfigImpl {

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

    private final SSLContext sslContext;
    private final SSLParameters sslParameters;
    private final SSLSocketFactory sslSocketFactory;
    private final SSLServerSocketFactory sslServerSocketFactory;
    private final List<TlsReloadableComponent> reloadableComponents;
    private final X509TrustManager originalTrustManager;
    private final X509KeyManager originalKeyManager;
    private final TlsConfig tlsConfig;

    private Tls(TlsConfig config) {
        // at this time, the TlsConfigInterceptor should have created SSL parameters, and an SSL context
        this(config, config.sslContext().orElseThrow(), config.sslParameters().orElseThrow(), config.tlsInfo());
    }

    private Tls(TlsConfig config,
                SSLContext sslContext,
                SSLParameters sslParameters,
                TlsInfo tlsInfo) {
        this.tlsConfig = Objects.requireNonNull(config);
        this.sslContext = sslContext;
        this.sslParameters = sslParameters;
        this.sslSocketFactory = sslContext.getSocketFactory();
        this.sslServerSocketFactory = sslContext.getServerSocketFactory();
        this.reloadableComponents = List.copyOf(tlsInfo.reloadableComponents());
        this.originalTrustManager = tlsInfo.originalTrustManager();
        this.originalKeyManager = tlsInfo.originalKeyManager();

        // have the manager know about this instance as the last step
        config.manager().tls(this);
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
        if (tlsConfig.tlsInfo().explicitContext()) {
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
     * Create a SSLSocket for the chosen protocol and the given socket.
     *
     * @param alpnProtocols protocol(s) to use (order is significant)
     * @param socket existing socket
     * @param address where SSL socket will connect
     * @return a new socket ready for TLS communication
     */
    public SSLSocket createSocket(List<String> alpnProtocols, Socket socket, InetSocketAddress address) {
        try {
            SSLSocket sslSocket = (SSLSocket) sslSocketFactory
                    .createSocket(socket, address.getHostName(), address.getPort(), true);
            sslParameters.setApplicationProtocols(alpnProtocols.toArray(new String[0]));
            sslSocket.setSSLParameters(sslParameters);
            return sslSocket;
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
     * The manager for this Tls instance.
     *
     * @return the tls manager for this instance
     */
    public TlsManager manager() {
        return tlsConfig.manager();
    }

//    @Override // TlsReloadableComponent iface multiplexer
    public void reload(Tls tls) {
        for (TlsReloadableComponent reloadableComponent : reloadableComponents) {
            reloadableComponent.reload(tls);
        }
    }

    @Override
    public List<TlsReloadableComponent> reloadableComponents() {
        return List.copyOf(reloadableComponents);
    }

    @Override
    public X509TrustManager originalTrustManager() {
        return originalTrustManager;
    }

    @Override
    public X509KeyManager originalKeyManager() {
        return originalKeyManager;
    }

    /**
     * Whether this TLS configuration is enabled or not.
     *
     * @return whether TLS is enabled
     */
    public boolean enabled() {
        return tlsConfig.enabled();
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
