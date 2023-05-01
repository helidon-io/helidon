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
import java.util.Objects;
import java.util.function.Consumer;

import io.helidon.common.context.Context;
import io.helidon.common.http.DirectHandler;
import io.helidon.common.http.RequestedUriDiscoveryContext;
import io.helidon.common.socket.SocketOptions;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.udp.UdpEndpoint;
import io.helidon.nima.webserver.http.DirectHandlers;

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
    private final int writeBufferSize;
    private final ContentEncodingContext contentEncodingContext;
    private final MediaContext mediaContext;
    private final DirectHandlers directHandlers;
    private final Context context;
    private final RequestedUriDiscoveryContext discoveryContext;
    private final boolean udp;
    private final UdpEndpoint udpEndpoint;

    private ListenerConfiguration(Builder builder) {
        this.socketOptions = new HashMap<>(builder.socketOptions);
        this.port = builder.port;
        this.address = builder.address;
        this.backlog = builder.backlog;
        this.tls = builder.tls;
        this.connectionOptions = builder.connectionOptions;
        this.writeQueueLength = builder.writeQueueLength;
        this.maxPayloadSize = builder.maxPayloadSize;
        this.writeBufferSize = builder.writeBufferSize;
        this.contentEncodingContext = builder.contentEncodingContext;
        this.mediaContext = builder.mediaContext;
        this.directHandlers = builder.directHandlers.build();
        this.context = builder.context;
        this.discoveryContext = builder.discoveryContext;
        this.udp = builder.udp;
        this.udpEndpoint = builder.udpEndpoint;
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
     * Content encoding context of this listener.
     *
     * @return content encoding context, never {@code null}
     */
    public ContentEncodingContext contentEncodingContext() {
        return contentEncodingContext;
    }

    /**
     * Media context of this listener.
     *
     * @return media context, never {@code null}
     */
    public MediaContext mediaContext() {
        return mediaContext;
    }

    /**
     * Maximal payload size (in bytes).
     *
     * @return maximal allowed payload size
     */
    public long maxPayloadSize() {
        return maxPayloadSize;
    }

    /**
     * Initial buffer size of {@link java.io.BufferedOutputStream} created internally.
     *
     * @return initial buffer size for writing (in bytes)
     */
    public int writeBufferSize() {
        return writeBufferSize;
    }

    /**
     * Configured direct handlers.
     *
     * @return direct handlers
     */
    public DirectHandlers directHandlers() {
        return directHandlers;
    }

    /**
     * Configured context.
     *
     * @return context
     */
    public Context context() {
        return context;
    }

    /**
     * Requuested URI discovery context.
     *
     * @return discovery context
     */
    public RequestedUriDiscoveryContext requestedUriDiscoveryContext() {
        return discoveryContext;
    }

    /**
     * Options for connections accepted by this listener.
     *
     * @return socket options
     */
    SocketOptions connectionOptions() {
        return connectionOptions;
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

    boolean udp() {
        return udp;
    }

    UdpEndpoint udpEndpoint() {
        return udpEndpoint;
    }

    /**
     * Fluent API builder for {@link io.helidon.nima.webserver.ListenerConfiguration}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, ListenerConfiguration> {
        private final Map<SocketOption, Object> socketOptions = new HashMap<>();
        private final SocketOptions.Builder connectOptionsBuilder = SocketOptions.builder();
        private final DirectHandlers.Builder directHandlers = DirectHandlers.builder();
        private final Context.Builder contextBuilder = Context.builder();
        private final String socketName;

        private int port = 0;
        private InetAddress address;
        private int backlog = 1024;
        private Tls tls;
        private SocketOptions connectionOptions;
        private int writeQueueLength = 0;
        private long maxPayloadSize = -1;
        private int writeBufferSize = 512;
        private ContentEncodingContext contentEncodingContext;
        private MediaContext mediaContext;
        private Context context;
        private RequestedUriDiscoveryContext discoveryContext;
        private boolean udp = false;
        private UdpEndpoint udpEndpoint;

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
            if (context == null) {
                context = contextBuilder.id("listener-" + address)
                        .build();
            }
            // we need to check for null, when we do not configure defaults from webserver
            if (contentEncodingContext == null) {
                contentEncodingContext = ContentEncodingContext.create();
            }
            if (mediaContext == null) {
                mediaContext = MediaContext.create();
            }
            if (discoveryContext == null) {
                discoveryContext = RequestedUriDiscoveryContext.builder()
                        .socketId(socketName)
                        .build();
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
            Objects.requireNonNull(host);
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
            Objects.requireNonNull(address);
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
            Objects.requireNonNull(tls);
            this.tls = tls;
            return this;
        }

        /**
         * Set listener for UDP.
         *
         * @param udp udp flag
         * @return updated builder
         */
        public Builder udp(boolean udp) {
            this.udp = udp;
            return this;
        }

        /**
         * Configure UDP endpoint for this listener.
         *
         * @param udpEndpoint the endpoint
         * @return updated builder
         */
        public Builder udpEndpoint(UdpEndpoint udpEndpoint) {
            if (!udp) {
                throw new IllegalArgumentException("Socket listener not of UDP type");
            }
            this.udpEndpoint = udpEndpoint;
            return this;
        }

        /**
         * Configure connection options for connections accepted by this listener.
         *
         * @param builderConsumer consumer of socket options builder
         * @return updated builder
         */
        public Builder connectionOptions(Consumer<SocketOptions.Builder> builderConsumer) {
            Objects.requireNonNull(builderConsumer);
            builderConsumer.accept(connectOptionsBuilder);
            return this;
        }

        /**
         * Number of buffers queued for write operations.
         *
         * @param writeQueueLength maximal number of queued writes, defaults to 0
         * @return updated builder
         */
        public Builder writeQueueLength(int writeQueueLength) {
            this.writeQueueLength = writeQueueLength;
            return this;
        }

        /**
         * Initial buffer size in bytes of {@link java.io.BufferedOutputStream} created internally to
         * write data to a socket connection. Default is {@code 512}.
         *
         * @param writeBufferSize initial buffer size used for writing
         * @return updated builder
         */
        public Builder writeBufferSize(int writeBufferSize) {
            this.writeBufferSize = writeBufferSize;
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
         * @return updated builder
         */
        public Builder maxPayloadSize(long maxPayloadSize) {
            this.maxPayloadSize = maxPayloadSize;
            return this;
        }

        /**
         * Configure the listener specific {@link MediaContext}.
         * This method discards all previously registered MediaContext.
         * If no media context is registered, media context of the webserver would be used.
         *
         * @param mediaContext media context
         * @return updated instance of the builder
         */
        public Builder mediaContext(MediaContext mediaContext) {
            Objects.requireNonNull(mediaContext);
            this.mediaContext = mediaContext;
            return this;
        }

        /**
         * Configure the listener specific {@link ContentEncodingContext}.
         * This method discards all previously registered ContentEncodingContext.
         * If no content encoding context is registered, content encoding context of the webserver would be used.
         *
         * @param contentEncodingContext content encoding context
         * @return updated instance of the builder
         */
        public Builder contentEncodingContext(ContentEncodingContext contentEncodingContext) {
            Objects.requireNonNull(contentEncodingContext);
            this.contentEncodingContext = contentEncodingContext;
            return this;
        }

        /**
         * Configure a simple handler specific for this listener.
         *
         * @param handler    handler to use
         * @param eventTypes event types this handler should handle
         * @return updated builder
         */
        public Builder directHandler(DirectHandler handler, DirectHandler.EventType... eventTypes) {
            Objects.requireNonNull(handler);
            Objects.requireNonNull(eventTypes);

            for (DirectHandler.EventType eventType : eventTypes) {
                directHandlers.addHandler(eventType, handler);
            }

            return this;
        }

        /**
         * Configure listener scoped context to be used as a parent for webserver request contexts.
         * If an explicit context is used, you need to take care of correctly configuring its parent.
         * It is expected that the parent of this context is the WebServer context. You should also configure explicit
         * WebServer context when using this method
         *
         * @param context top level context
         * @return an updated builder
         * @see io.helidon.nima.webserver.WebServer.Builder#context(io.helidon.common.context.Context)
         */
        public Builder context(Context context) {
            Objects.requireNonNull(context);
            this.context = context;
            return this;
        }

        /**
         * Configure the requested URI discovery context for this listener.
         *
         * @param discoveryContext the {@linkplain io.helidon.common.http.RequestedUriDiscoveryContext discovery context}
         * @return an updated builder
         */
        public Builder requestedUriDiscovery(RequestedUriDiscoveryContext discoveryContext) {
            this.discoveryContext = discoveryContext;
            return this;
        }

        Builder defaultDirectHandlers(DirectHandlers directHandlers) {
            this.directHandlers.defaults(directHandlers);
            return this;
        }

        Builder defaultMediaContext(MediaContext mediaContext) {
            if (this.mediaContext == null) {
                this.mediaContext = mediaContext;
            }
            return this;
        }

        Builder defaultContentEncodingContext(ContentEncodingContext encodingContext) {
            if (this.contentEncodingContext == null) {
                this.contentEncodingContext = encodingContext;
            }
            return this;
        }

        Builder parentContext(Context context) {
            contextBuilder.parent(context);
            return this;
        }
    }
}
