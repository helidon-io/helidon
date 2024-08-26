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

package io.helidon.webserver;

import java.net.Socket;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

import javax.net.ssl.SSLSocket;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.PlainSocket;
import io.helidon.common.socket.SocketWriter;
import io.helidon.common.socket.TlsSocket;
import io.helidon.common.task.InterruptableTask;
import io.helidon.common.tls.Tls;
import io.helidon.http.HttpException;
import io.helidon.http.RequestException;
import io.helidon.webserver.spi.ServerConnection;
import io.helidon.webserver.spi.ServerConnectionSelector;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

/**
 * Representation of a single channel between client and server.
 * Everything in this class runs in the channel reader virtual thread
 */
class ConnectionHandler implements InterruptableTask<Void>, ConnectionContext {
    private static final System.Logger LOGGER = System.getLogger(ConnectionHandler.class.getName());

    private final ListenerContext listenerContext;
    // we must safely release the semaphore whenever this connection is finished, so other connections can be created!
    private final Semaphore connectionSemaphore;
    private final Semaphore requestSemaphore;
    private final ConnectionProviders connectionProviders;
    private final List<ServerConnectionSelector> providerCandidates;
    private final Map<String, ServerConnection> activeConnections;
    private final Socket socket;
    private final String serverChannelId;
    private final Router router;
    private final Tls tls;
    private final ListenerConfig listenerConfig;

    private ServerConnection connection;
    private HelidonSocket helidonSocket;
    private DataReader reader;
    private SocketWriter writer;
    private ProxyProtocolData proxyProtocolData;

    ConnectionHandler(ListenerContext listenerContext,
                      Semaphore connectionSemaphore,
                      Semaphore requestSemaphore,
                      ConnectionProviders connectionProviders,
                      Map<String, ServerConnection> activeConnections,
                      Socket socket,
                      String serverChannelId,
                      Router router,
                      Tls tls) {
        this.listenerContext = listenerContext;
        this.connectionSemaphore = connectionSemaphore;
        this.requestSemaphore = requestSemaphore;
        this.connectionProviders = connectionProviders;
        this.providerCandidates = connectionProviders.providerCandidates();
        this.activeConnections = activeConnections;
        this.socket = socket;
        this.serverChannelId = serverChannelId;
        this.router = router;
        this.tls = tls;
        this.listenerConfig = listenerContext.config();
    }

    @Override
    public boolean canInterrupt() {
       return connection instanceof InterruptableTask<?> task && task.canInterrupt();
    }

    @Override
    public final void run() {
        String channelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(socket));

        // proxy protocol before SSL handshake
        if (listenerConfig.enableProxyProtocol()) {
            ProxyProtocolHandler handler = new ProxyProtocolHandler(socket, channelId);
            proxyProtocolData = handler.get();
        }

        // handle SSL and init helidonSocket, reader and writer
        try {
            if (tls.enabled()) {
                SSLSocket sslSocket = (SSLSocket) socket;
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
                helidonSocket = TlsSocket.server(sslSocket, channelId, serverChannelId);
            } else {
                helidonSocket = PlainSocket.server(socket, channelId, serverChannelId);
            }

            reader = new DataReader(helidonSocket);
            writer = SocketWriter.create(listenerContext.executor(), helidonSocket,
                    listenerContext.config().writeQueueLength());
        } catch (Exception e) {
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);      // see ServerListener
        }

        // connection handling
        String socketsId = helidonSocket.socketId() + " " + helidonSocket.childSocketId();
        Thread.currentThread().setName("[" + socketsId + "] WebServer socket");
        if (LOGGER.isLoggable(DEBUG)) {
            helidonSocket.log(LOGGER,
                       DEBUG,
                       "accepted socket from %s:%d",
                       helidonSocket.remotePeer().host(),
                       helidonSocket.remotePeer().port());
        }

        try {
            if (helidonSocket.protocolNegotiated()) {
                this.connection = connectionProviders.byApplicationProtocol(helidonSocket.protocol())
                        .connection(this);
            }

            if (connection == null) {
                this.connection = identifyConnection();
            }

            if (connection == null) {
                throw new CloseConnectionException("No suitable connection provider");
            }
            activeConnections.put(socketsId, connection);
            connection.handle(requestSemaphore);
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
        } catch (Exception e) {
            helidonSocket.log(LOGGER, WARNING, "unexpected exception", e);
        } finally {
            // connection has finished the loop of handling, release the semaphore
            connectionSemaphore.release();
            activeConnections.remove(socketsId);
            writer.close();
            closeChannel();
        }

        helidonSocket.log(LOGGER, DEBUG, "socket closed");
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
                                + "initial connection buffer (may be empty if no providers exist):\n%s",
                        currentBuffer.debugDataHex(false));
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
                                + "initial connection buffer (may be empty if no providers exist):\n%s",
                        currentBuffer.debugDataHex(true));

                return null;
            }

            // read next data (need to create a copy, to make sure we do not re-use the same buffer)
            currentBuffer = reader.getBuffer(reader.available() + 1); // for additional read
        }
    }

    private void closeChannel() {
        try {
            helidonSocket.close();
        } catch (Throwable e) {
            helidonSocket.log(LOGGER, TRACE, "Failed to close socket on connection close", e);
        }
    }
}
