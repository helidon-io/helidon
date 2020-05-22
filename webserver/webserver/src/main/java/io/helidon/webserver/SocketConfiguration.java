/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import io.helidon.config.ConfigException;

/**
 * The SocketConfiguration configures a port to listen on and its associated server socket parameters.
 */
public interface SocketConfiguration {

    /** The default socket configuration. */
    SocketConfiguration DEFAULT = builder().build();

    /**
     * The default backlog size to configure the server sockets with if no other value
     * is provided.
     */
    int DEFAULT_BACKLOG_SIZE = 1024;

    /**
     * Returns a server port to listen on with the server socket. If port is
     * {@code 0} then any available ephemeral port will be used.
     *
     * @return the server port of the server socket
     */
    int port();

    /**
     * Returns local address where the server listens on with the server socket.
     * If {@code null} then listens an all local addresses.
     *
     * @return an address to bind with the server socket; {@code null} for all local addresses
     */
    InetAddress bindAddress();

    /**
     * Returns a maximum length of the queue of incoming connections on the server
     * socket.
     * <p>
     * Default value is {@link #DEFAULT_BACKLOG_SIZE}.
     *
     * @return a maximum length of the queue of incoming connections
     */
    int backlog();

    /**
     * Returns a server socket timeout in milliseconds or {@code 0} for an infinite timeout.
     *
     * @return a server socket timeout in milliseconds or {@code 0}
     */
    int timeoutMillis();

    /**
     * Returns proposed value of the TCP receive window that is advertised to the remote peer on the
     * server socket.
     * <p>
     * If {@code 0} then use implementation default.
     *
     * @return a buffer size in bytes of the server socket or {@code 0}
     */
    int receiveBufferSize();

    /**
     * Returns a {@link SSLContext} to use with the server socket. If not {@code null} then
     * the server enforces an SSL communication.
     *
     * @return a SSL context to use
     */
    SSLContext ssl();

    /**
     * Returns the SSL protocols to enable, or {@code null} to enable the default
     * protocols.
     * @return the SSL protocols to enable
     */
    Set<String> enabledSslProtocols();

    /**
     * Creates a builder of {@link SocketConfiguration} class.
     *
     * @return a builder
     */
    static Builder builder() {
        return new Builder();
    }

    interface SocketConfigurationBuilder<B extends SocketConfigurationBuilder<B>> {
        /**
         * Configures a server port to listen on with the server socket. If port is
         * {@code 0} then any available ephemeral port will be used.
         *
         * @param port the server port of the server socket
         * @return this builder
         */
        B port(int port);

        /**
         * Configures local address where the server listens on with the server socket.
         * If not configured, then listens an all local addresses.
         *
         * @param address an address to bind with the server socket
         * @return this builder
         * @throws java.lang.NullPointerException in case the bind address is null
         * @throws io.helidon.config.ConfigException in case the address provided is not a valid host address
         */
        default B bindAddress(String address) {
            try {
                return bindAddress(InetAddress.getByName(address));
            } catch (UnknownHostException e) {
                throw new ConfigException("Illegal value of 'bind-address' configuration key. Expecting host or ip address!", e);
            }
        }

        /**
         * A helper method that just calls {@link #bindAddress(String)}.
         *
         * @param address host to listen on
         * @return this builder
         */
        default B host(String address) {
            return bindAddress(address);
        }

        /**
         * Configures local address where the server listens on with the server socket.
         * If not configured, then listens an all local addresses.
         *
         * @param bindAddress an address to bind with the server socket
         * @return this builder
         * @throws java.lang.NullPointerException in case the bind address is null
         */
        B bindAddress(InetAddress bindAddress);
        /**
         * Configures a maximum length of the queue of incoming connections on the server
         * socket.
         * <p>
         * Default value is {@link #DEFAULT_BACKLOG_SIZE}.
         *
         * @param backlog a maximum length of the queue of incoming connections
         * @return this builder
         */
        B backlog(int backlog);

        /**
         * Configures a server socket timeout.
         *
         * @param amount an amount of time to configure the timeout, use {@code 0} for infinite timeout
         * @param unit time unit to use with the configured amount
         * @return this builder
         */
        B timeout(long amount, TimeUnit unit);

        /**
         * Configures proposed value of the TCP receive window that is advertised to the remote peer on the
         * server socket.
         * <p>
         * If {@code 0} then use implementation default.
         *
         * @param receiveBufferSize a buffer size in bytes of the server socket or {@code 0}
         * @return this builder
         */
        B receiveBufferSize(int receiveBufferSize);

        /**
         * Configures SSL for this socket. When configured, the server enforces SSL
         * configuration.
         * If this method is called, any other method except for {@link #tls(java.util.function.Supplier)}¨
         * and repeated invocation of this method would be ignored.
         * <p>
         * If this method is called again, the previous configuration would be ignored.
         *
         * @param tlsConfig ssl configuration to use with this socket
         * @return this builder
         */
        B tls(TlsConfig tlsConfig);

        /**
         * Configures SSL for this socket. When configured, the server enforces SSL
         * configuration.
         *
         * @param tlsConfig supplier ssl configuration to use with this socket
         * @return this builder
         */
        default B tls(Supplier<TlsConfig> tlsConfig) {
            return tls(tlsConfig.get());
        }
    }

