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

package io.helidon.webclient.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.StandardProtocolFamily;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.tls.Tls;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Handshake;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.HandshakeOutcome;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.Role;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Timeout(20)
class ClientTransportObservationTest {
    @Test
    void unobservedCustomConnectionRetainsCanonicalNoop() {
        var connection = mock(ClientConnection.class);
        assertThat(HttpTransportObserverSupport.observe(connection, HttpTransportObserver.noop()), sameInstance(connection));
        assertThat(HttpTransportObserverSupport.connection(connection), sameInstance(ConnectionObservation.noop()));
        verifyNoMoreInteractions(connection);
    }

    @Test
    void reportsClientHandshakeBeforeProtocolAndExchange() {
        var observer = mock(HttpTransportObserver.class);
        var delegate = mock(ConnectionObservation.class);
        var handshake = mock(HandshakeObservation.class);
        var stream = mock(StreamObservation.class);
        when(observer.connectionOpened(Role.CLIENT, "tcp", Handshake.TLS)).thenReturn(delegate);
        when(delegate.handshakeStarted()).thenReturn(handshake);
        when(delegate.streamOpened(Direction.BIDIRECTIONAL, Initiator.LOCAL)).thenReturn(stream);
        var connection = new ClientTransportObservation(observer);
        connection.opened("tcp", Handshake.TLS);
        var completion = connection.handshakeStarted();
        assertThat(connection.handshakeStarted(), sameInstance(completion));
        completion.close(HandshakeOutcome.SUCCESS);
        completion.close(HandshakeOutcome.SUCCESS);
        connection.protocolSelected("http/2");
        connection.streamOpened(Direction.BIDIRECTIONAL, Initiator.LOCAL).close(StreamOutcome.COMPLETED);
        connection.closed();

        var order = inOrder(observer, delegate, handshake, stream);
        order.verify(observer).connectionOpened(Role.CLIENT, "tcp", Handshake.TLS);
        order.verify(delegate).handshakeStarted();
        order.verify(handshake).close(HandshakeOutcome.SUCCESS);
        order.verify(delegate).protocolSelected("http/2");
        order.verify(delegate).streamOpened(Direction.BIDIRECTIONAL, Initiator.LOCAL);
        order.verify(stream).close(StreamOutcome.COMPLETED);
        order.verify(delegate).close(ConnectionOutcome.LOCAL_CLOSE);
        order.verifyNoMoreInteractions();
    }

    @Test
    void repeatedProtocolSelectionsOnlyPublishActualChanges() {
        var delegate = mock(ConnectionObservation.class);
        var connection = connection(delegate);

        connection.protocolSelected("http/1.1");
        connection.protocolSelected(new String("http/1.1".toCharArray()));
        connection.protocolSelected("http/2");
        connection.protocolSelected("http/2");
        connection.protocolSelected("http/1.1");

        var order = inOrder(delegate);
        order.verify(delegate).protocolSelected("http/1.1");
        order.verify(delegate).protocolSelected("http/2");
        order.verify(delegate).protocolSelected("http/1.1");
        order.verifyNoMoreInteractions();
    }

    @Test
    void reentrantProtocolSelectionRetainsTheNestedSelection() {
        var delegate = mock(ConnectionObservation.class);
        var connection = connection(delegate);
        var selections = new AtomicInteger();
        doAnswer(_ -> {
            if (selections.getAndIncrement() == 0) {
                connection.protocolSelected("http/1.1");
                connection.protocolSelected("http/2");
            }
            return null;
        }).when(delegate).protocolSelected("http/1.1");

        connection.protocolSelected("http/1.1");
        connection.protocolSelected("http/2");
        connection.protocolSelected("http/1.1");

        var order = inOrder(delegate);
        order.verify(delegate).protocolSelected("http/1.1");
        order.verify(delegate).protocolSelected("http/2");
        order.verify(delegate).protocolSelected("http/1.1");
        order.verifyNoMoreInteractions();
    }

    @Test
    void protocolSelectionPreservesValidationAndInactiveSuppression() {
        var delegate = mock(ConnectionObservation.class);
        HttpTransportObserver observer = (_, _, _) -> delegate;
        var connection = new ClientTransportObservation(HttpTransportObserver.compose(List.of(observer)));

        connection.protocolSelected(null);
        connection.protocolSelected(" ");
        connection.protocolSelected("http/1.1");
        verifyNoMoreInteractions(delegate);

        connection.opened("tcp", Handshake.NONE);
        assertThrows(NullPointerException.class, () -> connection.protocolSelected(null));
        assertThrows(IllegalArgumentException.class, () -> connection.protocolSelected(""));
        assertThrows(IllegalArgumentException.class, () -> connection.protocolSelected(" \t"));
        connection.protocolSelected("http/1.1");
        connection.closed();
        connection.protocolSelected(null);
        connection.protocolSelected(" ");
        connection.protocolSelected("http/2");

        var order = inOrder(delegate);
        order.verify(delegate).protocolSelected("http/1.1");
        order.verify(delegate).close(ConnectionOutcome.LOCAL_CLOSE);
        order.verifyNoMoreInteractions();
    }

