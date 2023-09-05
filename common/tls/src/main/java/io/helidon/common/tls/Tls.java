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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
public class Tls implements RuntimeType.Api<TlsConfig> {

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
    private final boolean enabled;
    private final TlsConfig tlsConfig;
    private final TlsManager tlsManager;

    private Tls(TlsConfig config) {
        // at this time, the TlsConfigDecorator should have created SSL parameters; the SSL context is the responsibility
        // of the manager to provide
        this.tlsConfig = Objects.requireNonNull(config);
        this.sslParameters = config.sslParameters().orElseThrow();
        this.enabled = config.enabled();

        if (config.enabled()) {
            this.tlsManager = config.manager();
            this.tlsManager.init(config);
            this.sslContext = tlsManager.sslContext();
            this.sslSocketFactory = sslContext.getSocketFactory();
            this.sslServerSocketFactory = sslContext.getServerSocketFactory();
        } else {
            this.sslContext = null;
            this.sslSocketFactory = null;
            this.sslServerSocketFactory = null;
            this.tlsManager = null;
        }
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
        return new Tls(tlsConfig);
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
        checkEnabled();
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setSSLParameters(sslParameters);
        return sslEngine;
    }

    @Override
    public int hashCode() {
        if (enabled) {
            return 31 * Objects.hash(sslContext()) + hashCode(sslParameters());
        }
        return Objects.hash(Tls.class);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Tls other)) {
            return false;
        }
        if (!enabled() && !other.enabled()) {
            return true;
        }

        return sslContext().equals(other.sslContext()) && equals(sslParameters(), other.sslParameters());
    }

    /**
     * Create a TLS socket for a server.
     *
     * @return a new server socket ready for TLS communication
     */
    public SSLServerSocket createServerSocket() {
        checkEnabled();
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
        checkEnabled();
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
        checkEnabled();
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
     * Provides the SSL context.
     *
     * @return SSL context
     */
    public SSLContext sslContext() {
        checkEnabled();
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
     * Reload reloadable {@link TlsReloadableComponent}s with the new configuration.
     *
     * @param tls new TLS configuration
     */
    public void reload(Tls tls) {
        if (enabled) {
            tlsManager.reload(tls);
        }
    }

    /**
     * Whether this TLS configuration is enabled or not.
     *
     * @return whether TLS is enabled
     */
    public boolean enabled() {
        return enabled;
    }

    Optional<X509KeyManager> keyManager() {
        return tlsManager.keyManager();
    }

    Optional<X509TrustManager> trustManager() {
        return tlsManager.trustManager();
    }

    private void checkEnabled() {
        if (sslContext == null) {
            throw new IllegalStateException("TLS config is disabled, SSL related methods cannot be called.");
        }
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
}
