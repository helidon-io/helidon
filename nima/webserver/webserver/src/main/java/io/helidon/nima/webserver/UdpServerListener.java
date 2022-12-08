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

package io.helidon.nima.webserver;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.udp.UdpClient;
import io.helidon.nima.udp.UdpEndpoint;
import io.helidon.nima.udp.UdpMessage;

import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.TRACE;

class UdpServerListener implements ConnectionListener {
    private static final System.Logger LOGGER = System.getLogger(UdpServerListener.class.getName());

    private static final int READ_BUFFER_SIZE = 16 * 1024;
    private static final long EXECUTOR_SHUTDOWN_MILLIS = 500L;

    private final String socketName;
    private final ExecutorService handlerExecutor;
    private final Thread serverThread;
    private final CompletableFuture<Void> closeFuture;
    private final InetSocketAddress configuredAddress;

    private final MediaContext mediaContext;
    private final ContentEncodingContext contentEncodingContext;
    private final LoomServer server;
    private final UdpEndpoint endpoint;
    private final ByteBuffer readBuffer;

    private volatile boolean running;
    private volatile int localPort;
    private volatile DatagramChannel channel;

    UdpServerListener(LoomServer loomServer,
                   String socketName,
                   ListenerConfiguration listenerConfig,
                   MediaContext mediaContext,
                   ContentEncodingContext contentEncodingContext) {
        this.server = loomServer;
        this.socketName = socketName;
        this.endpoint = listenerConfig.udpEndpoint();
        this.readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);

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
        this.mediaContext = mediaContext;
        this.contentEncodingContext = contentEncodingContext;
    }

    @Override
    public String toString() {
        return "UDP[" + socketName + " (" + configuredAddress + ")]";
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
                Message message = new Message(remote, readBuffer);
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

    private class Message implements UdpMessage {

        private final InetSocketAddress remote;
        private final byte[] bytes;

        Message(InetSocketAddress remote, ByteBuffer readBuffer) {
            this.remote = remote;
            readBuffer.flip();
            bytes = new byte[readBuffer.remaining()];
            readBuffer.get(bytes);
        }

        @Override
        public UdpClient udpClient() {
            return new UdpClient() {
                @Override
                public InetAddress inetAddress() {
                    return remote.getAddress();
                }

                @Override
                public int port() {
                    return remote.getPort();
                }

                @Override
                public void sendMessage(Object msg) throws IOException {
                    throw new UnsupportedOperationException("Not supported");
                }

                @Override
                public void sendMessage(byte[] msg) throws IOException {
                    channel.send(ByteBuffer.wrap(msg), remote);
                }

                @Override
                public void sendMessage(InputStream msg) throws IOException {
                    throw new UnsupportedOperationException("Not supported");
                }
            };
        }

        @Override
        public <T> T as(Class<T> clazz) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public InputStream asInputStream() {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public byte[] asByteArray() {
            return bytes;
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
}
