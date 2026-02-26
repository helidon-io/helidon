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
    static ConnectionListener createNoop() {
        return new DefaultConnectionListener();
    }

    /**
     * Returns a {@link ConnectionListener} which writes bytes to the socket once the socket is connected.
     * @param bytes The bytes to write.
     * @return The listener which will write the given bytes to the socket.
     */
    static ConnectionListener createWriteOnConnect(byte[] bytes) {
        return new BytesWritingConnectionListener(bytes);
    }

    /**
     * Called when the given {@link Socket} connection has been established and {@link io.helidon.common.socket.SocketOptions}
     * applied, but before any TLS or HTTP application traffic has been sent.
     * @param socketInfo The newly connected socket.
     */
    void socketConnected(ConnectedSocketInfo socketInfo) throws IOException;

    /**
     * Called when the given {@link SocketChannel} connection has been established and
     * {@link io.helidon.common.socket.SocketOptions} applied, but before any TLS or HTTP application traffic has been sent.
     * @param socketInfo The newly connected socket channel.
     */
    void socketChannelConnected(ConnectedSocketChannelInfo socketInfo) throws IOException;
}
