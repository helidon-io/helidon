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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.helidon.common.GenericType;
import io.helidon.common.LazyValue;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.http.media.EntityReader;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.udp.UdpClient;
import io.helidon.nima.udp.UdpEndpoint;
import io.helidon.nima.udp.UdpMessage;

import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.TRACE;

class UdpServerListener extends ServerListener {
    private static final System.Logger LOGGER = System.getLogger(UdpServerListener.class.getName());

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long EXECUTOR_SHUTDOWN_MILLIS = 500L;
    private static final WritableHeaders<?> EMPTY_HEADERS = WritableHeaders.create();

    private final ExecutorService handlerExecutor;
    private final Thread serverThread;
    private final CompletableFuture<Void> closeFuture;
    private final InetSocketAddress configuredAddress;

    private final MediaContext mediaContext;
    private final LoomServer server;
    private final UdpEndpoint endpoint;
    private final ByteBuffer readBuffer;

    private volatile boolean running;
    private volatile int localPort;
    private volatile DatagramChannel channel;

    UdpServerListener(LoomServer loomServer,
                      String socketName,
                      ListenerConfiguration listenerConfig,
                      boolean inheritThreadLocals) {
        super(Collections.emptyList(), socketName, listenerConfig, Router.empty(), inheritThreadLocals);

        this.server = loomServer;
        this.endpoint = listenerConfig.udpEndpoint();
        this.readBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        this.serverThread = Thread.ofPlatform()
                .allowSetThreadLocals(true)
                .inheritInheritableThreadLocals(true)
                .daemon(false)
                .name("udp-server-" + socketName + "-listener")
                .unstarted(this::listen);
        this.handlerExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                .allowSetThreadLocals(true)
                .inheritInheritableThreadLocals(false)
                .factory());

        this.closeFuture = new CompletableFuture<>();

