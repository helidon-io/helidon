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
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
import org.junit.jupiter.params.provider.ValueSource;

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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
    void errorFromConnectionOpenPropagates() {
        AssertionError failure = new AssertionError("probe");
        AtomicInteger connectionOpened = new AtomicInteger();
        HttpTransportObserver failing = (role, transport, handshake) -> {
            throw failure;
        };
        HttpTransportObserver recording = (role, transport, handshake) -> {
            connectionOpened.incrementAndGet();
            return ConnectionObservation.noop();
        };

        AssertionError thrown = assertThrows(AssertionError.class,
                                             () -> HttpTransportObserver.compose(List.of(failing, recording))
                                                     .connectionOpened(SERVER, TRANSPORT_TCP, TLS));

        assertThat(thrown, sameInstance(failure));
        assertThat(connectionOpened.get(), is(0));
    }

    @Test
    void errorFromProtocolSelectionPropagatesAndDrainerRecovers() {
        AssertionError failure = new AssertionError("probe");
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger protocolSelected = new AtomicInteger();
        AtomicInteger connectionClosed = new AtomicInteger();
        HttpTransportObserver failing = (role, transport, handshake) -> new ConnectionObservation() {
            @Override
            public HandshakeObservation handshakeStarted() {
                return HandshakeObservation.noop();
            }

            @Override
            public void protocolSelected(String protocol) {
                if (attempts.getAndIncrement() == 0) {
                    throw failure;
                }
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
                connectionClosed.incrementAndGet();
            }
        };
        ConnectionObservation connection = HttpTransportObserver.compose(List.of(failing, recording))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);

        AssertionError thrown = assertThrows(AssertionError.class,
                                             () -> connection.protocolSelected(PROTOCOL_HTTP_2));

        assertThat(thrown, sameInstance(failure));
        assertThat(protocolSelected.get(), is(0));
        connection.protocolSelected(PROTOCOL_HTTP_3);
        connection.close(ConnectionOutcome.NORMAL);

        assertThat(protocolSelected.get(), is(1));
        assertThat(connectionClosed.get(), is(1));
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
    void connectionCloseCompletesStreamObservationsReturnedAfterClose() throws Exception {
        var events = new ConcurrentLinkedQueue<String>();
        var secondOpening = new CompletableFuture<Void>();
        var completeSecondOpen = new CompletableFuture<Void>();
        HttpTransportObserver first = recordingObserver("first", events,
                                                        () -> outcome -> events.add("first-stream-" + outcome));
        HttpTransportObserver second = recordingObserver("second", events, () -> {
            secondOpening.complete(null);
            completeSecondOpen.join();
            return outcome -> events.add("second-stream-" + outcome);
        });
        ConnectionObservation connection = HttpTransportObserver.compose(List.of(first, second))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        connection.handshakeStarted().close(SUCCESS);
        connection.protocolSelected(PROTOCOL_HTTP_1_1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var streamFuture = executor.submit(() -> connection.streamOpened(BIDIRECTIONAL, REMOTE));
            try {
                secondOpening.get(10, SECONDS);
                var closeFuture = executor.submit(() -> connection.close(ConnectionOutcome.LOCAL_CLOSE));
                closeFuture.get(10, SECONDS);
                assertThat("Connection callback remains deferred until the stream-open callback returns",
                           List.copyOf(events),
                           is(List.of("first-stream-CANCELLED")));
            } finally {
                completeSecondOpen.complete(null);
            }
            StreamObservation stream = streamFuture.get(10, SECONDS);
            stream.close(COMPLETED);
            connection.close(ConnectionOutcome.NORMAL);
        }

        assertThat(List.copyOf(events), is(List.of("first-stream-CANCELLED",
                                                 "second-stream-CANCELLED",
                                                 "first-connection-LOCAL_CLOSE",
                                                 "second-connection-LOCAL_CLOSE")));
    }

    @Test
    void singleObserverClosesLateStreamBeforeConnectionCallback() throws Exception {
        var events = new ConcurrentLinkedQueue<String>();
        var opening = new CompletableFuture<Void>();
        var completeOpen = new CompletableFuture<Void>();
        HttpTransportObserver observer = recordingObserver("observer", events, () -> {
            opening.complete(null);
            completeOpen.join();
            return outcome -> events.add("stream-" + outcome);
        });
        ConnectionObservation connection = HttpTransportObserver.compose(List.of(observer))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var streamFuture = executor.submit(() -> connection.streamOpened(BIDIRECTIONAL, REMOTE));
            try {
                opening.get(10, SECONDS);
                var closeFuture = executor.submit(() -> connection.close(ConnectionOutcome.LOCAL_CLOSE));
                closeFuture.get(10, SECONDS);
                assertThat("The connection callback waits for the only stream-open callback",
                           List.copyOf(events), is(List.of()));
            } finally {
                completeOpen.complete(null);
            }
            StreamObservation stream = streamFuture.get(10, SECONDS);
            stream.close(COMPLETED);
            connection.close(ConnectionOutcome.NORMAL);
        } finally {
            completeOpen.complete(null);
            executor.shutdownNow();
            assertThat("Stream lifecycle tasks terminated", executor.awaitTermination(10, SECONDS), is(true));
        }

        assertThat(List.copyOf(events), is(List.of("stream-CANCELLED", "observer-connection-LOCAL_CLOSE")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void singleObserverEmptyStreamClosePreservesOtherStreams(boolean failOpen) {
        var events = new ConcurrentLinkedQueue<String>();
        var opened = new AtomicInteger();
        HttpTransportObserver observer = recordingObserver("observer", events, () -> {
            if (opened.incrementAndGet() == 1) {
                if (failOpen) {
                    throw new IllegalStateException("stream open");
                }
                return StreamObservation.noop();
            }
            return outcome -> events.add("stream-" + outcome);
        });
        ConnectionObservation connection = HttpTransportObserver.compose(List.of(observer))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        StreamObservation empty = connection.streamOpened(BIDIRECTIONAL, REMOTE);
        StreamObservation pending = connection.streamOpened(BIDIRECTIONAL, REMOTE);

        empty.close(COMPLETED);
        empty.close(CANCELLED);
        connection.close(ConnectionOutcome.LOCAL_CLOSE);
        pending.close(COMPLETED);
        empty.close(COMPLETED);
        connection.close(ConnectionOutcome.NORMAL);

        assertThat(connection.streamOpened(BIDIRECTIONAL, REMOTE), sameInstance(StreamObservation.noop()));
        assertThat(opened.get(), is(2));
        assertThat(List.copyOf(events), is(List.of("stream-CANCELLED", "observer-connection-LOCAL_CLOSE")));
    }

    @Test
    void singleObserverIsolatesStreamCloseFailuresAndFinalizesConnection() {
        var events = new ConcurrentLinkedQueue<String>();
        HttpTransportObserver observer = recordingObserver("observer", events, () -> outcome -> {
            events.add("stream-" + outcome);
            throw new IllegalStateException("stream close");
        });
        ConnectionObservation connection = HttpTransportObserver.compose(List.of(observer))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        StreamObservation completed = connection.streamOpened(BIDIRECTIONAL, REMOTE);
        StreamObservation pending = connection.streamOpened(BIDIRECTIONAL, REMOTE);

        completed.close(COMPLETED);
        completed.close(CANCELLED);
        connection.close(ConnectionOutcome.LOCAL_CLOSE);
        pending.close(COMPLETED);
        connection.close(ConnectionOutcome.NORMAL);

        assertThat(List.copyOf(events), is(List.of("stream-COMPLETED",
                                                 "stream-CANCELLED",
                                                 "observer-connection-LOCAL_CLOSE")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void singleObserverStreamCloseErrorPropagatesAndFinalizesConnection(boolean connectionClose) {
        var events = new ConcurrentLinkedQueue<String>();
        var failure = new AssertionError("stream close");
        HttpTransportObserver observer = recordingObserver("observer", events, () -> outcome -> {
            events.add("stream-" + outcome);
            throw failure;
        });
        ConnectionObservation connection = HttpTransportObserver.compose(List.of(observer))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        StreamObservation stream = connection.streamOpened(BIDIRECTIONAL, REMOTE);

        AssertionError thrown = assertThrows(AssertionError.class, () -> {
            if (connectionClose) {
                connection.close(ConnectionOutcome.LOCAL_CLOSE);
            } else {
                stream.close(COMPLETED);
            }
        });
        connection.close(ConnectionOutcome.LOCAL_CLOSE);
        stream.close(COMPLETED);
        connection.close(ConnectionOutcome.NORMAL);

        assertThat(thrown, sameInstance(failure));
        assertThat(connection.streamOpened(BIDIRECTIONAL, REMOTE), sameInstance(StreamObservation.noop()));
        assertThat(List.copyOf(events), is(List.of("stream-" + (connectionClose ? CANCELLED : COMPLETED),
                                                 "observer-connection-LOCAL_CLOSE")));
    }

    @Test
    void singleObserverReentrantCloseWaitsForStreamCallback() {
        var events = new ConcurrentLinkedQueue<String>();
        var connectionRef = new AtomicReference<ConnectionObservation>();
        var streamRef = new AtomicReference<StreamObservation>();
        HttpTransportObserver observer = recordingObserver("observer", events, () -> outcome -> {
            events.add("stream-start-" + outcome);
            connectionRef.get().close(ConnectionOutcome.LOCAL_CLOSE);
            streamRef.get().close(CANCELLED);
            events.add("stream-end-" + outcome);
        });
        ConnectionObservation connection = HttpTransportObserver.compose(List.of(observer))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        connectionRef.set(connection);
        StreamObservation stream = connection.streamOpened(BIDIRECTIONAL, REMOTE);
        streamRef.set(stream);

        stream.close(COMPLETED);
        stream.close(CANCELLED);
        connection.close(ConnectionOutcome.NORMAL);

        assertThat(List.copyOf(events), is(List.of("stream-start-COMPLETED",
                                                 "stream-end-COMPLETED",
                                                 "observer-connection-LOCAL_CLOSE")));
    }

    @Test
    void connectionCloseCompletesOnlyRemainingOverlappingStreams() {
        var events = new ConcurrentLinkedQueue<String>();
        var identifiers = new AtomicInteger();
        HttpTransportObserver observer = recordingObserver("observer", events, () -> {
            int identifier = identifiers.incrementAndGet();
            return outcome -> events.add(identifier + "-stream-" + outcome);
        });
        ConnectionObservation connection = HttpTransportObserver.compose(List.of(observer))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        List<StreamObservation> streams = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            streams.add(connection.streamOpened(BIDIRECTIONAL, REMOTE));
        }

        streams.get(2).close(COMPLETED);
        streams.get(0).close(COMPLETED);
        streams.get(4).close(COMPLETED);
        streams.get(2).close(CANCELLED);
        streams.add(connection.streamOpened(BIDIRECTIONAL, REMOTE));

        assertThat(List.copyOf(events), is(List.of("3-stream-COMPLETED",
                                                 "1-stream-COMPLETED",
                                                 "5-stream-COMPLETED")));

        connection.close(ConnectionOutcome.LOCAL_CLOSE);
        streams.forEach(stream -> stream.close(COMPLETED));
        connection.close(ConnectionOutcome.NORMAL);

        List<String> completed = List.copyOf(events);
        assertThat("Each stream and the connection close exactly once", completed.size(), is(7));
        assertThat(completed.subList(3, 6), containsInAnyOrder("2-stream-CANCELLED",
                                                            "4-stream-CANCELLED",
                                                            "6-stream-CANCELLED"));
        assertThat("The connection callback follows all stream callbacks",
                   completed.getLast(), is("observer-connection-LOCAL_CLOSE"));
    }

    @Test
    void streamClosePreservesDelegateOrderAndIsolatesFailuresAfterSkippedObservers() {
        var events = new ConcurrentLinkedQueue<String>();
        HttpTransportObserver noop = recordingObserver("noop", events, StreamObservation::noop);
        HttpTransportObserver failingOpen = recordingObserver("failing-open", events, () -> {
            throw new IllegalStateException("stream open");
        });
        HttpTransportObserver failingClose = recordingObserver("failing-close", events, () -> outcome -> {
            events.add("failing-stream-" + outcome);
            throw new IllegalStateException("stream close");
        });
        HttpTransportObserver recording = recordingObserver("recording", events,
                                                            () -> outcome -> events.add("recording-stream-" + outcome));
        ConnectionObservation connection = HttpTransportObserver
                .compose(List.of(noop, failingOpen, failingClose, failingClose, recording))
                .connectionOpened(SERVER, TRANSPORT_TCP, TLS);

        StreamObservation completed = connection.streamOpened(BIDIRECTIONAL, REMOTE);
        completed.close(COMPLETED);
        completed.close(CANCELLED);
        StreamObservation pending = connection.streamOpened(BIDIRECTIONAL, REMOTE);
        connection.close(ConnectionOutcome.LOCAL_CLOSE);
        pending.close(COMPLETED);

        assertThat(List.copyOf(events), is(List.of("failing-stream-COMPLETED",
                                                 "recording-stream-COMPLETED",
                                                 "failing-stream-CANCELLED",
                                                 "recording-stream-CANCELLED",
                                                 "noop-connection-LOCAL_CLOSE",
                                                 "failing-open-connection-LOCAL_CLOSE",
                                                 "failing-close-connection-LOCAL_CLOSE",
                                                 "recording-connection-LOCAL_CLOSE")));
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

    private static HttpTransportObserver recordingObserver(String name,
                                                           Queue<String> events,
                                                           Supplier<StreamObservation> streamSupplier) {
        return (_, _, _) -> new ConnectionObservation() {
            @Override
            public HandshakeObservation handshakeStarted() {
                return HandshakeObservation.noop();
            }

            @Override
            public void protocolSelected(String protocol) {
            }

            @Override
            public StreamObservation streamOpened(Direction direction, Initiator initiator) {
                return streamSupplier.get();
            }

            @Override
            public void close(ConnectionOutcome outcome) {
                events.add(name + "-connection-" + outcome);
            }
        };
    }

}
