/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.http1;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.WebClient;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http1ExchangeLifecycleTest {
    @Test
    void concurrentResponseClosersInvokeDelegateOnlyOnce() {
        var firstCloseEntered = new CountDownLatch(1);
        var allowClose = new CountDownLatch(1);
        var connection = new Connection("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: 0\r\n\r\n",
                                        () -> {
                                            firstCloseEntered.countDown();
                                            await(allowClose);
                                        });
        client(connection).post("http://localhost/test").exchange(output -> output.close(), response -> {
            ClientConnection exposedConnection = ((Http1ClientResponseImpl) response).connection();
            var secondClosed = new CompletableFuture<Void>();
            Thread firstCloser = Thread.ofVirtual().start(exposedConnection::closeResource);
            Thread secondCloser = null;
            try {
                await(firstCloseEntered);
                secondCloser = Thread.ofVirtual().start(() -> {
                    try {
                        exposedConnection.closeResource();
                        secondClosed.complete(null);
                    } catch (Throwable e) {
                        secondClosed.completeExceptionally(e);
                    }
                });
                try {
                    secondClosed.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IOException("Concurrent close did not finish while the first delegate close was pending", e);
                }
                response.close();
                assertThat(connection.closes.get(), is(1));
                assertThat(connection.releases.get(), is(0));
            } finally {
                allowClose.countDown();
                join(firstCloser);
                if (secondCloser != null) {
                    join(secondCloser);
                }
            }
        });
        assertThat(connection.closes.get(), is(1));
    }

    @Test
    void inheritedReadFailureCleanupDoesNotCloseCachedDelegateAgain() {
        var connection = new Connection(() -> {
            throw new UncheckedIOException(new IOException("Response read failed"));
        }, () -> { });
        var client = client(connection);
        // The cache supplies the transport; an explicitly injected request connection would skip inherited cleanup.
        assertThrows(UncheckedIOException.class,
                     () -> client.post("http://localhost/test").exchange(output -> output.close(), response -> response.close()));
        assertThat(connection.closes.get(), is(1));
        assertThat(connection.releases.get(), is(0));
    }

    @Test
    void uploadUsesWriterAcquiredOnCallingThreadAndSuccessReleasesOnce() {
        var connection = new Connection("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n", () -> { });
        connection.enforceWriterOwner = true;
        client(connection).post("http://localhost/test").exchange(output -> {
            try (output) {
                output.write(new byte[16_384]);
            }
        }, response -> response.close());
        assertThat(connection.writerAcquisitions.get(), is(1));
        assertThat(connection.closes.get(), is(0));
        assertThat(connection.releases.get(), is(1));
    }

    private static Http1Client client(Connection connection) {
        var config = Http1ClientConfig.builder().sendExpectContinue(false).buildPrototype();
        var cache = new Http1ConnectionCache(false) {
            @Override
            ClientConnection connection(Http1ClientImpl client,
                                        ClientConnectionTarget target,
                                        ClientRequestHeaders headers,
                                        boolean keepAlive) {
                return connection;
            }
        };
        return new Http1ClientImpl(WebClient.create(), config) {
            @Override
            Http1ConnectionCache connectionCache() {
                return cache;
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat("Lifecycle coordination completed", latch.await(5, TimeUnit.SECONDS), is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Lifecycle coordination interrupted", e);
        }
    }

    private static void join(Thread thread) {
        try {
            assertThat("Close worker terminated", thread.join(Duration.ofSeconds(5)), is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted joining close worker", e);
        }
    }

    private static final class Connection implements ClientConnection {
        private final Thread owner = Thread.currentThread();
        private final AtomicInteger closes = new AtomicInteger();
        private final AtomicInteger releases = new AtomicInteger();
        private final AtomicInteger writerAcquisitions = new AtomicInteger();
        private final DataReader reader;
        private final Runnable onClose;
        private final HelidonSocket socket = new Socket();
        private final DataWriter writer = new DataWriter() {
            @Override
            public void write(BufferData... buffers) {
                for (BufferData buffer : buffers) {
                    write(buffer);
                }
            }

            @Override
            public void write(BufferData buffer) {
                buffer.skip(buffer.available());
            }

            @Override
            public void writeNow(BufferData... buffers) {
                write(buffers);
            }

            @Override
            public void writeNow(BufferData buffer) {
                write(buffer);
            }
        };
        private boolean enforceWriterOwner;

        private Connection(String response, Runnable onClose) {
            var supplied = new AtomicBoolean();
            reader = DataReader.create(() -> supplied.compareAndSet(false, true)
                    ? response.getBytes(StandardCharsets.US_ASCII) : new byte[0]);
            this.onClose = onClose;
        }

        private Connection(Supplier<byte[]> response, Runnable onClose) {
            reader = DataReader.create(response);
            this.onClose = onClose;
        }

        @Override
        public DataReader reader() {
            return reader;
        }

        @Override
        public DataWriter writer() {
            if (enforceWriterOwner && Thread.currentThread() != owner) {
                throw new IllegalStateException("Delegate writer accessor called outside its owner thread");
            }
            writerAcquisitions.incrementAndGet();
            return writer;
        }

        @Override
        public String channelId() {
            return "exchange-lifecycle-test";
        }

        @Override
        public HelidonSocket helidonSocket() {
            return socket;
        }

        @Override
        public void readTimeout(Duration timeout) {
        }

        @Override
        public void releaseResource() {
            releases.incrementAndGet();
        }

        @Override
        public void closeResource() {
            closes.incrementAndGet();
            onClose.run();
        }
    }

    private static final class Socket implements HelidonSocket {
        @Override
        public void close() {
        }

        @Override
        public void idle() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void write(BufferData buffer) {
        }

        @Override
        public PeerInfo remotePeer() {
            return null;
        }

        @Override
        public PeerInfo localPeer() {
            return null;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String socketId() {
            return "exchange-lifecycle-test";
        }

        @Override
        public String childSocketId() {
            return "exchange-lifecycle-test";
        }

        @Override
        public byte[] get() {
            return new byte[0];
        }
    }
}
