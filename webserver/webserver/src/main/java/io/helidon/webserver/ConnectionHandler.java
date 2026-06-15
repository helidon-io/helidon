/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.NioSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.PlainSocket;
import io.helidon.common.socket.SocketWriter;
import io.helidon.common.socket.TlsNioSocket;
import io.helidon.common.socket.TlsSocket;
import io.helidon.common.task.InterruptableTask;
import io.helidon.common.tls.Tls;
import io.helidon.http.HttpException;
import io.helidon.http.RequestException;
import io.helidon.webserver.spi.ServerConnection;
import io.helidon.webserver.spi.ServerConnectionSelector;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

/**
 * Representation of a single channel between client and server.
 * Everything in this class runs in the channel reader virtual thread
 */
class ConnectionHandler implements InterruptableTask<Void>, ConnectionContext {
    private static final System.Logger LOGGER = System.getLogger(ConnectionHandler.class.getName());
    private static final String HTTP_1_0 = "HTTP/1.0\r";
    private static final byte TLS_ALERT_CONTENT_TYPE = 21;
    private static final byte TLS_ALERT_LEVEL_FATAL = 2;
    private static final byte TLS_ALERT_UNRECOGNIZED_NAME = 112;

    private final ListenerContext listenerContext;
    // we must safely release the token whenever this connection is finished, so other connections can be created!
    private final LimitAlgorithm.Token limitToken;
    private final Limit requestLimit;
    private final ConnectionProviders connectionProviders;
    private final List<ServerConnectionSelector> providerCandidates;
    private final SocketChannel socket;
    private final String serverChannelId;
    private final Router router;
    private final Tls tls;
    private final ListenerTlsContext listenerTls;
    private final ListenerConfig listenerConfig;
    private final String channelId;
    private final Consumer<ConnectionHandler> connectionHandlerRemoveListener;
    private final ReentrantLock closeLock = new ReentrantLock();

    private String socketIds;
    private boolean closeRequested;
    private boolean closeInterrupt;
    private boolean handlingStarted;
    private DataReader reader;
    private SocketWriter writer;
    private ProxyProtocolData proxyProtocolData;
    private SniContext sniContext;

    // Published before handling starts so lifecycle close/interrupt paths do not miss the delegate.
    private volatile ServerConnection connection;
    // Published so concurrent close uses the wrapped socket once setup reaches that stage.
    private volatile HelidonSocket helidonSocket;

