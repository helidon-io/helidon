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

package io.helidon.nima.webserver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.helidon.common.socket.SocketOptions;
import io.helidon.nima.common.tls.Tls;

/**
 * Configuration of a server listener (server socket).
 */
public final class ListenerConfiguration {
    private final Map<SocketOption, Object> socketOptions;
    private final int port;
    private final InetAddress address;
    private final int backlog;
    private final Tls tls;
    private final SocketOptions connectionOptions;
    private final int writeQueueLength;
    private final long maxPayloadSize;

    private ListenerConfiguration(Builder builder) {
        this.socketOptions = new HashMap<>(builder.socketOptions);
        this.port = builder.port;
        this.address = builder.address;
        this.backlog = builder.backlog;
        this.tls = builder.tls;
        this.connectionOptions = builder.connectionOptions;
        this.writeQueueLength = builder.writeQueueLength;
        this.maxPayloadSize = builder.maxPayloadSize;
    }

    /**
     * Create a new builder for a named socket.
     *
     * @param socketName name of the listener
     * @return a new builder
     */
    public static Builder builder(String socketName) {
        return new Builder(socketName);
    }

    /**
     * Create a default configuration, listening on a random port and on localhost.
     *
     * @param socketName name of the socket to create default configuration for
     * @return a new default socket configuration
     */
    public static ListenerConfiguration create(String socketName) {
        return builder(socketName)
                .port(0)
                .host("localhost")
                .build();
    }

    /**
     * Update the server socket with configured socket options.
     *
     * @param socket socket to update
     */
    public void configureSocket(ServerSocket socket) {
        for (Map.Entry<SocketOption, Object> entry : socketOptions.entrySet()) {
            try {
                socket.setOption(entry.getKey(), entry.getValue());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Maximal number of buffers to be queued in the write queue (when used).
     *
     * @return write queue length
     */
    public int writeQueueLength() {
        return writeQueueLength;
    }

    /**
     * Options for connections accepted by this listener.
     *
     * @return socket options
     */
    SocketOptions connectionOptions() {
        return connectionOptions;
    }

    long maxPayloadSize() {
        return maxPayloadSize;
    }

    int port() {
        return port;
    }

    InetAddress address() {
        return address;
    }

    int backlog() {
        return backlog;
    }

    boolean hasTls() {
        return tls != null && tls.enabled();
    }

    Tls tls() {
        if (tls == null) {
            throw new IllegalStateException("Requested TLS when none is defined, call hasTls() first");
        }
        if (!tls.enabled()) {
            throw new IllegalStateException("Requested TLS when it is disabled, call hasTls() first");
        }
        return tls;
    }

    /**
     * Fluent API builder for {@link io.helidon.nima.webserver.ListenerConfiguration}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, ListenerConfiguration> {
        private final Map<SocketOption, Object> socketOptions = new HashMap<>();

        private final String socketName;
        private final SocketOptions.Builder connectOptionsBuilder = SocketOptions.builder();
        private int port = 0;
        private InetAddress address;
        private int backlog = 1024;
        private Tls tls;
        private SocketOptions connectionOptions;
        private int writeQueueLength = 0;
        private long maxPayloadSize = -1;

        private Builder(String socketName) {
            this.socketName = socketName;
            this.socketOptions.put(StandardSocketOptions.SO_REUSEADDR, true);
            this.socketOptions.put(StandardSocketOptions.SO_RCVBUF, 4096);
        }

        @Override
        public ListenerConfiguration build() {
            if (address == null) {
                host("localhost");
            }
            if (connectionOptions == null) {
                connectionOptions = connectOptionsBuilder.build();
            }
            return new ListenerConfiguration(this);
        }

        /**
         * Port to bind to.
         *
         * @param port port
         * @return updated builder
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Host or IP address to bind to.
         *
         * @param host host
         * @return updated builder
         */
        public Builder host(String host) {
            try {
                this.address = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Failed to get address from host.", e);
            }
            return this;
        }

        /**
         * Address to bind to.
         *
         * @param address address
         * @return updated builder
         */
        public Builder bindAddress(InetAddress address) {
            this.address = address;
            return this;
        }

        /**
         * Accept backlog.
         *
         * @param backlog backlog
         * @return updated builder
         */
        public Builder backlog(int backlog) {
            this.backlog = backlog;
            return this;
        }

        /**
         * Listener receive buffer size.
         *
         * @param receiveBufferSize buffer size in bytes
         * @return updated builder
         */
        public Builder receiveBufferSize(int receiveBufferSize) {
            socketOptions.put(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
            return this;
        }

        /**
         * Listener TLS configuration.
         *
         * @param tls tls
         * @return updated builder
         */
        public Builder tls(Tls tls) {
            this.tls = tls;
            return this;
        }

        /**
         * Configure connection options for connections accepted by this listener.
         *
         * @param builderConsumer consumer of socket options builder
         * @return updated builder
         */
        public Builder connectionOptions(Consumer<SocketOptions.Builder> builderConsumer) {
            builderConsumer.accept(connectOptionsBuilder);
            return this;
        }

        /**
         * Number of buffers queued for write operations.
         *
         * @param writeQueueLength maximal number of queued writes, defaults to 32
         * @return updated builder
         */
        public Builder writeQueueLength(int writeQueueLength) {
            this.writeQueueLength = writeQueueLength;
            return this;
        }

        /**
         * Maximal number of bytes an entity may have.
         * If {@link io.helidon.common.http.Http.Header#CONTENT_LENGTH} is used, this is checked immediately,
         * if {@link io.helidon.common.http.Http.HeaderValues#TRANSFER_ENCODING_CHUNKED} is used, we will fail when the
         * number of bytes read would exceed the max payload size.
         * Defaults to unlimited ({@code -1}).
         *
         * @param maxPayloadSize maximal number of bytes of entity
         */
        public void maxPayloadSize(long maxPayloadSize) {
            this.maxPayloadSize = maxPayloadSize;
        }
    }
}
