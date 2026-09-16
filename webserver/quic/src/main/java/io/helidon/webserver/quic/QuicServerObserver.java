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

package io.helidon.webserver.quic;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.HandshakeOutcome;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicServerRuntime;
import io.helidon.quic.QuicTermination;

import static io.helidon.http.HttpTransportObserver.Handshake.QUIC_TLS;
import static io.helidon.http.HttpTransportObserver.Role.SERVER;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_QUIC;
import static java.lang.System.Logger.Level.WARNING;

final class QuicServerObserver implements QuicServerRuntime.Observer {
    private static final System.Logger LOGGER = System.getLogger(QuicServerObserver.class.getName());

    private final ConcurrentMap<QuicConnection, ObservedConnection> connections = new ConcurrentHashMap<>();
    private final HttpTransportObserver observer;
    private final BiPredicate<QuicConnection, QuicTermination> normalTermination;
    private final boolean enabled;

    QuicServerObserver(HttpTransportObserver observer,
                       BiPredicate<QuicConnection, QuicTermination> normalTermination) {
        this.observer = Objects.requireNonNull(observer, "observer");
        this.normalTermination = Objects.requireNonNull(normalTermination, "normalTermination");
        this.enabled = observer != HttpTransportObserver.noop();
    }

    @Override
    public void connectionCreated(QuicConnection connection) {
        if (!enabled) {
            return;
        }
        ConnectionObservation connectionObservation = ConnectionObservation.sequential(Objects.requireNonNull(
                observer.connectionOpened(SERVER, TRANSPORT_QUIC, QUIC_TLS),
                "connection observation"));
        HandshakeObservation handshakeObservation;
        try {
            handshakeObservation = Objects.requireNonNull(connectionObservation.handshakeStarted(),
                                                          "handshake observation");
        } catch (RuntimeException | Error failure) {
            try {
                connectionObservation.close(ConnectionOutcome.ERROR);
            } catch (Throwable closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        ObservedConnection created = new ObservedConnection(connectionObservation, handshakeObservation);
        ObservedConnection existing = connections.putIfAbsent(connection, created);
        if (existing != null) {
            closeObserved(created, ConnectionOutcome.ERROR);
            return;
        }
        try {
            connection.whenTerminated()
                    .whenComplete((termination, failure) -> connectionTerminated(connection,
                                                                                 termination,
                                                                                 failure,
                                                                                 ConnectionOutcome.ERROR));
        } catch (RuntimeException | Error failure) {
            if (connections.remove(connection, created)) {
                closeObserved(created, ConnectionOutcome.ERROR);
            }
            throw failure;
        }
    }

    @Override
    public void handshakeSucceeded(QuicConnection connection) {
        ObservedConnection observed = connections.get(connection);
        if (observed != null && observed.handshakeCompleted.compareAndSet(false, true)) {
            observed.handshakeObservation.close(HandshakeOutcome.SUCCESS);
        }
    }

    void handoff(QuicConnection connection,
                 BiConsumer<QuicConnection, ConnectionObservation> consumer) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(consumer, "consumer");
        ObservedConnection observed = connections.get(connection);
        consumer.accept(connection, observed == null ? ConnectionObservation.noop() : observed.connectionObservation);
    }

    void closeOutstanding(ConnectionOutcome fallback) {
        Objects.requireNonNull(fallback, "fallback");
        connections.forEach((connection, observed) -> {
            if (connections.remove(connection, observed)) {
                closeObserved(observed, fallback);
            }
        });
    }

    private static void closeObserved(ObservedConnection observed, ConnectionOutcome outcome) {
        try {
            observed.connectionObservation.close(outcome);
        } catch (Throwable failure) {
            LOGGER.log(WARNING, "HTTP transport observation failed during QUIC connection close", failure);
        }
    }

    private void connectionTerminated(QuicConnection connection,
                                      QuicTermination termination,
                                      Throwable failure,
                                      ConnectionOutcome fallback) {
        ObservedConnection observed = connections.remove(connection);
        if (observed == null) {
            return;
        }
        ConnectionOutcome outcome = fallback;
        if (failure != null) {
            outcome = ConnectionOutcome.ERROR;
        } else if (termination != null) {
            try {
                if (termination.kind() == QuicTermination.Kind.STATELESS_RESET) {
                    outcome = ConnectionOutcome.REMOTE_CLOSE;
                } else if (termination.cause().orElse(null) instanceof TimeoutException) {
                    outcome = ConnectionOutcome.TIMEOUT;
                } else if (normalTermination.test(connection, termination)) {
                    outcome = termination.origin() == QuicTermination.Origin.LOCAL
                            ? ConnectionOutcome.LOCAL_CLOSE
                            : ConnectionOutcome.REMOTE_CLOSE;
                } else {
                    outcome = ConnectionOutcome.ERROR;
                }
            } catch (Throwable classifierFailure) {
                LOGGER.log(WARNING, "QUIC sub-protocol termination classification failed", classifierFailure);
                outcome = ConnectionOutcome.ERROR;
            }
        }
        closeObserved(observed, outcome);
    }

    private static final class ObservedConnection {
        private final ConnectionObservation connectionObservation;
        private final HandshakeObservation handshakeObservation;
        private final AtomicBoolean handshakeCompleted = new AtomicBoolean();

        private ObservedConnection(ConnectionObservation connectionObservation,
                                   HandshakeObservation handshakeObservation) {
            this.connectionObservation = connectionObservation;
            this.handshakeObservation = handshakeObservation;
        }
    }
}