    ConnectionHandler(ListenerContext listenerContext,
                      LimitAlgorithm.Token limitToken,
                      Limit requestLimit,
                      ConnectionProviders connectionProviders,
                      SocketChannel socket,
                      String serverChannelId,
                      Router router,
                      Tls tls,
                      ListenerTlsContext listenerTls,
                      Consumer<ConnectionHandler> connectionHandlerRemoveListener) {
        this.listenerContext = listenerContext;
        this.limitToken = limitToken;
        this.requestLimit = requestLimit;
        this.connectionProviders = connectionProviders;
        this.providerCandidates = connectionProviders.providerCandidates();
        this.socket = socket;
        this.serverChannelId = serverChannelId;
        this.router = router;
        this.tls = tls;
        this.listenerTls = listenerTls;
        this.listenerConfig = listenerContext.config();
        this.connectionHandlerRemoveListener = connectionHandlerRemoveListener;
        this.channelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(socket));
    }

    @Override
    public boolean canInterrupt() {
        return connection instanceof InterruptableTask<?> task && task.canInterrupt();
    }

    @Override
    public final void run() {
        try {
            try {
                run(channelId);
            } catch (Throwable e) {
                LOGGER.log(ERROR, "Unexpected throwable while handling connection", e);
            }
        } finally {
            releaseConnectionLimit(handlingStarted);
            if (writer != null) {
                writer.close();
            }
            closeChannel();
            if (helidonSocket != null) {
                helidonSocket.log(LOGGER, DEBUG, "socket closed");
            }
            connectionHandlerRemoveListener.accept(this);
        }
    }

    @Override
    public PeerInfo remotePeer() {
        return helidonSocket.remotePeer();
    }

    @Override
    public PeerInfo localPeer() {
        return helidonSocket.localPeer();
    }

    @Override
    public boolean isSecure() {
        return helidonSocket.isSecure();
    }

    @Override
    public String socketId() {
        return helidonSocket.socketId();
    }

    @Override
    public String childSocketId() {
        return helidonSocket.childSocketId();
    }

    @Override
    public ListenerContext listenerContext() {
        return listenerContext;
    }

    @Override
    public ExecutorService executor() {
        return listenerContext.executor();
    }

    @Override
    public DataWriter dataWriter() {
        return writer;
    }

    @Override
    public DataReader dataReader() {
        return reader;
    }

    @Override
    public Router router() {
        return router;
    }

    @Override
    public Optional<ProxyProtocolData> proxyProtocolData() {
        return Optional.ofNullable(proxyProtocolData);
    }

    @Override
    public Optional<SniContext> sniContext() {
        return Optional.ofNullable(sniContext);
    }

    @Override
    public HelidonSocket serverSocket() {
        return helidonSocket;
    }

    void close(boolean interrupt) {
        ServerConnection localConnection;
        boolean localCloseInterrupt;
        boolean closeChannel;
        closeLock.lock();
        try {
            if (interrupt) {
                closeInterrupt = true;
            }
            closeRequested = true;
            localConnection = connection;
            localCloseInterrupt = closeInterrupt;
            closeChannel = !handlingStarted;
        } finally {
            closeLock.unlock();
        }
        if (closeChannel) {
            closeChannel();
        }
        if (localConnection != null) {
            localConnection.close(localCloseInterrupt);
        }
    }

    void closeIfIdle(Duration timeout) {
        ServerConnection localConnection;
        closeLock.lock();
        try {
            if (!handlingStarted || closeRequested) {
                return;
            }
            localConnection = connection;
        } finally {
            closeLock.unlock();
        }
        if (localConnection != null && localConnection.idleTime().compareTo(timeout) > 0) {
            // this should be a graceful shutdown, in case a request is received in parallel, we want to handle
            // it, and yes, then it would be closed (and it must not accept another request)
            close(false);
        }
    }

    static boolean isHttp10Connection(DataReader reader) {
        try {
            reader.ensureAvailable();
        } catch (DataReader.InsufficientDataAvailableException e) {
            throw new CloseConnectionException("No data available", e);
        }
        BufferData request = reader.getBuffer(reader.available());
        int lf = request.indexOf(Bytes.LF_BYTE);
        return lf != -1 && request.readString(lf).endsWith(HTTP_1_0);
    }

    // extracted run method to make the run method clean (a single try/finally block)
    private void run(String channelId) {
        // proxy protocol before SSL handshake
        if (listenerConfig.enableProxyProtocol()) {
            ProxyProtocolHandler handler = new ProxyProtocolHandler(socket, channelId);
            try {
                proxyProtocolData = handler.get();
            } catch (RuntimeException e) {
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, "[" + channelId + "] Failed to retrieve Proxy Protocol data", e);
                }
                return;
            }
        }

        // handle SSL and init helidonSocket, reader and writer
        try {
            helidonSocket = createSocket(tls, socket, channelId);

            reader = DataReader.create(new MapExceptionDataSupplier(helidonSocket));
            writer = SocketWriter.create(listenerContext.executor(),
                                         helidonSocket,
                                         listenerConfig.writeQueueLength(),
                                         listenerConfig.smartAsyncWrites());
        } catch (RuntimeException e) {
            // these exceptions are thrown to the executor service
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "[" + channelId + "] Failed to establish connection", e);
            }
            return;
        } catch (Exception e) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "[" + channelId + "] Failed to establish connection", e);
            }
            return;
        }

        // connection handling
        socketIds = helidonSocket.socketId() + " " + helidonSocket.childSocketId();
        Thread.currentThread().setName("[" + socketIds + "] WebServer socket");
        if (LOGGER.isLoggable(DEBUG)) {
            helidonSocket.log(LOGGER,
                              DEBUG,
                              "accepted socket from %s:%d",
                              helidonSocket.remotePeer().host(),
                              helidonSocket.remotePeer().port());
        }

        try {
            ServerConnection selectedConnection = null;
            if (helidonSocket.protocolNegotiated()) {
                selectedConnection = connectionProviders.byApplicationProtocol(helidonSocket.protocol())
                        .connection(this);
            }

            if (selectedConnection == null) {
                selectedConnection = identifyConnection();
            }

            if (selectedConnection == null) {
                if (isHttp10Connection(reader)) {
                    // cannot easily return 505, so log better message instead
                    throw new CloseConnectionException("HTTP 1.0 is not supported, consider using HTTP 1.1");
                }
                throw new CloseConnectionException("No suitable connection provider");
            }
            if (!startHandling(selectedConnection)) {
                close(false);
                return;
            }
            selectedConnection.handle(requestLimit);
        } catch (RequestException e) {
            helidonSocket.log(LOGGER, WARNING, "escaped Request exception", e);
        } catch (HttpException e) {
            helidonSocket.log(LOGGER, WARNING, "escaped HTTP exception", e);
        } catch (ServerConnectionException e) {
            // socket exception - the socket failed, probably killed by OS, proxy or client
            helidonSocket.log(LOGGER, TRACE, "server I/O issue", e);
        } catch (CloseConnectionException e) {
            // end of request stream - safe to close the connection, as it was requested by our client
            helidonSocket.log(LOGGER, TRACE, "connection close requested", e);
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof SocketException) {
                // socket exception - the socket failed, probably killed by OS, proxy or client
                helidonSocket.log(LOGGER, TRACE, "server I/O issue", e);
            } else {
                helidonSocket.log(LOGGER, WARNING, "unexpected I/O exception", e);
            }
        } catch (Exception e) {
            helidonSocket.log(LOGGER, WARNING, "unexpected exception", e);
        }
    }

    private void releaseConnectionLimit(boolean handlingStarted) {
        if (handlingStarted) {
            limitToken.success();
        } else {
            limitToken.ignore();
        }
    }

    private boolean startHandling(ServerConnection selectedConnection) {
        closeLock.lock();
        try {
            connection = selectedConnection;
            if (closeRequested) {
                return false;
            }
            handlingStarted = true;
            return true;
        } finally {
            closeLock.unlock();
        }
    }

    private HelidonSocket createSocket(Tls tls, SocketChannel socket, String channelId) throws IOException {
        if (listenerConfig.useNio()) {
            return createNioSocket(tls, socket, channelId);
        }
        return createByteSocket(tls, socket, channelId);
    }

    private HelidonSocket createNioSocket(Tls tls, SocketChannel channel, String channelId) throws IOException {
        if (tls.enabled()) {
            ByteBuffer replayBuffer = null;
            Tls selectedTls = tls;
            if (listenerTls.virtualHostsEnabled()) {
                ClientHelloPrefaceReader.ClientHelloPreface preface =
                        ClientHelloPrefaceReader.read(channel, listenerConfig.connectionOptions().readTimeout());
                ListenerTlsContext.Selection selection;
                try {
                    selection = preface.sniHost()
                            .map(listenerTls::select)
                            .orElseGet(listenerTls::selectWithoutSni);
                } catch (ListenerTlsContext.RejectedSniException e) {
                    if (e.sendUnrecognizedNameAlert()) {
                        sendUnrecognizedNameAlert(channel, preface.replayBuffer());
                    }
                    throw e;
                }
                selectedTls = selection.tls();
                sniContext = selection.sniContext();
                replayBuffer = preface.replayBuffer();
            }
            var address = channel.getRemoteAddress();

            SSLEngine engine;
            if (address instanceof InetSocketAddress isa) {
                engine = selectedTls.sslContext().createSSLEngine(isa.getHostString(), isa.getPort());
            } else {
                engine = selectedTls.sslContext().createSSLEngine();
            }

            SSLParameters parameters = selectedTls.sslParameters();
            parameters.setEndpointIdentificationAlgorithm("");
            engine.setSSLParameters(parameters);

            engine.setHandshakeApplicationProtocolSelector((sslEngine, list) -> {
                for (String protocolId : list) {
                    if (connectionProviders.supportedApplicationProtocols()
                            .contains(protocolId)) {
                        return protocolId;
                    }
                }
                return null;
            });

            if (replayBuffer == null) {
                return TlsNioSocket.server(channel, engine, channelId, serverChannelId);
            }
            return TlsNioSocket.server(channel, engine, channelId, serverChannelId, replayBuffer);
        }
        return NioSocket.server(channel, channelId, serverChannelId);
    }

    private void sendUnrecognizedNameAlert(SocketChannel channel, ByteBuffer replayBuffer) throws IOException {
        ByteBuffer replay = replayBuffer.duplicate();
        byte recordMajor = 3;
        byte recordMinor = 3;
        if (replay.remaining() >= 3) {
            int position = replay.position();
            recordMajor = replay.get(position + 1);
            recordMinor = replay.get(position + 2);
        }
        ByteBuffer alert = ByteBuffer.wrap(new byte[] {
                TLS_ALERT_CONTENT_TYPE,
                recordMajor,
                recordMinor,
                0,
                2,
                TLS_ALERT_LEVEL_FATAL,
                TLS_ALERT_UNRECOGNIZED_NAME
        });
        while (alert.hasRemaining()) {
            channel.write(alert);
        }
    }

    private HelidonSocket createByteSocket(Tls tls, SocketChannel channel, String channelId) throws IOException {
        if (tls.enabled()) {
            if (listenerTls.virtualHostsEnabled()) {
                throw new IllegalStateException("Listener virtual hosts require NIO TLS");
            }
            SSLSocket sslSocket = (SSLSocket) tls.sslContext()
                    .getSocketFactory()
                    .createSocket(channel.socket(), null, false);
            sslSocket.setHandshakeApplicationProtocolSelector(
                    (sslEngine, list) -> {
                        for (String protocolId : list) {
                            if (connectionProviders.supportedApplicationProtocols()
                                    .contains(protocolId)) {
                                return protocolId;
                            }
                        }
                        return null;
                    });
            sslSocket.startHandshake();
            return TlsSocket.server(sslSocket, channelId, serverChannelId);
        }
        return PlainSocket.server(channel.socket(), channelId, serverChannelId);
    }

    private ServerConnection identifyConnection() {
        // if just one candidate, take a chance with it
        if (providerCandidates.size() == 1) {
            return providerCandidates.getFirst().connection(this);
        }

        try {
            reader.ensureAvailable();
        } catch (DataReader.InsufficientDataAvailableException e) {
            throw new CloseConnectionException("No data available", e);
        }

        // never move position of the reader
        BufferData currentBuffer = reader.getBuffer(reader.available());

        // go through candidates until we identify what kind of connection we have (now this will be either HTTP/1 or HTTP/2)
        while (true) {
            Iterator<ServerConnectionSelector> iterator = providerCandidates.iterator();
            if (!iterator.hasNext()) {
                helidonSocket.log(LOGGER, DEBUG, "Could not find a suitable connection provider. "
                                + "initial connection buffer bytes=%d",
                        currentBuffer.available());
                return null;
            }

            // check if we can identify which connection to use based on the available data
            while (iterator.hasNext()) {
                ServerConnectionSelector candidate = iterator.next();
                int expectedBytes = candidate.bytesToIdentifyConnection();

                ServerConnectionSelector.Support supports;
                if (expectedBytes == 0 || expectedBytes < currentBuffer.available()) {
                    supports = candidate.supports(currentBuffer);
                } else {
                    // we need more data, let's keep this provider for now
                    continue;
                }

                switch (supports) {
                case SUPPORTED -> {
                    return candidate.connection(this);
                }
                // we are no longer interested in this connection provider, remove it from our list
                case UNSUPPORTED -> iterator.remove();
                case UNKNOWN -> {
                    // we may still use it if it accepts any # of bytes, otherwise remove it (and this is a
                    // wrong response...)
                    if (expectedBytes != 0) {
                        iterator.remove();
                    }
                }
                default -> throw new IllegalStateException("Unknown support (" + supports
                                                                   + ") returned from provider "
                                                                   + candidate.getClass().getName());
                }
                currentBuffer.rewind();
            }

            // we may have removed all candidates, we must re-check
            // we must return before requesting more data (as more data may not be available)
            if (providerCandidates.isEmpty()) {
                helidonSocket.log(LOGGER,
                        DEBUG,
                        "Could not find a suitable connection provider. "
                                + "initial connection buffer bytes=%d",
                        currentBuffer.available());

                return null;
            }

            // read next data (need to create a copy, to make sure we do not re-use the same buffer)
            currentBuffer = reader.getBuffer(reader.available() + 1); // for additional read
        }
    }

    private void closeChannel() {
        if (helidonSocket == null) {
            try {
                socket.close();
            } catch (Throwable e) {
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, "[" + channelId + "] Failed to establish connection", e);
                }
            }
        } else {
            try {
                helidonSocket.close();
            } catch (Throwable e) {
                helidonSocket.log(LOGGER, TRACE, "Failed to close socket on connection close", e);
            }
        }
    }

    private static class MapExceptionDataSupplier implements Supplier<byte[]> {
        private final HelidonSocket helidonSocket;

        private MapExceptionDataSupplier(HelidonSocket helidonSocket) {
            this.helidonSocket = helidonSocket;
        }

        @Override
        public byte[] get() {
            try {
                return helidonSocket.get();
            } catch (UncheckedIOException e) {
                throw new ServerConnectionException("Failed to get data from socket", e);
            }
        }
    }
}
