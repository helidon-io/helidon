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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.WebClientServiceRequest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http1ExchangeTest {
    @Test
    void serviceWhenSentCompletesOnlyAfterEntityFraming() {
        var connection = new Connection("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
        var allowClose = new CountDownLatch(1);
        var entityWritten = new CountDownLatch(1);
        var sent = new AtomicReference<CompletableFuture<WebClientServiceRequest>>();
        var client = Http1Client.builder().sendExpectContinue(false).addService((chain, request) -> {
            sent.set(request.whenSent().toCompletableFuture());
            return chain.proceed(request);
        }).build();
        client.post("http://localhost/test").connection(connection).exchange(output -> {
            output.write(42);
            entityWritten.countDown();
            await(allowClose);
            output.close();
        }, response -> {
            await(entityWritten);
            assertThat(sent.get().isDone(), is(false));
            allowClose.countDown();
        });
        assertThat(sent.get().isDone(), is(true));
        assertThat(sent.get().isCompletedExceptionally(), is(false));
    }

    @Test
    void serviceWhenSentFailsWhenServerRejectsEntity() {
        var connection = new Connection("HTTP/1.1 413 Content Too Large\r\nContent-Length: 0\r\n\r\n");
        var sent = new AtomicReference<CompletableFuture<WebClientServiceRequest>>();
        var client = Http1Client.builder().sendExpectContinue(true).addService((chain, request) -> {
            sent.set(request.whenSent().toCompletableFuture());
            return chain.proceed(request);
        }).build();
        client.post("http://localhost/test").connection(connection).exchange(output -> output.close(),
                                                                          response -> assertThat(response.status().code(), is(413)));
        assertThat(sent.get().isCompletedExceptionally(), is(true));
    }

    @Test
    void validatesStreamingRequestBeforeTransport() {
        var client = Http1Client.builder().sendExpectContinue(false).build();
        assertThrows(IllegalArgumentException.class,
                     () -> client.method(Method.HEAD).uri("http://localhost/test").exchange(output -> output.close(),
                                                                                          response -> response.close()));
        assertThrows(IllegalArgumentException.class,
                     () -> client.method(Method.QUERY).uri("http://localhost/test").exchange(output -> output.close(),
                                                                                           response -> response.close()));
    }

    @Test
    void successfulResponseDoesNotReleaseAnOngoingUpload() {
        var connection = new Connection("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
        var uploadStarted = new CountDownLatch(1);
        var responseConsumed = new CountDownLatch(1);
        var uploadFinished = new AtomicBoolean();
        var context = Context.create();
        Thread caller = Thread.currentThread();
        Contexts.runInContext(context, () -> request(connection).exchange(output -> {
            assertThat(Thread.currentThread().isVirtual(), is(true));
            assertThat(Contexts.context().orElseThrow(), sameInstance(context));
            uploadStarted.countDown();
            await(responseConsumed);
            try (output) {
                output.write(42);
            }
            uploadFinished.set(true);
        }, response -> {
            await(uploadStarted);
            assertThat(Thread.currentThread(), sameInstance(caller));
            response.close();
            assertThat(connection.releases.get(), is(0));
            assertThat(uploadFinished.get(), is(false));
            responseConsumed.countDown();
        }));
        assertThat(uploadFinished.get(), is(true));
        assertThat(connection.releases.get(), is(1));
        assertThat(connection.closes.get(), is(0));
    }

    @Test
    void redirectIsDeliveredWithoutReplayingUpload() {
        var connection = new Connection("HTTP/1.1 307 Temporary Redirect\r\nLocation: /other\r\nContent-Length: 0\r\n\r\n");
        var calls = new AtomicInteger();
        request(connection).followRedirects(true).exchange(output -> {
            calls.incrementAndGet();
            output.close();
        }, response -> assertThat(response.status(), is(Status.TEMPORARY_REDIRECT_307)));
        assertThat(calls.get(), is(1));
        assertThat(connection.releases.get(), is(0));
        assertThat(connection.closes.get(), greaterThan(0));
    }

    @Test
    void finalResponseToExpectContinueDoesNotInvokeUpload() {
        var connection = new Connection("HTTP/1.1 413 Content Too Large\r\nContent-Length: 0\r\n\r\n");
        var called = new AtomicBoolean();
        request(connection).sendExpectContinue(true).exchange(output -> {
            called.set(true);
            output.close();
        }, response -> assertThat(response.status().code(), is(413)));
        assertThat(called.get(), is(false));
    }

    @Test
    void continueTimeoutPreservesPartialStatusLine() {
        var reads = new AtomicInteger();
        var connection = new Connection(() -> switch (reads.getAndIncrement()) {
            case 0 -> "HTTP/1.1 200".getBytes(StandardCharsets.US_ASCII);
            case 1 -> throw new UncheckedIOException(new SocketTimeoutException("Continue deadline"));
            case 2 -> " OK\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
            default -> new byte[0];
        });
        var uploaded = new AtomicBoolean();
        request(connection).sendExpectContinue(true).exchange(output -> {
            uploaded.set(true);
            output.close();
        }, response -> assertThat(response.status(), is(Status.OK_200)));
        assertThat(uploaded.get(), is(true));
    }

    @Test
    void successfulFinalResponseToContinueDoesNotReuseUnframedRequest() {
        var connection = new Connection("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
        request(connection).sendExpectContinue(true).exchange(output -> output.close(), response -> {
            assertThat(response.status(), is(Status.OK_200));
        });
        assertThat(connection.releases.get(), is(0));
        assertThat(connection.closes.get(), greaterThan(0));
    }

    @Test
    void pendingIoWithoutProgressTimesOutAndClosesConnection() {
        var closed = new CountDownLatch(1);
        var connection = new Connection(() -> {
            try {
                await(closed);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            throw new UncheckedIOException(new IOException("Connection closed"));
        }, closed::countDown);
        UncheckedIOException failure = assertThrows(UncheckedIOException.class, () ->
                request(connection).readTimeout(Duration.ofMillis(200))
                        .exchange(output -> output.close(), response -> response.close()));
        assertThat(failure.getCause(), instanceOf(SocketTimeoutException.class));
        assertThat(connection.closes.get(), greaterThan(0));
        assertThat(connection.releases.get(), is(0));
    }

    @Test
    void healthyUploadCanOutlastResponseReadTimeout() {
        var uploaded = new CountDownLatch(1);
        var reads = new AtomicInteger();
        var connection = new Connection(() -> {
            if (reads.getAndIncrement() != 0) {
                return new byte[0];
            }
            try {
                await(uploaded);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        }, uploaded::countDown);
        var ticks = new Semaphore(0);
        try (var scheduler = Executors.newSingleThreadScheduledExecutor()) {
            var scheduled = scheduler.scheduleAtFixedRate(ticks::release, 0, 100, TimeUnit.MILLISECONDS);
            long started = System.nanoTime();
            request(connection).readTimeout(Duration.ofSeconds(1)).exchange(output -> {
                try (output) {
                    for (int i = 0; i < 21; i++) {
                        try {
                            if (!ticks.tryAcquire(5, TimeUnit.SECONDS)) {
                                throw new IOException("Upload progress scheduling timed out");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException(e);
                        }
                        output.write(i);
                    }
                }
                uploaded.countDown();
            }, response -> assertThat(response.status(), is(Status.OK_200)));
            scheduled.cancel(false);
            assertThat(System.nanoTime() - started, greaterThan(Duration.ofSeconds(1).toNanos()));
            assertThat(connection.releases.get(), is(1));
        }
    }

    @Test
    void responseFailureInterruptsAndJoinsUpload() {
        var connection = new Connection("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
        var started = new CountDownLatch(1);
        var stopped = new AtomicBoolean();
        var expected = new IOException("Response consumer failed");
        UncheckedIOException failure = assertThrows(UncheckedIOException.class, () ->
                request(connection).exchange(output -> {
                    started.countDown();
                    try {
                        await(new CountDownLatch(1));
                    } finally {
                        stopped.set(true);
                    }
                }, response -> {
                    await(started);
                    throw expected;
                }));
        assertThat(failure.getCause(), sameInstance(expected));
        assertThat(stopped.get(), is(true));
        assertThat(connection.releases.get(), is(0));
        assertThat(connection.closes.get(), greaterThan(0));
    }

    @Test
    void fixedLengthUnderflowDiscardsConnection() {
        var connection = new Connection("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
        assertThrows(UncheckedIOException.class, () ->
                request(connection).header(HeaderNames.CONTENT_LENGTH, "2").exchange(output -> {
                    try (output) {
                        output.write(42);
                    }
                }, response -> response.close()));
        assertThat(connection.releases.get(), is(0));
        assertThat(connection.closes.get(), greaterThan(0));
    }

    @Test
    void fixedLengthOverflowDiscardsConnection() {
        var connection = new Connection("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
        assertThrows(UncheckedIOException.class, () ->
                request(connection).header(HeaderNames.CONTENT_LENGTH, "1").exchange(output -> {
                    try (output) {
                        output.write(new byte[] {42, 43});
                    }
                }, response -> response.close()));
        assertThat(connection.releases.get(), is(0));
        assertThat(connection.closes.get(), greaterThan(0));
    }

    private static Http1ClientRequest request(Connection connection) {
        return Http1Client.builder().sendExpectContinue(false).build().post("http://localhost/test").connection(connection);
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Exchange coordination timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Upload interrupted", e);
        }
    }

    private static final class Connection implements ClientConnection {
        private final AtomicInteger releases = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private final ByteArrayOutputStream request = new ByteArrayOutputStream();
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
                request.writeBytes(buffer.readBytes());
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

        private Connection(String response) {
            var supplied = new AtomicBoolean();
            reader = DataReader.create(() -> supplied.compareAndSet(false, true)
                    ? response.getBytes(StandardCharsets.US_ASCII) : new byte[0]);
            onClose = () -> { };
        }

        private Connection(Supplier<byte[]> response) {
            this(response, () -> { });
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
            return writer;
        }

        @Override
        public String channelId() {
            return "exchange-test";
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
            return "exchange-test";
        }

        @Override
        public String childSocketId() {
            return "exchange-test";
        }

        @Override
        public byte[] get() {
            return new byte[0];
        }
    }
}
