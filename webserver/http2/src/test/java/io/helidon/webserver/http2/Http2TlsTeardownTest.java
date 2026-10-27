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

package io.helidon.webserver.http2;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLEngine;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.SocketWriter;
import io.helidon.common.socket.TlsNioSocket;
import io.helidon.common.tls.Tls;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Timeout(20)
class Http2TlsTeardownTest {
    private static final byte[] TERMINAL_DATA = {0, 0, 3, 0, 1, 0, 0, 0, 1, 'e', 'n', 'd'};

    @ParameterizedTest
    @CsvSource({"TLSv1.2, 1", "TLSv1.2, 2", "TLSv1.3, 1", "TLSv1.3, 2"})
    void successfulTerminalPublicationPreservesTlsCloseNotify(String protocol, int writeQueueLength) throws Exception {
        StreamObservation delegate = mock(StreamObservation.class);
        Http2TransportObservation observation = observation(delegate);
        Http2TransportObservation.Stream stream = observation.openStream(true);
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch publish = new CountDownLatch(1);
        CountDownLatch stopping = new CountDownLatch(1);
        AtomicInteger aborts = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        try (TlsPair pair = TlsPair.create(protocol);
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            HelidonSocket gatedSocket = mock(HelidonSocket.class);
            doAnswer(invocation -> {
                pair.server.write(invocation.getArgument(0));
                written.countDown();
                awaitUninterruptibly(publish);
                return null;
            }).when(gatedSocket).write(any(BufferData.class));
            SocketWriter socketWriter = SocketWriter.create(executor, gatedSocket, writeQueueLength, false);
            Thread writer = Thread.ofPlatform().start(() -> {
                stream.beginTerminalWrite();
                try {
                    socketWriter.writeNow(BufferData.create(TERMINAL_DATA));
                    stream.localEnd();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                    stream.fail();
                } finally {
                    stream.endTerminalWrite();
                }
            });
            Thread teardown = Thread.ofPlatform().unstarted(() -> {
                stopping.countDown();
                try {
                    observation.stop(Duration.ofNanos(Long.MAX_VALUE), () -> {
                        aborts.incrementAndGet();
                        pair.abort();
                    });
                    socketWriter.close();
                    pair.server.close();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            });
            try {
                await(written);
                assertThat(pair.client.get(), is(TERMINAL_DATA));
                teardown.start();
                await(stopping);
                awaitState(teardown, Thread.State.TIMED_WAITING);
                assertThat("healthy publication is not aborted while waiting", aborts.get(), is(0));
                assertThat("client has not received close_notify yet", pair.clientEngine.isInboundDone(), is(false));

                publish.countDown();
                join(writer);
                join(teardown);

                pair.client.get();
                assertThat("graceful TLS close sends close_notify", pair.clientEngine.isInboundDone(), is(true));
                assertThat(failure.get(), nullValue());
                assertThat(aborts.get(), is(0));
                verify(delegate).close(StreamOutcome.COMPLETED);
                verify(delegate, never()).close(StreamOutcome.ERROR);
            } finally {
                publish.countDown();
                pair.abort();
                join(writer);
                if (teardown.getState() != Thread.State.NEW) {
                    join(teardown);
                }
                socketWriter.close();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"TLSv1.2", "TLSv1.3"})
    void zeroGraceAbortsPendingTlsWriteAndPublishesFailure(String protocol) throws Exception {
        StreamObservation delegate = mock(StreamObservation.class);
        Http2TransportObservation observation = observation(delegate);
        Http2TransportObservation.Stream stream = observation.openStream(true);
        CountDownLatch registered = new CountDownLatch(1);
        CountDownLatch aborted = new CountDownLatch(1);
        AtomicInteger aborts = new AtomicInteger();
        AtomicReference<Throwable> writeFailure = new AtomicReference<>();

        try (TlsPair pair = TlsPair.create(protocol)) {
            Thread writer = Thread.ofPlatform().start(() -> {
                stream.beginTerminalWrite();
                try {
                    registered.countDown();
                    awaitUninterruptibly(aborted);
                    pair.server.write(BufferData.create(TERMINAL_DATA));
                    stream.localEnd();
                } catch (Throwable t) {
                    writeFailure.set(t);
                    stream.fail();
                } finally {
                    stream.endTerminalWrite();
                }
            });
            try {
                await(registered);
                observation.stop(Duration.ZERO, () -> {
                    aborts.incrementAndGet();
                    pair.abort();
                    aborted.countDown();
                });
                join(writer);

                assertThat(aborts.get(), is(1));
                assertThat("write on the aborted TLS transport fails", writeFailure.get(), notNullValue());
                verify(delegate).close(StreamOutcome.ERROR);
                verify(delegate, never()).close(StreamOutcome.COMPLETED);
                pair.client.get();
                assertThat("forced raw close does not send close_notify", pair.clientEngine.isInboundDone(), is(false));
            } finally {
                pair.abort();
                aborted.countDown();
                join(writer);
            }
        }
    }

    private static Http2TransportObservation observation(StreamObservation stream) {
        ConnectionObservation connection = mock(ConnectionObservation.class);
        when(connection.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE)).thenReturn(stream);
        return new Http2TransportObservation(connection);
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat("coordination latch released", latch.await(5, TimeUnit.SECONDS), is(true));
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    await(latch);
                    return;
                } catch (InterruptedException _) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void awaitState(Thread thread, Thread.State state) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != state && thread.isAlive() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat("teardown is waiting for publication", thread.getState(), is(state));
    }

