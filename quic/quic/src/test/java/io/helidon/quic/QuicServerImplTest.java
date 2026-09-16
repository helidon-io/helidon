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

package io.helidon.quic;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.tls.Tls;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicServerImplTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void shouldNotifyPeerAndReleaseConnectionWhenCompletionWinsInterruptedAccept() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
                var fixture = new ServerFixture(executor)) {
            var handshake = fixture.startHandshake();
            InetSocketAddress listenerAddress = fixture.server.localAddress();

            assertInterruptedAccept(fixture.server, handshake.completion());

            // Start the next handshake before waiting for the discarded connection's peer notification.
            // With one connection allowed and idle timeout disabled, a leaked permit prevents admission.
            assertThat("interrupted accept keeps the shared listener running", fixture.server.isRunning(), is(true));
            assertThat("interrupted accept retains the listener address", fixture.server.localAddress(), is(listenerAddress));
            fixture.startHandshake().completion().run();
            QuicSession session = fixture.accept();
            assertThat(session.isOpen(), is(true));
            assertThat(session.applicationProtocol(), is("h3"));

            QuicTermination termination = handshake.connection().whenTerminated()
                    .toCompletableFuture()
                    .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat("interrupted accept notifies the peer", termination.origin(), is(QuicTermination.Origin.PEER));
            assertThat(termination.kind(), is(QuicTermination.Kind.CONNECTION_CLOSE));
            assertThat(termination.layer(), is(QuicTermination.Layer.TRANSPORT));
            assertThat(termination.errorCode(), is(OptionalLong.of(QuicTransportErrors.NO_ERROR.code())));
        }
    }

    @Test
    void shouldPreserveConnectionWhenCancellationWinsInterruptedAccept() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
                var fixture = new ServerFixture(executor)) {
            var handshake = fixture.startHandshake();

            assertInterruptedAccept(fixture.server, () -> { });
            handshake.completion().run();

            QuicSession session = fixture.accept();
            assertThat(session.isOpen(), is(true));
            assertThat(session.applicationProtocol(), is("h3"));
        }
    }

    @Test
    void shouldReturnAcceptedSessionWithoutTerminatingIt() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
                var fixture = new ServerFixture(executor)) {
            fixture.startHandshake().completion().run();

            QuicSession session = fixture.accept();

            assertThat(session.isOpen(), is(true));
            assertThat(session.applicationProtocol(), is("h3"));
            assertThat(session.quicVersion(), is(QuicVersion.QUIC_V1));
        }
    }

    private static void assertInterruptedAccept(QuicServer server, Runnable onRestore) throws Exception {
        var accepting = new InterruptedAcceptThread(server, onRestore);
        accepting.start();
        try {
            QuicException failure = accepting.result.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(failure.getMessage(), containsString("QUIC server accept interrupted"));
            assertThat(failure.getCause(), instanceOf(InterruptedException.class));
            assertThat("interrupted accept cleanup succeeds", failure.getSuppressed(), emptyArray());
        } finally {
            if (accepting.isAlive()) {
                accepting.interrupt();
            }
            assertThat("interrupted accept thread stopped", accepting.join(TIMEOUT), is(true));
        }
    }

    private static final class ServerFixture implements AutoCloseable {
        private final BlockingQueue<Runnable> handshakeCompletions = new LinkedBlockingQueue<>();
        private final ExecutorService executor;
        private final QuicServer server;
        private final QuicClientRuntime client;

        private ServerFixture(ExecutorService executor) throws Exception {
            this.executor = executor;
            Tls serverTls = Tls.builder()
                    .privateKey(QuicTlsRfc8448Vectors.rsaPrivateKey())
                    .privateKeyCertChain(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                    .build();
            QuicConfig config = QuicConfig.builder()
                    .availableVersions(List.of(QuicVersion.QUIC_V1))
                    .idleTimeout(Duration.ZERO)
                    .buildPrototype();
            server = QuicServer.builder()
                    .executor(task -> {
                        if (task instanceof CompletableFuture.AsynchronousCompletionTask) {
                            // Handshake completion is the server's only asynchronous future task here.
                            // Running it also delivers the connection to runtime.accept() synchronously.
                            handshakeCompletions.add(task);
                        } else {
                            executor.execute(task);
                        }
                    })
                    .tls(serverTls)
                    .quicConfig(config)
                    .applicationProtocols(List.of("h3"))
                    .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                    .maxConnections(1)
                    .shutdownTimeout(TIMEOUT)
                    .build();
            try {
                client = QuicClientRuntime.builder()
                        .executor(executor)
                        .tls(Tls.builder().trustAll(true).build())
                        .quicConfig(config)
                        .build();
            } catch (RuntimeException | Error failure) {
                server.close();
                throw failure;
            }
            try {
                server.start();
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        @Override
        public void close() {
            try {
                server.close();
            } finally {
                client.close();
            }
        }

        private Handshake startHandshake() throws InterruptedException {
            InetSocketAddress serverAddress = server.localAddress();
            // The RFC 8448 certificate identifies "rsa"; the network destination remains loopback.
            QuicClientConnection connection = client.createConnection(serverAddress,
                                                                       "rsa",
                                                                       serverAddress.getPort(),
                                                                       new String[] {"h3"});
            try {
                connection.startHandshake().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (ExecutionException | TimeoutException failure) {
                throw new AssertionError("Server must admit the next connection within its connection limit", failure);
            }
            Runnable completion = handshakeCompletions.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat("server handshake completion task", completion, notNullValue());
            return new Handshake(connection, completion);
        }

        private QuicSession accept() throws Exception {
            Future<QuicSession> accepted = executor.submit(server::accept);
            try {
                return accepted.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } finally {
                accepted.cancel(true);
            }
        }
    }

    private static final class InterruptedAcceptThread extends Thread {
        private final QuicServer server;
        private final Runnable onRestore;
        private final CompletableFuture<QuicException> result = new CompletableFuture<>();

        private boolean restored;

        private InterruptedAcceptThread(QuicServer server, Runnable onRestore) {
            super("interrupted-quic-server-accept");
            this.server = server;
            this.onRestore = onRestore;
            setDaemon(true);
        }

        @Override
        public void run() {
            super.interrupt();
            try {
                QuicException failure = assertThrows(QuicException.class, server::accept);
                assertThat("accept restores interruption", isInterrupted(), is(true));
                result.complete(failure);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            } finally {
                Thread.interrupted();
            }
        }

        @Override
        public void interrupt() {
            if (Thread.currentThread() == this && !restored) {
                restored = true;
                // The real CompletableFuture.get() has thrown and cleared interruption.
                // Complete delivery before QuicBlockingSupport restores the flag and tries cancellation.
                onRestore.run();
            }
            super.interrupt();
        }
    }

    private record Handshake(QuicClientConnection connection, Runnable completion) {
    }
}
