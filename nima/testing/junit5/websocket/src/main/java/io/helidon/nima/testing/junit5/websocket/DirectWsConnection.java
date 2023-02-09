/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.testing.junit5.websocket;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.nima.testing.junit5.webserver.DirectPeerInfo;
import io.helidon.nima.testing.junit5.webserver.DirectSocket;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.Router;
import io.helidon.nima.websocket.WsListener;
import io.helidon.nima.websocket.client.ClientWsConnection;
import io.helidon.nima.websocket.webserver.WsConnection;
import io.helidon.nima.websocket.webserver.WsRoute;

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
        return new DataReader(() -> {
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
            ClientWsConnection clientConnection = ClientWsConnection.create(clientListener,
                                                                            socket,
                                                                            clientReader,
                                                                            clientWriter,
                                                                            Optional.empty());
            serverFuture = executorService.submit(serverConnection::handle);
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
}
