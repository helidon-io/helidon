/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.net.ssl.SSLEngine;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.NioSocket;
import io.helidon.common.socket.TlsNioSocket;
import io.helidon.common.tls.Tls;

import static io.helidon.webclient.api.TcpClientConnection.debugTls;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

/**
 * Client connection to a UNIX domain socket.
 */
public class UnixDomainSocketClientConnection implements ClientConnection {
    private static final System.Logger LOGGER = System.getLogger(UnixDomainSocketClientConnection.class.getName());
    private final WebClient webClient;
    private final Tls tls;
    private final UnixDomainSocketAddress address;
    private final List<String> alpnId;
    private final Function<UnixDomainSocketClientConnection, Boolean> releaseFunction;
    private final Consumer<UnixDomainSocketClientConnection> closeConsumer;

    private String channelId;
    private SocketChannel channel;
    private HelidonSocket socket;
    private DataReader reader;
    private DataWriter writer;
    private boolean closed;
    private boolean allowExpectContinue = true;

    private UnixDomainSocketClientConnection(WebClient webClient,
                                             Tls tls,
                                             UnixDomainSocketAddress address,
                                             List<String> alpnId,
                                             Function<UnixDomainSocketClientConnection, Boolean> releaseFunction,
                                             Consumer<UnixDomainSocketClientConnection> closeConsumer) {
        this.webClient = webClient;
        this.tls = tls;
        this.address = address;
        this.alpnId = alpnId;
        this.releaseFunction = releaseFunction;
        this.closeConsumer = closeConsumer;
    }

    /**
     * Create a new UNIX Domain Socket Connection.
     *
     * @param webClient       webclient, to get configuration
     * @param tls             TLS configuration
     * @param tcpProtocolIds  protocol IDs for ALPN (TLS protocol negotiation)
     * @param address         address of the socket
     * @param releaseFunction called when {@link #releaseResource()} is called, if {@code false} is returned, the connection will
     *                        be closed instead kept open
     * @param closeConsumer   called when {@link #closeResource()} is called, the connection is no longer usable after this moment
     * @return a new UNIX domain socket connection, {@link #connect()} must be called to make it available for use
     */
    public static UnixDomainSocketClientConnection create(WebClient webClient,
                                                          Tls tls,
                                                          List<String> tcpProtocolIds,
                                                          UnixDomainSocketAddress address,
                                                          Function<UnixDomainSocketClientConnection, Boolean> releaseFunction,
                                                          Consumer<UnixDomainSocketClientConnection> closeConsumer) {
        return new UnixDomainSocketClientConnection(webClient,
                                                    tls,
                                                    address,
                                                    tcpProtocolIds,
                                                    releaseFunction,
                                                    closeConsumer);
    }

    @Override
    public DataReader reader() {
        if (closed) {
            throw new IllegalStateException("Attempt to call reader() on a closed connection");
        }

        if (reader == null) {
            throw new IllegalStateException("Attempt to call reader() on a connection that is not connected");
        }

        return reader;
    }

    @Override
    public DataWriter writer() {
        if (closed) {
            throw new IllegalStateException("Attempt to call writer() on a closed connection");
        }

        if (writer == null) {
            throw new IllegalStateException("Attempt to call writer() on a connection that is not connected");
        }

        return writer;
    }

    @Override
    public String channelId() {
        return channelId;
    }

    @Override
    public boolean allowExpectContinue() {
        return allowExpectContinue;
    }

    @Override
    public void allowExpectContinue(boolean allowExpectContinue) {
        this.allowExpectContinue = allowExpectContinue;
    }

    @Override
    public HelidonSocket helidonSocket() {
        return socket;
    }

    @Override
    public void readTimeout(Duration readTimeout) {
        // read timeout is not supported for UNIX domain sockets
    }

    @Override
    public boolean isConnected() {
        return !closed && channel.isConnected();
    }

    @Override
    public void closeResource() {
        if (closed) {
            return;
        }
        try {
            this.channel.close();
        } catch (IOException e) {
            LOGGER.log(TRACE, "Failed to close a client socket channel", e);
        }
        this.closed = true;
        closeConsumer.accept(this);
    }

    @Override
    public void releaseResource() {
        if (closed) {
            return;
        }
        if (!releaseFunction.apply(this)) {
            closeResource();
        }
    }

    /**
     * Connect this connection to the socket.
     *
     * @return this connection, connected to the UNIX domain socket
     */
    @Override
    public UnixDomainSocketClientConnection connect() {
        try {
            this.channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            this.channel.connect(this.address);
            this.channelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(this.channel));

            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, String.format("[client %s] UNIX socket client connected %s %s",
                                                channelId,
                                                address.getPath().toString(),
                                                Thread.currentThread().getName()));
            }

            this.webClient.prototype()
                    .socketOptions()
                    .configureSocket(this.channel);

            if (this.tls.enabled()) {
                SSLEngine engine = this.tls.sslContext().createSSLEngine();
                engine.setEnabledProtocols(this.alpnId.toArray(new String[0]));

                if (LOGGER.isLoggable(TRACE)) {
                    debugTls(engine, channelId);
                }

                this.socket = TlsNioSocket.client(this.channel, engine, this.channelId);
            } else {
                this.socket = NioSocket.client(this.channel, this.channelId);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        this.reader = DataReader.create(this.socket);
        int writeBufferSize = this.webClient.prototype().writeBufferSize();
        this.writer = new TcpClientConnection.BufferedDataWriter(this.socket, writeBufferSize);

        return this;
    }

}
