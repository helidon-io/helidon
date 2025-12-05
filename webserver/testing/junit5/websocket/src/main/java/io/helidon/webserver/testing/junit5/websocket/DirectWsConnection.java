/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5.websocket;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.HttpPrologue;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.websocket.ClientWsConnection;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.testing.junit5.DirectPeerInfo;
import io.helidon.webserver.testing.junit5.DirectSocket;
import io.helidon.webserver.websocket.WsConnection;
import io.helidon.webserver.websocket.WsRoute;
import io.helidon.websocket.WsListener;

class DirectWsConnection {
    private final AtomicBoolean serverStarted = new AtomicBoolean();

    private final HttpPrologue prologue;
    private final WsListener clientListener;
    private final WsRoute serverRoute;
    private final DataReader clientReader;
    private final DataWriter clientWriter;
    private final DataReader serverReader;
    private final DataWriter serverWriter;
    private final HelidonSocket socket;
    private final ConnectionContext ctx;
    private final ExecutorService executorService;
    private volatile Future<?> serverFuture;
    private volatile Future<?> clientFuture;

    DirectWsConnection(HttpPrologue prologue, WsListener clientListener, WsRoute serverRoute) {
        this.prologue = prologue;
        this.clientListener = clientListener;
        this.serverRoute = serverRoute;
        this.executorService = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("direct-test-ws", 1)
                                                                          .factory());

        ArrayBlockingQueue<byte[]> serverToClient = new ArrayBlockingQueue<>(1024);
        ArrayBlockingQueue<byte[]> clientToServer = new ArrayBlockingQueue<>(1024);
        this.clientReader = reader(serverToClient);
        this.clientWriter = writer(clientToServer);
        this.serverReader = reader(clientToServer);
        this.serverWriter = writer(serverToClient);
        DirectPeerInfo info = new DirectPeerInfo(InetSocketAddress.createUnresolved("localhost", 64000),
                                                 "localhost",
                                                 64000,
                                                 Optional.empty(),
                                                 Optional.empty());
        this.socket = DirectSocket.create(info, info, false);
        this.ctx = new DirectWsServerContext(executorService,
                                             Router.builder().build(),
                                             socket,
                                             serverWriter,
                                             serverReader);
    }

    static DirectWsConnection create(HttpPrologue prologue, WsListener clientListener, WsRoute serverRoute) {
        return new DirectWsConnection(prologue, clientListener, serverRoute);
    }

    private static DataReader reader(ArrayBlockingQueue<byte[]> queue) {
        return DataReader.create(() -> {
            byte[] data;
            try {
                data = queue.take();
            } catch (InterruptedException e) {
                throw new IllegalArgumentException("Thread interrupted", e);
            }
            if (data.length == 0) {
                return null;
            }
            return data;
        });
    }

    void start() {
        if (serverStarted.compareAndSet(false, true)) {
            WsConnection serverConnection = WsConnection.create(ctx, prologue, WritableHeaders.create(), "", serverRoute);

            ClientWsConnection clientConnection = ClientWsConnection.create(new DirectConnect(clientReader, clientWriter),
                                                                            clientListener);
            serverFuture = executorService.submit(() -> {
                serverConnection.handle(new Semaphore(1024));
            });
            clientFuture = executorService.submit(clientConnection);
        }
    }

    void stop() {
        Future<?> s = serverFuture;
        Future<?> c = clientFuture;
        if (s != null) {
            s.cancel(true);
        }
        if (c != null) {
            c.cancel(true);
        }
    }

    private DataWriter writer(ArrayBlockingQueue<byte[]> queue) {
        return new DataWriter() {
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
                byte[] bytes = new byte[buffer.available()];
                buffer.read(bytes);
                try {
                    queue.put(bytes);
                } catch (InterruptedException e) {
                    throw new IllegalStateException("Thread interrupted", e);
                }
            }
        };
    }

    private static class DirectConnect implements ClientConnection {
        private final DataReader reader;
        private final DataWriter writer;
        private final PeerInfo clientPeer;
        private final PeerInfo localPeer;
        private final HelidonSocket socket;

        private DirectConnect(DataReader reader, DataWriter writer) {
            this.reader = reader;
            this.writer = writer;

            String clientHost = "localhost";
            int clientPort = 9999;
            String serverHost = "server";
            int serverPort = 9999;

            this.clientPeer = new DirectPeerInfo(
                    InetSocketAddress.createUnresolved(clientHost, clientPort),
                    clientHost,
                    clientPort,
                    Optional.empty(),
                    Optional.empty());

            this.localPeer = new DirectPeerInfo(
                    InetSocketAddress.createUnresolved(serverHost, serverPort),
                    serverHost,
                    serverPort,
                    Optional.empty(),
                    Optional.empty());

            this.socket = DirectSocket.create(localPeer, clientPeer, false);
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
        public String channelId() {
            return "direct-ws-connection";
        }

        @Override
        public HelidonSocket helidonSocket() {
            return socket;
        }

        @Override
        public void readTimeout(Duration readTimeout) {
        }

        @Override
        public void closeResource() {
        }
    }
}
