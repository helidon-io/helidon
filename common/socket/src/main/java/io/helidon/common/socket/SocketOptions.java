/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.common.socket;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Socket options.
 */
public class SocketOptions {
    private final Map<SocketOption, Object> socketOptions;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    private SocketOptions(Map<SocketOption, Object> socketOptions, Duration connectTimeout, Duration readTimeout) {
        this.socketOptions = new HashMap(socketOptions);
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    /**
     * A new fluent API builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Configure socket with defined socket options.
     *
     * @param socket socket to update
     */
    public void configureSocket(Socket socket) {
        for (Map.Entry<SocketOption, Object> entry : socketOptions.entrySet()) {
            try {
                socket.setOption(entry.getKey(), entry.getValue());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Socket connect timeout.
     *
     * @return connect timeout duration
     */
    public Duration connectTimeout() {
        return connectTimeout;
    }

    /**
     * Socket read timeout.
     *
     * @return read timeout duration
     */
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public String toString() {
        return "SocketOptions{"
                + "socketOptions=" + socketOptions
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout
                + '}';
    }

    /**
     * Fluent API builder for {@link io.helidon.common.socket.SocketOptions}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, SocketOptions> {
        private static final int DEFAULT_SO_BUFFER_SIZE = 32768;

        private final Map<SocketOption, Object> socketOptions = new HashMap<>();
        private int socketReceiveBufferSize = DEFAULT_SO_BUFFER_SIZE;
        private int socketSendBufferSize = DEFAULT_SO_BUFFER_SIZE;
        private boolean socketReuseAddress = true;
        private boolean socketKeepAlive = true;
        private boolean tcpNoDelay = false;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);

        private Builder() {
        }

        @Override
        public final SocketOptions build() {
            socketOptions.put(StandardSocketOptions.SO_RCVBUF, socketReceiveBufferSize);
            socketOptions.put(StandardSocketOptions.SO_SNDBUF, socketSendBufferSize);
            socketOptions.put(StandardSocketOptions.SO_REUSEADDR, socketReuseAddress);
            socketOptions.put(StandardSocketOptions.SO_KEEPALIVE, socketKeepAlive);
            socketOptions.put(StandardSocketOptions.TCP_NODELAY, tcpNoDelay);

            return new SocketOptions(socketOptions, connectTimeout, readTimeout);
        }

        /**
         * Socket receive buffer size.
         * Default is {@value #DEFAULT_SO_BUFFER_SIZE}.
         *
         * @param socketReceiveBufferSize buffer size, in bytes
         * @return updated builder
         * @see java.net.StandardSocketOptions#SO_RCVBUF
         */
        public Builder socketReceiveBufferSize(int socketReceiveBufferSize) {
            this.socketReceiveBufferSize = socketReceiveBufferSize;
            return this;
        }

        /**
         * Socket send buffer size.
         * Default is {@value #DEFAULT_SO_BUFFER_SIZE}.
         *
         * @param socketSendBufferSize buffer size, in bytes
         * @return updated builder
         * @see java.net.StandardSocketOptions#SO_SNDBUF
         */
        public Builder socketSendBufferSize(int socketSendBufferSize) {
            this.socketSendBufferSize = socketSendBufferSize;
            return this;
        }

        /**
         * Socket reuse address.
         * Default is {@code true}.
         *
         * @param socketReuseAddress whether to reuse address
         * @return updated builder
         * @see java.net.StandardSocketOptions#SO_REUSEADDR
         */
        public Builder socketReuseAddress(boolean socketReuseAddress) {
            this.socketReuseAddress = socketReuseAddress;
            return this;
        }

        /**
         * Configure socket keep alive.
         * Default is {@code true}.
         *
         * @param socketKeepAlive keep alive
         * @return updated builder
         * @see java.net.StandardSocketOptions#SO_KEEPALIVE
         */
        public Builder socketKeepAlive(boolean socketKeepAlive) {
            this.socketKeepAlive = socketKeepAlive;
            return this;
        }

        /**
         * This option may improve performance on some systems.
         * Default is {@code false}.
         *
         * @param tcpNoDelay whether to use TCP_NODELAY, defaults to {@code false}
         * @return updated builder
         * @see java.net.StandardSocketOptions#TCP_NODELAY
         */
        public Builder tcpNoDelay(boolean tcpNoDelay) {
            this.tcpNoDelay = tcpNoDelay;
            return this;
        }

        /**
         * Set an arbitrary option.
         *
         * @param option option to set
         * @param value option value
         * @param <O> option type
         * @return updated builder
         * @see java.net.StandardSocketOptions
         */
        public <O> Builder setOption(SocketOption<O> option, O value) {
            socketOptions.put(option, value);
            return this;
        }

        /**
         * Read timeout.
         * Default is 30 seconds.
         *
         * @param readTimeout read timeout
         * @return updated builder
         */
        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        /**
         * Connect timeout.
         * Default is 10 seconds.
         *
         * @param connectTimeout connect timeout
         * @return updated builder
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }
    }
}