    /** The {@link io.helidon.webserver.SocketConfiguration} builder class. */
    final class Builder implements SocketConfigurationBuilder<Builder>, io.helidon.common.Builder<SocketConfiguration> {

        private final TlsConfig.Builder tlsConfigBuilder = TlsConfig.builder();

        private int port = 0;
        private InetAddress bindAddress = null;
        private int backlog = DEFAULT_BACKLOG_SIZE;
        private int timeoutMillis = 0;
        private int receiveBufferSize = 0;
        private TlsConfig tlsConfig;

        private Builder() {
        }

        @Override
        public SocketConfiguration build() {
            if (null == tlsConfig) {
                tlsConfig = tlsConfigBuilder.build();
            }

            return new ServerBasicConfig.SocketConfig(this);
        }

        @Override
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        @Override
        public Builder bindAddress(InetAddress bindAddress) {
            this.bindAddress = bindAddress;
            return this;
        }

        /**
         * Configures a maximum length of the queue of incoming connections on the server
         * socket.
         * <p>
         * Default value is {@link #DEFAULT_BACKLOG_SIZE}.
         *
         * @param backlog a maximum length of the queue of incoming connections
         * @return this builder
         */
        public Builder backlog(int backlog) {
            this.backlog = backlog;
            return this;
        }

        /**
         * Configures a server socket timeout in milliseconds or {@code 0} for an infinite timeout.
         *
         * @param timeoutMillis a server socket timeout in milliseconds or {@code 0}
         * @return this builder
         *
         * @deprecated since 2.0.0 please use {@link #timeout(long, java.util.concurrent.TimeUnit)} instead
         */
        @Deprecated
        public Builder timeoutMillis(int timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        /**
         * Configures proposed value of the TCP receive window that is advertised to the remote peer on the
         * server socket.
         * <p>
         * If {@code 0} then use implementation default.
         *
         * @param receiveBufferSize a buffer size in bytes of the server socket or {@code 0}
         * @return this builder
         */
        @Override
        public Builder receiveBufferSize(int receiveBufferSize) {
            this.receiveBufferSize = receiveBufferSize;
            return this;
        }

        /**
         * Configures a {@link SSLContext} to use with the server socket. If not {@code null} then
         * the server enforces an SSL communication.
         *
         * @param sslContext a SSL context to use
         * @return this builder
         *
         * @deprecated since 2.0.0, please use {@link #tls(TlsConfig)} instead
         */
        @Deprecated
        public Builder ssl(SSLContext sslContext) {
            if (null != sslContext) {
                this.tlsConfigBuilder.sslContext(sslContext);
            }
            return this;
        }

        /**
         * Configures a {@link SSLContext} to use with the server socket. If not {@code null} then
         * the server enforces an SSL communication.
         *
         * @param sslContextBuilder a SSL context builder to use; will be built as a first step of this
         *                          method execution
         * @return this builder
         * @deprecated since 2.0.0, please use {@link #tls(Supplier)} instead
         */
        @Deprecated
        public Builder ssl(Supplier<? extends SSLContext> sslContextBuilder) {
            return ssl(sslContextBuilder != null ? sslContextBuilder.get() : null);
        }

        /**
         * Configures the SSL protocols to enable with the server socket.
         * @param protocols protocols to enable, if {@code null} enables the
         * default protocols
         * @return this builder
         *
         * @deprecated since 2.0.0, please use {@link io.helidon.webserver.TlsConfig.Builder#enabledProtocols(String...)}
         *              instead
         */
        @Deprecated
        public Builder enabledSSlProtocols(String... protocols) {
            if (null == protocols) {
                enabledSSlProtocols(List.of());
            } else {
                enabledSSlProtocols(Arrays.asList(protocols));
            }
            return this;
        }

        /**
         * Configures the SSL protocols to enable with the server socket.
         * @param protocols protocols to enable, if {@code null} or empty enables
         *  the default protocols
         * @return this builder
         */
        @Deprecated
        public Builder enabledSSlProtocols(List<String> protocols) {
            if (null == protocols) {
                this.tlsConfigBuilder.enabledProtocols(List.of());
            } else {
                this.tlsConfigBuilder.enabledProtocols(protocols);
            }
            return this;
        }

        @Override
        public Builder timeout(long amount, TimeUnit unit) {
            long timeout = unit.toMillis(amount);
            if (timeout > Integer.MAX_VALUE) {
                this.timeoutMillis = 0;
            } else {
                this.timeoutMillis = (int) timeout;
            }
            return this;
        }

        @Override
        public Builder tls(TlsConfig tlsConfig) {
            this.tlsConfig = tlsConfig;
            return this;
        }

        public int port() {
            return port;
        }

        Optional<InetAddress> bindAddress() {
            return Optional.ofNullable(bindAddress);
        }

        int backlog() {
            return backlog;
        }

        int timeoutMillis() {
            return timeoutMillis;
        }

        int receiveBufferSize() {
            return receiveBufferSize;
        }

        TlsConfig tlsConfig() {
            return tlsConfig;
        }
    }
}