    private static void join(Thread thread) throws InterruptedException {
        thread.join(TimeUnit.SECONDS.toMillis(5));
        assertThat("owned thread terminated", thread.isAlive(), is(false));
    }

    private static final class TlsPair implements AutoCloseable {
        private final SocketChannel serverChannel;
        private final SocketChannel clientChannel;
        private final TlsNioSocket server;
        private final TlsNioSocket client;
        private final SSLEngine clientEngine;

        private TlsPair(SocketChannel serverChannel,
                        SocketChannel clientChannel,
                        TlsNioSocket server,
                        TlsNioSocket client,
                        SSLEngine clientEngine) {
            this.serverChannel = serverChannel;
            this.clientChannel = clientChannel;
            this.server = server;
            this.client = client;
            this.clientEngine = clientEngine;
        }

        static TlsPair create(String protocol) throws Exception {
            Keys keys = Keys.builder()
                    .keystore(store -> store.passphrase("password").keystore(Resource.create("server.p12")))
                    .build();
            Tls serverTls = Tls.builder().privateKey(keys).privateKeyCertChain(keys).build();
            SSLEngine serverEngine = serverTls.sslContext().createSSLEngine();
            serverEngine.setEnabledProtocols(new String[] {protocol});
            SSLEngine clientEngine = Tls.builder().trustAll(true).build().sslContext().createSSLEngine();
            clientEngine.setEnabledProtocols(new String[] {protocol});

            try (ServerSocketChannel listener = ServerSocketChannel.open()) {
                listener.bind(new InetSocketAddress("127.0.0.1", 0));
                SocketChannel clientChannel = SocketChannel.open(listener.getLocalAddress());
                SocketChannel serverChannel = listener.accept();
                TlsPair pair = new TlsPair(serverChannel,
                                           clientChannel,
                                           TlsNioSocket.server(serverChannel, serverEngine, "server", "listener"),
                                           TlsNioSocket.client(clientChannel, clientEngine, "client"),
                                           clientEngine);
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread handshake = Thread.ofPlatform().start(() -> {
                    try {
                        pair.server.handshake();
                    } catch (Throwable t) {
                        failure.set(t);
                        pair.abort();
                    }
                });
                try {
                    pair.client.handshake();
                    join(handshake);
                    assertThat("server TLS handshake succeeded", failure.get(), nullValue());
                    return pair;
                } catch (Throwable t) {
                    pair.close();
                    join(handshake);
                    throw t;
                }
            }
        }

        void abort() {
            try {
                serverChannel.close();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to abort server channel", e);
            }
        }

        @Override
        public void close() throws Exception {
            try {
                serverChannel.close();
            } finally {
                clientChannel.close();
            }
        }
    }
}
