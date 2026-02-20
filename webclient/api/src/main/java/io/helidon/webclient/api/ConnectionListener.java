/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.api;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.Objects;

/**
 * This interface provides callback methods that are invoked when the client connection is first established,
 * before any TLS or HTTP application traffic is sent. This can be used for cases such as the HAProxy
 * Proxy Protocol, which involves sending protocol-specific data bytes on the connection before the TLS
 * handshake occurs.
 */
public interface ConnectionListener {
    /**
     * Returns a {@link ConnectionListener} which does nothing.
     * @return The no-op listener.
     */
    static ConnectionListener noop() {
        return new DefaultConnectionListener();
    }

    /**
     * Called when the given {@link Socket} connection has been established and {@link io.helidon.common.socket.SocketOptions}
     * applied, but before any TLS or HTTP application traffic has been sent.
     * @param socket The newly connected socket.
     */
    void socketConnected(ConnectedSocket socket) throws IOException;

    /**
     * Called when the given {@link SocketChannel} connection has been established and
     * {@link io.helidon.common.socket.SocketOptions} applied, but before any TLS or HTTP application traffic has been sent.
     * @param socket The newly connected socket channel.
     */
    void socketConnected(ConnectedSocketChannel socket) throws IOException;

    /**
     * Context information about a newly connected {@link Socket}.
     * @param socket The socket itself.
     * @param channelId The channel id.
     */
    record ConnectedSocket(Socket socket, String channelId) {
        /**
         * Canonical constructor, enforces null checks.
         * @param socket The socket itself.
         * @param channelId The channel id.
         */
        public ConnectedSocket(final Socket socket, final String channelId) {
            this.socket = Objects.requireNonNull(socket);
            this.channelId = Objects.requireNonNull(channelId);
        }
    }

    /**
     * Context information about a newly connected {@link SocketChannel}.
     * @param socket The socket itself.
     * @param channelId The channel id.
     */
    record ConnectedSocketChannel(SocketChannel socket, String channelId) {
        /**
         * Canonical constructor, enforces null checks.
         * @param socket The socket itself.
         * @param channelId The channel id.
         */
        public ConnectedSocketChannel(final SocketChannel socket, final String channelId) {
            this.socket = Objects.requireNonNull(socket);
            this.channelId = Objects.requireNonNull(channelId);
        }
    }
}
