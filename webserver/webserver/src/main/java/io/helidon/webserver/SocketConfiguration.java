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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

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
     * Whether to require client authentication or not.
     *
     * @return client authentication
     */
    ClientAuthentication clientAuth();

    /**
     * Creates a builder of {@link SocketConfiguration} class.
     *
     * @return a builder
     */
    static Builder builder() {
        return new Builder();
    }

    /** The {@link io.helidon.webserver.SocketConfiguration} builder class. */
    final class Builder implements io.helidon.common.Builder<SocketConfiguration> {

        private int port = 0;
        private InetAddress bindAddress = null;
        private SSLContext sslContext = null;
        private final Set<String> enabledSslProtocols = new HashSet<>();
        private int backlog = 0;
        private int timeoutMillis = 0;
        private int receiveBufferSize = 0;
        private ClientAuthentication clientAuth = ClientAuthentication.NONE;

        private Builder() {
        }

        /**
         * Configures a server port to listen on with the server socket. If port is
         * {@code 0} then any available ephemeral port will be used.
         *
         * @param port the server port of the server socket
         * @return this builder
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Configures local address where the server listens on with the server socket.
         * If {@code null} then listens an all local addresses.
         *
         * @param bindAddress an address to bind with the server socket; {@code null} for all local addresses
         * @return this builder
         */
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
         */
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
         */
        public Builder ssl(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /**
         * Configures a {@link SSLContext} to use with the server socket. If not {@code null} then
         * the server enforces an SSL communication.
         *
         * @param sslContextBuilder a SSL context builder to use; will be built as a first step of this
         *                          method execution
         * @return this builder
         */
        public Builder ssl(Supplier<? extends SSLContext> sslContextBuilder) {
            return ssl(sslContextBuilder != null ? sslContextBuilder.get() : null);
        }

        /**
         * Configures whether client authentication will be required or not.
         *
         * @param clientAuth client authentication
         * @return this builder
         */
        public Builder clientAuth(ClientAuthentication clientAuth) {
            this.clientAuth = Objects.requireNonNull(clientAuth);
            return this;
        }

        void clientAuth(String it) {
            clientAuth(ClientAuthentication.valueOf(it.toUpperCase()));
        }

        /**
         * Configures the SSL protocols to enable with the server socket.
         * @param protocols protocols to enable, if {@code null} enables the
         * default protocols
         * @return this builder
         */
        public Builder enabledSSlProtocols(String... protocols){
            this.enabledSslProtocols.addAll(Arrays.asList(protocols));
            return this;
        }

        /**
         * Configures the SSL protocols to enable with the server socket.
         * @param protocols protocols to enable, if {@code null} or empty enables
         *  the default protocols
         * @return this builder
         */
        public Builder enabledSSlProtocols(List<String> protocols){
            if (protocols != null) {
                this.enabledSslProtocols.addAll(protocols);
            }
            return this;
        }

        @Override
        public SocketConfiguration build() {
            return new ServerBasicConfig.SocketConfig(port, bindAddress,
                    sslContext, enabledSslProtocols, backlog, timeoutMillis,
                    receiveBufferSize, clientAuth);
        }
    }
}
