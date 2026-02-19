package io.helidon.webclient.api;

import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.Objects;

/**
 * This interface provides callback methods that are invoked when a client connection is first established,
 * before any TLS or HTTP application traffic is sent. This can be used for use cases such as the HAProxy
 * Proxy Protocol, which involves sending protocol-specific data bytes on the connection before the TLS
 * handshake occurs.
 */
public interface ConnectionInitializer {
    /**
     * The default initializer, which does nothing.
     * @return The no-op default initializer.
     */
    static ConnectionInitializer noop() {
        return new DefaultConnectionInitializer();
    }

    /**
     * Called when the given {@link Socket} connection has been established and {@link io.helidon.common.socket.SocketOptions}
     * applied, but before any TLS or HTTP application traffic has been sent.
     * @param socket The newly connected socket.
     */
    void initializeConnectedSocket(ConnectedSocket socket);

    /**
     * Called when the given {@link SocketChannel} connection has been established and
     * {@link io.helidon.common.socket.SocketOptions} applied, but before any TLS or HTTP application traffic has been sent.
     * @param socket The newly connected socket channel.
     */
    void initializeConnectedSocket(ConnectedSocketChannel socket);

    /**
     * Context information about a newly connected {@link Socket}.
     * @param socket The socket itself.
     * @param channelId The channel id.
     */
    record ConnectedSocket(Socket socket, String channelId) {
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
        public ConnectedSocketChannel(final SocketChannel socket, final String channelId) {
            this.socket = Objects.requireNonNull(socket);
            this.channelId = Objects.requireNonNull(channelId);
        }
    }
}