    @Test
    void physicalFailureOwnsPendingChildrenAndSuppressesLateCallbacks() {
        var delegate = mock(ConnectionObservation.class);
        var child = mock(StreamObservation.class);
        when(delegate.streamOpened(any(), any())).thenReturn(child);
        var connection = connection(delegate);
        var stream = connection.streamOpened(Direction.BIDIRECTIONAL, Initiator.LOCAL);
        connection.failed(new UncheckedIOException(new SocketTimeoutException("write timed out")));
        connection.closed();
        stream.close(StreamOutcome.CANCELLED);
        connection.protocolSelected("http/1.1");
        connection.closed();
        assertThat(connection.streamOpened(Direction.BIDIRECTIONAL, Initiator.LOCAL), sameInstance(StreamObservation.noop()));
        verify(delegate).streamOpened(Direction.BIDIRECTIONAL, Initiator.LOCAL);
        verify(delegate).close(ConnectionOutcome.TIMEOUT);
        verifyNoMoreInteractions(delegate, child);
    }

    @Test
    void failedGracefulCloseReportsTransportFailure() {
        var delegate = mock(ConnectionObservation.class);
        var connection = connection(delegate);
        connection.outcome(ConnectionOutcome.LOCAL_CLOSE);
        connection.failed(new UncheckedIOException(new SocketTimeoutException("close write timed out")));
        connection.closed();
        verify(delegate).close(ConnectionOutcome.TIMEOUT);
    }

    @Test
    void successfulExchangeClosesOnlyOnce() {
        var delegate = mock(ConnectionObservation.class);
        var child = mock(StreamObservation.class);
        when(delegate.streamOpened(any(), any())).thenReturn(child);
        var stream = connection(delegate).streamOpened(Direction.BIDIRECTIONAL, Initiator.LOCAL);
        stream.close(StreamOutcome.COMPLETED);
        stream.close(StreamOutcome.CANCELLED);
        verify(child).close(StreamOutcome.COMPLETED);
        verifyNoMoreInteractions(child);
    }

