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

import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.http.HttpException;
import io.helidon.common.http.RequestException;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.SocketWriter;
import io.helidon.nima.webserver.http.DirectHandlers;
import io.helidon.nima.webserver.spi.ServerConnection;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

/**
 * Representation of a single channel between client and server.
 * Everything in this class runs in the channel reader virtual thread
 */
class ConnectionHandler implements Runnable {
    private static final System.Logger LOGGER = System.getLogger(ConnectionHandler.class.getName());

    private final ConnectionProviders connectionProviders;
    private final List<ServerConnectionSelector> providerCandidates;
    private final String serverChannelId;
    private final HelidonSocket socket;
    private final String channelId;
    private final SocketWriter writer;
    private final DataReader reader;
    private final ConnectionContext ctx;

    private ServerConnection connection;

    ConnectionHandler(ServerContext serverContext,
                      ConnectionProviders connectionProviders,
                      ExecutorService sharedExecutor,
                      String serverChannelId,
                      String channelId,
                      HelidonSocket socket,
                      Router router,
                      int writeQueueLength,
                      long maxPayloadSize,
                      DirectHandlers simpleHandlers) {
        this.connectionProviders = connectionProviders;
        this.providerCandidates = connectionProviders.providerCandidates();
        this.serverChannelId = serverChannelId;
        this.socket = socket;
        this.channelId = channelId;
        this.writer = SocketWriter.create(sharedExecutor, socket, writeQueueLength);
        this.reader = new DataReader(socket);
        this.ctx = ConnectionContext.create(serverContext,
                                            sharedExecutor,
                                            writer,
                                            reader,
                                            router,
                                            serverChannelId,
                                            channelId,
                                            simpleHandlers,
                                            socket,
                                            maxPayloadSize);
    }

    @Override
    public final void run() {
        Thread.currentThread().setName("[" + socket.socketId() + " " + socket.childSocketId() + "] Nima socket");
        if (LOGGER.isLoggable(DEBUG)) {
            ctx.log(LOGGER, DEBUG, "accepted socket from %s", socket.remotePeer().host());
        }

        try {
            if (socket.protocolNegotiated()) {
                this.connection = connectionProviders.byApplicationProtocol(socket.protocol())
                        .connection(ctx);
            }

            if (connection == null) {
                this.connection = identifyConnection();
            }

            if (connection == null) {
                throw new CloseConnectionException("No suitable connection provider");
            }

            // removing structured concurrency for now - we should use this when we start more threads for a single
            // request, which is not the case now for HTTP/1
            //            try (var executor = StructuredTaskScope.open(serverChannelId + " " + channelId,
            //                                                         Thread.ofVirtual().factory(), (scope, future) -> {})) {
            //                try {
            //                    connection.handle();
            //                } finally {
            //                    writer.close();
            //                    executor.join();
            //                }
            //            }
            connection.handle();
        } catch (RequestException e) {
            ctx.log(LOGGER, WARNING, "escaped Request exception", e);
        } catch (HttpException e) {
            ctx.log(LOGGER, WARNING, "escaped HTTP exception", e);
        } catch (CloseConnectionException e) {
            // end of request stream - safe to close the connection, as it was requested by our client
            ctx.log(LOGGER, TRACE, "connection close requested", e);
        } catch (UncheckedIOException e) {
            // socket exception - the socket failed, probably killed by OS, proxy or client
            ctx.log(LOGGER, TRACE, "received I/O exception", e);
        } catch (Exception e) {
            ctx.log(LOGGER, WARNING, "unexpected exception", e);
        } finally {
            writer.close();
            closeChannel();
        }

        ctx.log(LOGGER, DEBUG, "socket closed");
    }

    private ServerConnection identifyConnection() {
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
                ctx.log(LOGGER, DEBUG, "Could not find a suitable connection provider. "
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
                    return candidate.connection(ctx);
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
                ctx.log(LOGGER,
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
            socket.close();
        } catch (Throwable e) {
            ctx.log(LOGGER, TRACE, "Failed to close socket on connection close", e);
        }
    }
}