        int port = listenerConfig.port();
        if (port < 1) {
            port = 0;
        }
        this.configuredAddress = new InetSocketAddress(listenerConfig.address(), port);
        this.mediaContext = listenerConfig.mediaContext();
    }

    @Override
    public String toString() {
        return "UDP[" + socketName() + " (" + configuredAddress + ")]";
    }

    @Override
    public int port() {
        return localPort;
    }

    @Override
    public InetSocketAddress configuredAddress() {
        return configuredAddress;
    }

    @Override
    public void start() {
        try {
            channel = DatagramChannel.open();
            channel.bind(configuredAddress);
            localPort = channel.socket().getLocalPort();

            if (LOGGER.isLoggable(INFO)) {
                String format = "[%s] udp://%s:%s bound for socket '%s'";
                String serverChannelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(channel));
                LOGGER.log(INFO, String.format(format,
                        serverChannelId,
                        configuredAddress.getAddress().getHostAddress(),
                        localPort,
                        socketName()));
            }
        } catch (IOException e) {
            LOGGER.log(TRACE, "Failed to open socket channel", e);
            throw new RuntimeException(e);
        }
        running = true;
        serverThread.start();
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        channel = null;
        shutdownExecutor(handlerExecutor);
        serverThread.interrupt();
        closeFuture.join();
    }

    private void listen() {
        try {
            while (running) {
                readBuffer.clear();
                InetSocketAddress remote = (InetSocketAddress) channel.receive(readBuffer);
                readBuffer.flip();
                Message message = new Message(remote, clone(readBuffer));
                handlerExecutor.submit(() -> {
                    try {
                        endpoint.onMessage(message);
                    } catch (Exception e) {
                        LOGGER.log(TRACE, "Failed on call to onMessage", e);
                        endpoint.onError(e);
                    }
                });
            }
        } catch (ClosedByInterruptException e) {
            // falls through -- thread interrupted by stop()
        } catch (IOException e) {
            LOGGER.log(TRACE, "Failed to open socket channel", e);
            handlerExecutor.submit(() -> endpoint.onError(e));
            stop();
        }
        closeFuture.complete(null);
    }

    /**
     * Implementation of UdpMessage interface.
     */
    private class Message implements UdpMessage {

        private static final LazyValue<Map<Class<?>, EntityReader<?>>> READERS =
                LazyValue.create(ConcurrentHashMap::new);

        private final ByteBuffer readBuffer;
        private final Client client;

        Message(InetSocketAddress remote, ByteBuffer readBuffer) {
            this.client = new Client(remote);
            this.readBuffer = readBuffer;
        }

        @Override
        public UdpClient udpClient() {
            return client;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as(Class<T> clazz) {
            if (clazz.equals(ByteBuffer.class)) {
                return (T) readBuffer;
            } else if (clazz.equals(String.class)) {
                return (T) new String(asByteArray(), StandardCharsets.UTF_8);
            } else if (clazz.equals(byte[].class)) {
                return (T) asByteArray();
            } else if (clazz.equals(InputStream.class)) {
                return (T) asInputStream();
            } else {
                GenericType<T> type = GenericType.create(clazz);
                EntityReader<T> reader = (EntityReader<T>) READERS.get().computeIfAbsent(clazz,
                        c -> mediaContext.reader(type, EMPTY_HEADERS));
                return reader.read(type, asInputStream(), EMPTY_HEADERS);
            }
        }

        @Override
        public InputStream asInputStream() {
            return new ByteArrayInputStream(asByteArray());
        }

        @Override
        public byte[] asByteArray() {
            byte[] bytes = new byte[readBuffer.remaining()];
            readBuffer.get(bytes);
            return bytes;
        }
    }

    /**
     * Implementation of UdpClient interface.
     */
    private class Client implements UdpClient {

        private static final LazyValue<Map<Class<?>, EntityWriter<?>>> WRITERS =
                LazyValue.create(ConcurrentHashMap::new);

        private ByteBuffer writeBuffer;
        private final InetSocketAddress remote;

        Client(InetSocketAddress remote) {
            this.remote = remote;
        }

        @Override
        public InetAddress inetAddress() {
            return remote.getAddress();
        }

        @Override
        public int port() {
            return remote.getPort();
        }

        @Override
        public boolean isConnected() {
            return channel.isConnected();
        }

        @Override
        public void connect() throws IOException {
            channel.connect(remote);
        }

        @Override
        public void disconnect() throws IOException {
            channel.disconnect();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Client client)) {
                return false;
            }
            return remote.equals(client.remote);
        }

        @Override
        public int hashCode() {
            return Objects.hash(remote);
        }

        private ByteBuffer writeBuffer() {
            if (writeBuffer == null) {
                writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);
                return writeBuffer;
            }
            return writeBuffer.clear();
        }

        @Override
        @SuppressWarnings("unchecked")
        public void sendMessage(Object msg) throws IOException {
            if (msg instanceof ByteBuffer buffer) {
                sendMessage(buffer);
            } else if (msg instanceof byte[] bytes) {
                sendMessage(bytes);
            } else if (msg instanceof String str) {
                sendMessage(str.getBytes(StandardCharsets.UTF_8));
            } else if (msg instanceof InputStream is) {
                sendMessage(is);
            } else {
                Class<?> clazz = msg.getClass();
                GenericType<Object> type = (GenericType<Object>) GenericType.create(clazz);
                EntityWriter<Object> writer = (EntityWriter<Object>) WRITERS.get().computeIfAbsent(clazz,
                        c -> mediaContext.writer(type, EMPTY_HEADERS));
                ByteBuffer buffer = writeBuffer();
                writer.write(type, msg, new OutputStream() {
                    @Override
                    public void write(int b) {
                        buffer.put((byte) b);
                    }
                }, EMPTY_HEADERS);
                buffer.flip();
                sendMessage(buffer);
            }
        }

        @Override
        public void sendMessage(byte[] bytes) throws IOException {
            channel.send(ByteBuffer.wrap(bytes), remote);
        }

        @Override
        public void sendMessage(InputStream is) throws IOException {
            channel.send(ByteBuffer.wrap(is.readAllBytes()), remote);
        }

        @Override
        public void sendMessage(ByteBuffer buffer) throws IOException {
            channel.send(buffer, remote);
        }
    }

    /**
     * Shutdown an executor by waiting for a period of time.
     *
     * @param executor executor to shut down
     */
    static void shutdownExecutor(ExecutorService executor) {
        try {
            boolean terminate = executor.awaitTermination(EXECUTOR_SHUTDOWN_MILLIS, TimeUnit.MILLISECONDS);
            if (!terminate) {
                List<Runnable> running = executor.shutdownNow();
                if (!running.isEmpty()) {
                    LOGGER.log(INFO, running.size() + " channel tasks did not terminate gracefully");
                }
            }
        } catch (InterruptedException e) {
            LOGGER.log(INFO, "InterruptedException caught while shutting down channel tasks");
        }
    }

    /**
     * Allocates a new byte buffer of exact size.
     *
     * @param buffer original buffer to copy
     * @return new byte buffer
     */
    static ByteBuffer clone(ByteBuffer buffer) {
        ByteBuffer clone = ByteBuffer.allocate(buffer.remaining());
        clone.put(buffer);
        clone.flip();
        return clone;
    }
}
