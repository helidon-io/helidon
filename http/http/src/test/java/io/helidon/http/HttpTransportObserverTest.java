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

package io.helidon.http;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.HandshakeOutcome;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.helidon.http.HttpTransportObserver.Direction.BIDIRECTIONAL;
import static io.helidon.http.HttpTransportObserver.Handshake.TLS;
import static io.helidon.http.HttpTransportObserver.HandshakeOutcome.SUCCESS;
import static io.helidon.http.HttpTransportObserver.Initiator.REMOTE;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_1_1;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_2;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_3;
import static io.helidon.http.HttpTransportObserver.Role.SERVER;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.CANCELLED;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.COMPLETED;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_QUIC;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_TCP;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_UNIX;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpTransportObserverTest {
    @Test
    void knownIdentifiersMatchMetricsTags() {
        assertThat(TRANSPORT_TCP, is("tcp"));
        assertThat(TRANSPORT_UNIX, is("unix"));
        assertThat(TRANSPORT_QUIC, is("quic"));
        assertThat(PROTOCOL_HTTP_1_1, is("http/1.1"));
        assertThat(PROTOCOL_HTTP_2, is("http/2"));
        assertThat(PROTOCOL_HTTP_3, is("http/3"));
    }

    @Test
    void customIdentifiersAreForwarded() {
        AtomicReference<String> observedTransport = new AtomicReference<>();
        AtomicReference<String> observedProtocol = new AtomicReference<>();
        HttpTransportObserver observer = (role, transport, handshake) -> {
            observedTransport.set(transport);
            return new ConnectionObservation() {
                @Override
                public HandshakeObservation handshakeStarted() {
                    return HandshakeObservation.noop();
                }

                @Override
                public void protocolSelected(String protocol) {
                    observedProtocol.set(protocol);
                }

                @Override
                public StreamObservation streamOpened(Direction direction, Initiator initiator) {
                    return StreamObservation.noop();
                }

                @Override
                public void close(ConnectionOutcome outcome) {
                }
            };
        };
        HttpTransportObserver composed = HttpTransportObserver.compose(List.of(observer));
        ConnectionObservation connection = composed.connectionOpened(SERVER, "custom-transport", TLS);

        connection.protocolSelected("custom-protocol");

        assertThat(observedTransport.get(), is("custom-transport"));
        assertThat(observedProtocol.get(), is("custom-protocol"));
        assertThrows(IllegalArgumentException.class, () -> composed.connectionOpened(SERVER, " ", TLS));
        assertThrows(NullPointerException.class, () -> composed.connectionOpened(SERVER, null, TLS));
    }

    @Test
    void emptyCompositionIsNoOp() {
        assertThat(HttpTransportObserver.compose(List.of()), sameInstance(HttpTransportObserver.noop()));
        assertThat(HttpTransportObserver.compose(List.of(HttpTransportObserver.noop())),
                   sameInstance(HttpTransportObserver.noop()));
    }

    @Test
    void compositionDeduplicatesByIdentityAndPreservesOrder() {
        List<String> events = new ArrayList<>();
        HttpTransportObserver first = (role, transport, handshake) -> {
            events.add("first");
            return ConnectionObservation.noop();
        };
        HttpTransportObserver second = (role, transport, handshake) -> {
            events.add("second");
            return ConnectionObservation.noop();
        };

        HttpTransportObserver.compose(List.of(first, first, second))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);

        assertThat(events, is(List.of("first", "second")));
    }

    @Test
    void compositionIsolatesObserverFailures() {
        AtomicInteger connectionOpened = new AtomicInteger();
        AtomicInteger protocolSelected = new AtomicInteger();
        AtomicInteger streamOpened = new AtomicInteger();
        AtomicInteger streamClosed = new AtomicInteger();
        AtomicInteger connectionClosed = new AtomicInteger();

        HttpTransportObserver failingOpen = (role, transport, handshake) -> {
            throw new IllegalStateException("open");
        };
        HttpTransportObserver failingCallbacks = (role, transport, handshake) -> new ConnectionObservation() {
            @Override
            public HandshakeObservation handshakeStarted() {
                throw new IllegalStateException("handshake");
            }

            @Override
            public void protocolSelected(String protocol) {
                throw new IllegalStateException("protocol");
            }

            @Override
            public StreamObservation streamOpened(Direction direction, Initiator initiator) {
                throw new IllegalStateException("stream");
            }

            @Override
            public void close(ConnectionOutcome outcome) {
                throw new IllegalStateException("close");
            }
        };
        HttpTransportObserver recording = (role, transport, handshake) -> {
            connectionOpened.incrementAndGet();
            return new ConnectionObservation() {
                @Override
                public HandshakeObservation handshakeStarted() {
                    return outcome -> {
                    };
                }

                @Override
                public void protocolSelected(String protocol) {
                    protocolSelected.incrementAndGet();
                }

                @Override
                public StreamObservation streamOpened(Direction direction, Initiator initiator) {
                    streamOpened.incrementAndGet();
                    return outcome -> streamClosed.incrementAndGet();
                }

                @Override
                public void close(ConnectionOutcome outcome) {
                    connectionClosed.incrementAndGet();
                }
            };
        };

        assertDoesNotThrow(() -> {
            ConnectionObservation connection =
                    HttpTransportObserver.compose(List.of(failingOpen, failingCallbacks, recording))
                            .connectionOpened(SERVER, TRANSPORT_TCP, TLS);
            connection.handshakeStarted().close(SUCCESS);
            connection.protocolSelected(PROTOCOL_HTTP_2);
            connection.streamOpened(BIDIRECTIONAL, REMOTE).close(COMPLETED);
            connection.close(ConnectionOutcome.LOCAL_CLOSE);
        });

        assertThat(connectionOpened.get(), is(1));
        assertThat(protocolSelected.get(), is(1));
        assertThat(streamOpened.get(), is(1));
        assertThat(streamClosed.get(), is(1));
        assertThat(connectionClosed.get(), is(1));
    }

    @Test
    void virtualMachineErrorFromConnectionOpenPropagates() {
        OutOfMemoryError failure = new OutOfMemoryError("probe");
        AtomicInteger connectionOpened = new AtomicInteger();
        HttpTransportObserver failing = (role, transport, handshake) -> {
            throw failure;
        };
        HttpTransportObserver recording = (role, transport, handshake) -> {
            connectionOpened.incrementAndGet();
            return ConnectionObservation.noop();
        };

        OutOfMemoryError thrown = assertThrows(OutOfMemoryError.class,
                                                () -> HttpTransportObserver.compose(List.of(failing, recording))
                                                        .connectionOpened(SERVER, TRANSPORT_TCP, TLS));

        assertThat(thrown, sameInstance(failure));
        assertThat(connectionOpened.get(), is(0));
    }

    @Test
    void virtualMachineErrorFromProtocolSelectionPropagates() {
        OutOfMemoryError failure = new OutOfMemoryError("probe");
        AtomicInteger protocolSelected = new AtomicInteger();
        HttpTransportObserver failing = (role, transport, handshake) -> new ConnectionObservation() {
            @Override
            public HandshakeObservation handshakeStarted() {
                return HandshakeObservation.noop();
            }

            @Override
            public void protocolSelected(String protocol) {
                throw failure;
            }

            @Override
            public StreamObservation streamOpened(Direction direction, Initiator initiator) {
                return StreamObservation.noop();
            }

            @Override
            public void close(ConnectionOutcome outcome) {
            }
        };
        HttpTransportObserver recording = (role, transport, handshake) -> new ConnectionObservation() {
            @Override
            public HandshakeObservation handshakeStarted() {
                return HandshakeObservation.noop();
            }

            @Override
            public void protocolSelected(String protocol) {
                protocolSelected.incrementAndGet();
            }

            @Override
            public StreamObservation streamOpened(Direction direction, Initiator initiator) {
                return StreamObservation.noop();
            }

            @Override
            public void close(ConnectionOutcome outcome) {
            }
        };
        ConnectionObservation connection = HttpTransportObserver.compose(List.of(failing, recording))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);

        OutOfMemoryError thrown = assertThrows(OutOfMemoryError.class,
                                                () -> connection.protocolSelected(PROTOCOL_HTTP_2));

        assertThat(thrown, sameInstance(failure));
        assertThat(protocolSelected.get(), is(0));
    }

    @ParameterizedTest
    @EnumSource(ConnectionOutcome.class)
    void connectionCloseCompletesOpenChildrenExactlyOnce(ConnectionOutcome outcome) {
        AtomicInteger handshakeClosed = new AtomicInteger();
        AtomicInteger streamClosed = new AtomicInteger();
        AtomicInteger connectionClosed = new AtomicInteger();
        AtomicReference<HandshakeOutcome> handshakeOutcome = new AtomicReference<>();
        AtomicReference<StreamOutcome> streamOutcome = new AtomicReference<>();
        AtomicReference<ConnectionOutcome> connectionOutcome = new AtomicReference<>();
        HttpTransportObserver observer = (role, transport, handshake) -> new ConnectionObservation() {
            @Override
            public HandshakeObservation handshakeStarted() {
                return outcome -> {
                    handshakeClosed.incrementAndGet();
                    handshakeOutcome.set(outcome);
                };
            }

            @Override
            public void protocolSelected(String protocol) {
            }

            @Override
            public StreamObservation streamOpened(Direction direction, Initiator initiator) {
                return outcome -> {
                    streamClosed.incrementAndGet();
                    streamOutcome.set(outcome);
                };
            }

            @Override
            public void close(ConnectionOutcome outcome) {
                connectionClosed.incrementAndGet();
                connectionOutcome.set(outcome);
            }
        };
        ConnectionObservation connection = HttpTransportObserver.compose(List.of(observer))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        HandshakeObservation handshake = connection.handshakeStarted();
        StreamObservation stream = connection.streamOpened(BIDIRECTIONAL, REMOTE);

        assertThat(connection.handshakeStarted(), sameInstance(handshake));
        connection.close(outcome);
        connection.close(outcome);
        handshake.close(SUCCESS);
        stream.close(COMPLETED);

        assertThat(handshakeClosed.get(), is(1));
        HandshakeOutcome expectedHandshakeOutcome = switch (outcome) {
            case NORMAL, LOCAL_CLOSE -> HandshakeOutcome.CANCELLED;
            case REMOTE_CLOSE, ERROR -> HandshakeOutcome.FAILURE;
            case TIMEOUT -> HandshakeOutcome.TIMEOUT;
        };
        assertThat(handshakeOutcome.get(), is(expectedHandshakeOutcome));
        assertThat(streamClosed.get(), is(1));
        StreamOutcome expectedStreamOutcome = switch (outcome) {
            case NORMAL, LOCAL_CLOSE, REMOTE_CLOSE -> CANCELLED;
            case TIMEOUT, ERROR -> StreamOutcome.ERROR;
        };
        assertThat(streamOutcome.get(), is(expectedStreamOutcome));
        assertThat(connectionClosed.get(), is(1));
        assertThat(connectionOutcome.get(), is(outcome));
    }

    @Test
    void protocolSelectionForwardsOnlySelectionsAndTransitions() {
        AtomicInteger selections = new AtomicInteger();
        HttpTransportObserver observer = (role, transport, handshake) -> new ConnectionObservation() {
            @Override
            public HandshakeObservation handshakeStarted() {
                return HandshakeObservation.noop();
            }

            @Override
            public void protocolSelected(String protocol) {
                selections.incrementAndGet();
            }

            @Override
            public StreamObservation streamOpened(Direction direction, Initiator initiator) {
                return StreamObservation.noop();
            }

            @Override
            public void close(ConnectionOutcome outcome) {
            }
        };
        ConnectionObservation connection = HttpTransportObserver.compose(List.of(observer))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);

        connection.protocolSelected(PROTOCOL_HTTP_1_1);
        connection.protocolSelected(new String(PROTOCOL_HTTP_1_1));
        connection.protocolSelected(PROTOCOL_HTTP_2);

        assertThat(selections.get(), is(2));
        assertThrows(IllegalArgumentException.class, () -> connection.protocolSelected(" "));
        assertThrows(NullPointerException.class, () -> connection.protocolSelected(null));
    }

    @Test
    void concurrentConnectionCloseAndStreamOpenCompleteExactlyOnce() throws Exception {
        CountDownLatch streamOpenStarted = new CountDownLatch(1);
        CountDownLatch completeStreamOpen = new CountDownLatch(1);
        AtomicInteger streamClosed = new AtomicInteger();
        AtomicInteger connectionClosed = new AtomicInteger();
        List<String> events = new CopyOnWriteArrayList<>();
        HttpTransportObserver observer = (role, transport, handshake) -> new ConnectionObservation() {
            @Override
            public HandshakeObservation handshakeStarted() {
                return HandshakeObservation.noop();
            }

            @Override
            public void protocolSelected(String protocol) {
            }

            @Override
            public StreamObservation streamOpened(Direction direction, Initiator initiator) {
                events.add("stream-open");
                streamOpenStarted.countDown();
                try {
                    if (!completeStreamOpen.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to complete stream open");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while opening stream", e);
                }
                return outcome -> {
                    streamClosed.incrementAndGet();
                    events.add("stream-close");
                };
            }

            @Override
            public void close(ConnectionOutcome outcome) {
                connectionClosed.incrementAndGet();
                events.add("connection-close");
            }
        };
        ConnectionObservation connection = HttpTransportObserver.compose(List.of(observer))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        CompletableFuture<StreamObservation> opening = CompletableFuture.supplyAsync(
                () -> connection.streamOpened(BIDIRECTIONAL, REMOTE));

        assertThat(streamOpenStarted.await(5, TimeUnit.SECONDS), is(true));
        connection.close(ConnectionOutcome.NORMAL);
        completeStreamOpen.countDown();
        StreamObservation stream = opening.get(5, TimeUnit.SECONDS);
        stream.close(COMPLETED);

        assertThat(streamClosed.get(), is(1));
        assertThat(connectionClosed.get(), is(1));
        assertThat(events, is(List.of("stream-open", "stream-close", "connection-close")));
    }

    @Test
    void concurrentConnectionCloseAndHandshakeOpenCompleteExactlyOnce() throws Exception {
        CountDownLatch handshakeOpenStarted = new CountDownLatch(1);
        CountDownLatch completeHandshakeOpen = new CountDownLatch(1);
        AtomicInteger handshakeClosed = new AtomicInteger();
        AtomicInteger connectionClosed = new AtomicInteger();
        List<String> events = new CopyOnWriteArrayList<>();
        HttpTransportObserver observer = (role, transport, handshake) -> new ConnectionObservation() {
            @Override
            public HandshakeObservation handshakeStarted() {
                events.add("handshake-open");
                handshakeOpenStarted.countDown();
                try {
                    if (!completeHandshakeOpen.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to complete handshake open");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while opening handshake", e);
                }
                return outcome -> {
                    handshakeClosed.incrementAndGet();
                    events.add("handshake-close");
                };
            }

            @Override
            public void protocolSelected(String protocol) {
            }

            @Override
            public StreamObservation streamOpened(Direction direction, Initiator initiator) {
                return StreamObservation.noop();
            }

            @Override
            public void close(ConnectionOutcome outcome) {
                connectionClosed.incrementAndGet();
                events.add("connection-close");
            }
        };
        ConnectionObservation connection = HttpTransportObserver.compose(List.of(observer))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        CompletableFuture<HandshakeObservation> opening = CompletableFuture.supplyAsync(connection::handshakeStarted);

        assertThat(handshakeOpenStarted.await(5, TimeUnit.SECONDS), is(true));
        connection.close(ConnectionOutcome.NORMAL);
        completeHandshakeOpen.countDown();
        HandshakeObservation handshake = opening.get(5, TimeUnit.SECONDS);
        handshake.close(SUCCESS);

        assertThat(handshakeClosed.get(), is(1));
        assertThat(connectionClosed.get(), is(1));
        assertThat(events, is(List.of("handshake-open", "handshake-close", "connection-close")));
    }

    @Test
    void concurrentStreamOpenCallbacksAreNotSerialized() throws Exception {
        CountDownLatch bothStreamsStarted = new CountDownLatch(2);
        CountDownLatch completeStreams = new CountDownLatch(1);
        AtomicInteger activeCallbacks = new AtomicInteger();
        AtomicInteger maximumActiveCallbacks = new AtomicInteger();
        HttpTransportObserver observer = (role, transport, handshake) -> new ConnectionObservation() {
            @Override
            public HandshakeObservation handshakeStarted() {
                return HandshakeObservation.noop();
            }

            @Override
            public void protocolSelected(String protocol) {
            }

            @Override
            public StreamObservation streamOpened(Direction direction, Initiator initiator) {
                int active = activeCallbacks.incrementAndGet();
                maximumActiveCallbacks.accumulateAndGet(active, Math::max);
                bothStreamsStarted.countDown();
                try {
                    if (!completeStreams.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to complete stream callbacks");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while opening stream", e);
                } finally {
                    activeCallbacks.decrementAndGet();
                }
                return StreamObservation.noop();
            }

            @Override
            public void close(ConnectionOutcome outcome) {
            }
        };
        ConnectionObservation connection = HttpTransportObserver.compose(List.of(observer))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        boolean concurrent;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<StreamObservation> first = CompletableFuture.supplyAsync(
                    () -> connection.streamOpened(BIDIRECTIONAL, REMOTE), executor);
            CompletableFuture<StreamObservation> second = CompletableFuture.supplyAsync(
                    () -> connection.streamOpened(BIDIRECTIONAL, REMOTE), executor);

            try {
                concurrent = bothStreamsStarted.await(5, TimeUnit.SECONDS);
            } finally {
                completeStreams.countDown();
            }
            first.get(5, TimeUnit.SECONDS).close(COMPLETED);
            second.get(5, TimeUnit.SECONDS).close(COMPLETED);
        }
        connection.close(ConnectionOutcome.NORMAL);

        assertThat(concurrent, is(true));
        assertThat(maximumActiveCallbacks.get(), is(2));
    }
}
