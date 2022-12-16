/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.testing.junit5.webserver;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.context.Context;
import io.helidon.common.socket.PeerInfo;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webclient.ClientConnection;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.Router;
import io.helidon.nima.webserver.ServerContext;
import io.helidon.nima.webserver.http.DirectHandlers;
import io.helidon.nima.webserver.http1.Http1ConnectionProvider;
import io.helidon.nima.webserver.spi.ServerConnection;

class DirectClientConnection implements ClientConnection {
    private final AtomicBoolean serverStarted = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private final Router router;
    private final DataReader clientReader;
    private final DataWriter clientWriter;
    private final DataReader serverReader;
    private final DataWriter serverWriter;
    private final DirectSocket socket;

    DirectClientConnection(PeerInfo clientPeer,
                           PeerInfo localPeer,
                           Router router,
                           boolean isTls) {

        this.router = router;
        this.socket = new DirectSocket(localPeer, clientPeer, isTls);

        ArrayBlockingQueue<byte[]> serverToClient = new ArrayBlockingQueue<>(1024);
        ArrayBlockingQueue<byte[]> clientToServer = new ArrayBlockingQueue<>(1024);
        this.clientReader = reader(serverToClient);
        this.clientWriter = writer(clientToServer);
        this.serverReader = reader(clientToServer);
        this.serverWriter = writer(serverToClient);
    }

    @Override
    public DataReader reader() {
        return clientReader;
    }

    @Override
    public DataWriter writer() {
        return clientWriter;
    }

    @Override
    public void release() {
        close();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            clientWriter.writeNow(BufferData.empty());
        }
    }

    @Override
    public String channelId() {
        return "unit-client";
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
                if (serverStarted.compareAndSet(false, true)) {
                    startServer();
                }
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

    private DataReader reader(ArrayBlockingQueue<byte[]> queue) {
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

    private void startServer() {
        ExecutorService executorService = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("direct-test-server", 1)
                                                                                     .factory());
        ConnectionContext ctx = ConnectionContext.create(
                ServerContext.create(Context.create(),
                                     MediaContext.create(),
                                     ContentEncodingContext.create()),
                executorService,
                serverWriter,
                serverReader,
                router,
                "unit-server",
                "unit-channel",
                DirectHandlers.builder().build(),
                socket,
                -1);

        ServerConnection connection = Http1ConnectionProvider.builder()
                .build()
                .connection(ctx);
        executorService.submit(() -> {
            try {
                connection.handle();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }
}
