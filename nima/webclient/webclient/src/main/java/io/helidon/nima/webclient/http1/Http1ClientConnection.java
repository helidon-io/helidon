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

package io.helidon.nima.webclient.http1;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.SocketOptions;
import io.helidon.nima.webclient.ClientConnection;
import io.helidon.nima.webclient.ConnectionStrategy;
import io.helidon.nima.webclient.ConnectionStrategy.ConnectionValues;

import static java.lang.System.Logger.Level.DEBUG;

class Http1ClientConnection implements ClientConnection {
    private static final System.Logger LOGGER = System.getLogger(Http1ClientConnection.class.getName());
    private static final long QUEUE_TIMEOUT = 10;
    private static final TimeUnit QUEUE_TIMEOUT_TIME_UNIT = TimeUnit.MILLISECONDS;

    private final LinkedBlockingDeque<Http1ClientConnection> connectionQueue;
    private final ConnectionKey connectionKey;
    private final io.helidon.common.socket.SocketOptions options;
    private final boolean keepAlive;
    private String channelId;
    private Socket socket;
    private HelidonSocket helidonSocket;
    private DataReader reader;
    private DataWriter writer;

    Http1ClientConnection(SocketOptions options, ConnectionKey connectionKey) {
        this(options, null, connectionKey);
    }

    Http1ClientConnection(SocketOptions options,
                          LinkedBlockingDeque<Http1ClientConnection> connectionQueue,
                          ConnectionKey connectionKey) {
        this.options = options;
        this.connectionQueue = connectionQueue;
        this.keepAlive = (connectionQueue != null);
        this.connectionKey = connectionKey;
    }

    @Override
    public DataReader reader() {
        return reader;
    }

    @Override
    public DataWriter writer() {
        return writer;
    }

    @Override
    public void release() {
        finishRequest();
    }

    @Override
    public void close() {
        try {
            this.socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String channelId() {
        return channelId;
    }

    @Override
    public Socket socket() {
        return socket;
    }

    @Override
    public void readTimeout(Duration readTimeout) {
        if (!isConnected()) {
            throw new IllegalStateException("Read timeout cannot be set, because connection has not been established.");
        }
        try {
            socket.setSoTimeout((int) readTimeout.toMillis());
        } catch (SocketException e) {
            throw new UncheckedIOException("Could not set read timeout to the connection with the channel id: " + channelId, e);
        }
    }

    boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    Http1ClientConnection connect() {
        ConnectionValues connection = ConnectionStrategy.connect(connectionKey.host(), connectionKey.port(),
                connectionKey.proxy(), connectionKey.tls(), options, connectionKey.dnsResolver(),
                connectionKey.dnsAddressLookup(), keepAlive);
        this.socket = connection.socket();
        this.channelId = connection.channelId();
        this.helidonSocket = connection.helidonSocket();

        this.reader = new DataReader(helidonSocket);
        this.writer = new DataWriter() {
            @Override
            public void write(BufferData... buffers) {
                writeNow(buffers);
            }

            @Override
            public void write(BufferData buffer) {
                writeNow(buffer);
            }

            @Override
            public void writeNow(BufferData... buffers) {
                for (BufferData buffer : buffers) {
                    writeNow(buffer);
                }
            }

            @Override
            public void writeNow(BufferData buffer) {
                helidonSocket.write(buffer);
            }
        };

        return this;
    }

    void finishRequest() {
        if (keepAlive && connectionQueue != null && socket.isConnected()) {
            try {
                if (connectionQueue.offer(this, QUEUE_TIMEOUT, QUEUE_TIMEOUT_TIME_UNIT)) {
                    LOGGER.log(DEBUG, () -> String.format("[%s] client connection returned %s",
                                                          channelId,
                                                          Thread.currentThread().getName()));
                    return;
                } else {
                    LOGGER.log(DEBUG, () -> String.format("[%s] Unable to return client connection because queue is full %s",
                                                          channelId,
                                                          Thread.currentThread().getName()));
                }
            } catch (InterruptedException ie) {
                LOGGER.log(DEBUG, () -> String.format("[%s] Unable to return client connection due to '%s' %s",
                                                    channelId,
                                                    ie.getMessage(),
                                                    Thread.currentThread().getName()));
            }
        }
        // Close if unable to add to queue
        close();
    }
}