    @Test
    void callbacksForMultiplexedClientStreamsAreSequential() throws Exception {
        var delegate = mock(ConnectionObservation.class);
        var active = new AtomicInteger();
        var overlaps = new AtomicInteger();
        var closed = new AtomicInteger();
        when(delegate.streamOpened(any(), any())).thenAnswer(_ -> {
            if (active.incrementAndGet() != 1) {
                overlaps.incrementAndGet();
            }
            Thread.yield();
            active.decrementAndGet();
            return (StreamObservation) _ -> {
                if (active.incrementAndGet() != 1) {
                    overlaps.incrementAndGet();
                }
                Thread.yield();
                closed.incrementAndGet();
                active.decrementAndGet();
            };
        });
        var connection = connection(delegate);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 100; i++) {
                tasks.add(executor.submit(() -> connection.streamOpened(Direction.BIDIRECTIONAL, Initiator.LOCAL)
                        .close(StreamOutcome.COMPLETED)));
            }
            for (var task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        }
        assertThat("All client exchanges completed exactly once", closed.get(), is(100));
        assertThat("Connection callbacks cannot overlap", overlaps.get(), is(0));
    }

    @Test
    void physicalTcpConnectionOpensOnlyAfterConnectAndClosesOnce() throws Exception {
        var observer = mock(HttpTransportObserver.class);
        var delegate = mock(ConnectionObservation.class);
        var listener = mock(ConnectionListener.class);
        when(observer.connectionOpened(Role.CLIENT, "tcp", Handshake.NONE)).thenReturn(delegate);
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5_000);
            var connection = tcpConnection(server.getLocalPort(), Tls.builder().enabled(false).build(), listener, observer);
            verifyNoMoreInteractions(observer, delegate);
            try {
                connection.connect();
                try (var accepted = server.accept()) {
                    assertThat(accepted.isConnected(), is(true));
                    assertThat(connection.isConnected(), is(true));
                }
            } finally {
                connection.closeResource();
            }
            connection.closeResource();

            var order = inOrder(observer, listener, delegate);
            order.verify(observer).connectionOpened(Role.CLIENT, "tcp", Handshake.NONE);
            order.verify(listener).socketConnected(any());
            order.verify(delegate).close(ConnectionOutcome.LOCAL_CLOSE);
            order.verifyNoMoreInteractions();
        }
    }

    @Test
    void physicalUnixConnectionOpensOnlyAfterConnectAndClosesOnce(@TempDir Path directory) throws Exception {
        ServerSocketChannel server;
        try {
            server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        } catch (UnsupportedOperationException e) {
            assumeTrue(false, "UNIX domain sockets are not supported by this platform: " + e.getMessage());
            return;
        }
        Path path = directory.resolve("s");
        var address = UnixDomainSocketAddress.of(path);
        try (server) {
            server.bind(address);
            var observer = mock(HttpTransportObserver.class);
            var delegate = mock(ConnectionObservation.class);
            var listener = mock(ConnectionListener.class);
            when(observer.connectionOpened(Role.CLIENT, "unix", Handshake.NONE)).thenReturn(delegate);
            var client = mock(WebClient.class);
            var config = WebClientConfig.builder()
                    .servicesDiscoverServices(false)
                    .connectionListener(listener)
                    .buildPrototype();
            when(client.prototype()).thenReturn(config);
            var connection = HttpTransportObserverSupport.observe(
                    UnixDomainSocketClientConnection.create(client, Tls.builder().enabled(false).build(),
                                                             List.of("http/1.1"), address, _ -> false, _ -> {
                                                             }),
                    observer);
            verifyNoMoreInteractions(observer, delegate);
            try {
                connection.connect();
                try (var accepted = server.accept()) {
                    assertThat(accepted.isConnected(), is(true));
                    assertThat(connection.isConnected(), is(true));
                }
            } finally {
                connection.closeResource();
            }
            connection.closeResource();

            var order = inOrder(observer, listener, delegate);
            order.verify(observer).connectionOpened(Role.CLIENT, "unix", Handshake.NONE);
            order.verify(listener).socketChannelConnected(any());
            order.verify(delegate).close(ConnectionOutcome.LOCAL_CLOSE);
            order.verifyNoMoreInteractions();
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void initializerFailureClosesOpenedPhysicalConnectionWithError() throws Exception {
        var observer = mock(HttpTransportObserver.class);
        var delegate = mock(ConnectionObservation.class);
        var listener = mock(ConnectionListener.class);
        when(observer.connectionOpened(Role.CLIENT, "tcp", Handshake.NONE)).thenReturn(delegate);
        doThrow(new IOException("Initializer failed")).when(listener).socketConnected(any());
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var connection = tcpConnection(server.getLocalPort(), Tls.builder().enabled(false).build(), listener, observer);
            try {
                assertThrows(UncheckedIOException.class, connection::connect);
                assertThat(connection.socket().isClosed(), is(true));
            } finally {
                connection.closeResource();
            }

            var order = inOrder(observer, listener, delegate);
            order.verify(observer).connectionOpened(Role.CLIENT, "tcp", Handshake.NONE);
            order.verify(listener).socketConnected(any());
            order.verify(delegate).close(ConnectionOutcome.ERROR);
            order.verifyNoMoreInteractions();
        }
    }

    @Test
    void failedTlsHandshakeClosesPhysicalObservationWithPendingHandshake() throws Exception {
        var observer = mock(HttpTransportObserver.class);
        var delegate = mock(ConnectionObservation.class);
        var handshake = mock(HandshakeObservation.class);
        when(observer.connectionOpened(Role.CLIENT, "tcp", Handshake.TLS)).thenReturn(delegate);
        when(delegate.handshakeStarted()).thenReturn(handshake);
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setSoTimeout(5_000);
            var peer = executor.submit(() -> {
                try (var accepted = server.accept()) {
                    accepted.getOutputStream().write("Not a TLS record\r\n".getBytes(StandardCharsets.US_ASCII));
                }
                return null;
            });
            var connection = tcpConnection(server.getLocalPort(),
                                           Tls.builder().trustAll(true).build(),
                                           ConnectionListener.createNoop(),
                                           observer);
            try {
                assertThrows(UncheckedIOException.class, connection::connect);
                assertThat(connection.socket().isClosed(), is(true));
                peer.get(10, TimeUnit.SECONDS);
            } finally {
                connection.closeResource();
            }

            var order = inOrder(observer, delegate);
            order.verify(observer).connectionOpened(Role.CLIENT, "tcp", Handshake.TLS);
            order.verify(delegate).handshakeStarted();
            order.verify(delegate).close(ConnectionOutcome.ERROR);
            order.verifyNoMoreInteractions();
            verifyNoMoreInteractions(handshake);
        }
    }

    private static TcpClientConnection tcpConnection(int port,
                                                     Tls tls,
                                                     ConnectionListener listener,
                                                     HttpTransportObserver observer) {
        var client = mock(WebClient.class);
        var config = WebClientConfig.builder()
                .servicesDiscoverServices(false)
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(5))
                .connectionListener(listener)
                .buildPrototype();
        when(client.prototype()).thenReturn(config);
        ClientUri uri = ClientUri.create(URI.create((tls.enabled() ? "https" : "http") + "://localhost:" + port));
        ConnectionKey key = ConnectionKey.create(uri,
                                                 tls,
                                                 (_, _) -> InetAddress.getLoopbackAddress(),
                                                 DnsAddressLookup.IPV4,
                                                 Proxy.noProxy());
        return HttpTransportObserverSupport.observe(TcpClientConnection.create(client, key, List.of("http/1.1"),
                                                                               _ -> false, _ -> {
                                                                               }),
                                                     observer);
    }

    private static ClientTransportObservation connection(ConnectionObservation delegate) {
        var observer = mock(HttpTransportObserver.class);
        when(observer.connectionOpened(Role.CLIENT, "tcp", Handshake.NONE)).thenReturn(delegate);
        var connection = new ClientTransportObservation(observer);
        connection.opened("tcp", Handshake.NONE);
        return connection;
    }
}
