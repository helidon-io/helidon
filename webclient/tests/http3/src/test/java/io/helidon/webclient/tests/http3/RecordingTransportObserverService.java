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

package io.helidon.webclient.tests.http3;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webclient.spi.WebClientTransportObserverProvider;

final class RecordingTransportObserverService implements WebClientService, WebClientTransportObserverProvider {
    private final Object identity = new Object();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger registrationsOpened = new AtomicInteger();
    private final AtomicInteger registrationsClosed = new AtomicInteger();
    private final AtomicInteger registrationCompletions = new AtomicInteger();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final List<ConnectionRecord> connections = new CopyOnWriteArrayList<>();

    @Override
    public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
        requests.incrementAndGet();
        return chain.proceed(request);
    }

    @Override
    public String type() {
        return "recording-http-transport-observer";
    }

    @Override
    public Object transportObserverIdentity() {
        return identity;
    }

    @Override
    public Registration openTransportObserver() {
        registrationsOpened.incrementAndGet();
        return new Registration() {
            @Override
            public HttpTransportObserver observer() {
                return RecordingTransportObserverService.this::connectionOpened;
            }

            @Override
            public void close() {
                events.add("registration-close");
                registrationsClosed.incrementAndGet();
            }

            @Override
            public CompletionStage<Void> completion() {
                events.add("registration-completion");
                registrationCompletions.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    int requests() {
        return requests.get();
    }

    int registrationsOpened() {
        return registrationsOpened.get();
    }

    int registrationsClosed() {
        return registrationsClosed.get();
    }

    int registrationCompletions() {
        return registrationCompletions.get();
    }

    List<String> events() {
        return List.copyOf(events);
    }

    List<ConnectionRecord> connections() {
        return List.copyOf(connections);
    }

    private ConnectionObservation connectionOpened(Role role, String transport, Handshake handshake) {
        events.add("connection-open");
        ConnectionRecord connection = new ConnectionRecord(role, transport, handshake, events);
        connections.add(connection);
        return connection;
    }

    static final class ConnectionRecord implements ConnectionObservation {
        private final Role role;
        private final String transport;
        private final Handshake handshake;
        private final List<String> events;
        private final CompletableFuture<HandshakeOutcome> handshakeOutcome = new CompletableFuture<>();
        private final CompletableFuture<ConnectionOutcome> outcome = new CompletableFuture<>();
        private final List<String> protocols = new CopyOnWriteArrayList<>();
        private final List<StreamRecord> streams = new CopyOnWriteArrayList<>();
        private final HandshakeObservation handshakeObservation;

        private ConnectionRecord(Role role, String transport, Handshake handshake, List<String> events) {
            this.role = role;
            this.transport = transport;
            this.handshake = handshake;
            this.events = events;
            this.handshakeObservation = result -> {
                events.add("handshake-" + result);
                handshakeOutcome.complete(result);
            };
        }

        @Override
        public HandshakeObservation handshakeStarted() {
            events.add("handshake-start");
            return handshakeObservation;
        }

        @Override
        public void protocolSelected(String protocol) {
            events.add("protocol-" + protocol);
            protocols.add(protocol);
        }

        @Override
        public StreamObservation streamOpened(Direction direction, Initiator initiator) {
            events.add("stream-open");
            StreamRecord stream = new StreamRecord(direction, initiator, events);
            streams.add(stream);
            return stream::close;
        }

        @Override
        public void close(ConnectionOutcome outcome) {
            events.add("connection-" + outcome);
            this.outcome.complete(outcome);
        }

        Role role() {
            return role;
        }

        String transport() {
            return transport;
        }

        Handshake handshake() {
            return handshake;
        }

        CompletableFuture<HandshakeOutcome> handshakeOutcome() {
            return handshakeOutcome;
        }

        CompletableFuture<ConnectionOutcome> outcome() {
            return outcome;
        }

        List<String> protocols() {
            return List.copyOf(protocols);
        }

        List<StreamRecord> streams() {
            return List.copyOf(streams);
        }
    }

    static final class StreamRecord {
        private final Direction direction;
        private final Initiator initiator;
        private final List<String> events;
        private final CompletableFuture<StreamOutcome> outcome = new CompletableFuture<>();

        private StreamRecord(Direction direction, Initiator initiator, List<String> events) {
            this.direction = direction;
            this.initiator = initiator;
            this.events = events;
        }

        private void close(StreamOutcome outcome) {
            events.add("stream-" + outcome);
            this.outcome.complete(outcome);
        }

        Direction direction() {
            return direction;
        }

        Initiator initiator() {
            return initiator;
        }

        CompletableFuture<StreamOutcome> outcome() {
            return outcome;
        }
    }
}
