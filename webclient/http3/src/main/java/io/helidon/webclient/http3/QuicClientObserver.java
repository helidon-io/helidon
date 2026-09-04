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

package io.helidon.webclient.http3;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.HandshakeOutcome;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.quic.QuicClientRuntime;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.QuicTransportErrors;

import static io.helidon.http.HttpTransportObserver.Handshake.QUIC_TLS;
import static io.helidon.http.HttpTransportObserver.HandshakeOutcome.CANCELLED;
import static io.helidon.http.HttpTransportObserver.HandshakeOutcome.FAILURE;
import static io.helidon.http.HttpTransportObserver.Role.CLIENT;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_QUIC;
import static java.lang.System.Logger.Level.WARNING;

final class QuicClientObserver implements QuicClientRuntime.Observer {
    private static final System.Logger LOGGER = System.getLogger(QuicClientObserver.class.getName());

    private final ConcurrentMap<QuicConnection, ObservedConnection> connections = new ConcurrentHashMap<>();
    private final HttpTransportObserver observer;
    private final boolean enabled;

    QuicClientObserver(HttpTransportObserver observer) {
        this.observer = Objects.requireNonNull(observer, "observer");
        this.enabled = observer != HttpTransportObserver.noop();
    }

    @Override
    public void connectionCreated(QuicConnection connection) {
        if (!enabled) {
            return;
        }
        ObservedConnection created = new ObservedConnection(ConnectionObservation.sequential(Objects.requireNonNull(
                observer.connectionOpened(CLIENT, TRANSPORT_QUIC, QUIC_TLS),
                "connection observation")));
        ObservedConnection existing = connections.putIfAbsent(connection, created);
        if (existing != null) {
            closeObserved(created.observation(), ConnectionOutcome.ERROR);
            return;
        }
        try {
            connection.whenTerminated()
                    .whenComplete((termination, failure) -> connectionTerminated(connection, termination, failure));
        } catch (RuntimeException | Error failure) {
            if (connections.remove(connection, created)) {
                closeObserved(created.observation(), ConnectionOutcome.ERROR);
            }
            throw failure;
        }
    }

    ConnectionObservation observation(QuicConnection connection) {
        ObservedConnection observed = connections.get(Objects.requireNonNull(connection, "connection"));
        return observed == null ? ConnectionObservation.noop() : observed.observation();
    }

    void terminalOutcome(QuicConnection connection, ConnectionOutcome outcome) {
        ObservedConnection observed = connections.get(Objects.requireNonNull(connection, "connection"));
        if (observed != null) {
            observed.terminalOutcome().compareAndSet(null, Objects.requireNonNull(outcome, "outcome"));
        }
    }

    private void connectionTerminated(QuicConnection connection,
                                      QuicTermination termination,
                                      Throwable failure) {
        ObservedConnection observed = connections.remove(connection);
        if (observed == null) {
            return;
        }
        ConnectionOutcome outcome = observed.terminalOutcome().get();
        if (outcome == null) {
            outcome = classifyTermination(termination, failure).connectionOutcome();
        }
        closeObserved(observed.observation(), outcome);
    }

    static TerminationOutcomes classifyTermination(QuicTermination termination, Throwable failure) {
        if (failure != null || termination == null) {
            return TerminationOutcomes.ERROR;
        }
        if (termination.kind() == QuicTermination.Kind.STATELESS_RESET) {
            return TerminationOutcomes.REMOTE_CLOSE;
        }
        boolean normal = termination.cause().isEmpty()
                && termination.kind() == QuicTermination.Kind.CONNECTION_CLOSE
                && ((termination.layer() == QuicTermination.Layer.APPLICATION
                        && termination.errorCode().orElse(-1) == Http3ErrorCode.NO_ERROR.code())
                        || (termination.layer() == QuicTermination.Layer.TRANSPORT
                        && termination.errorCode().orElse(-1) == QuicTransportErrors.NO_ERROR.code()));
        if (!normal) {
            return TerminationOutcomes.ERROR;
        }
        return termination.origin() == QuicTermination.Origin.LOCAL
                ? TerminationOutcomes.LOCAL_CLOSE
                : TerminationOutcomes.REMOTE_CLOSE;
    }

    static HandshakeOutcome handshakeFailureOutcome(QuicConnection connection) {
        return connection.termination()
                .map(termination -> classifyTermination(termination, null).connectionOutcome())
                .filter(ConnectionOutcome.LOCAL_CLOSE::equals)
                .map(_ -> CANCELLED)
                .orElse(FAILURE);
    }

    private static void closeObserved(ConnectionObservation observed, ConnectionOutcome outcome) {
        try {
            observed.close(outcome);
        } catch (Throwable failure) {
            LOGGER.log(WARNING, "HTTP transport observation failed during QUIC client connection close", failure);
        }
    }

    record TerminationOutcomes(ConnectionOutcome connectionOutcome, StreamOutcome openStreamOutcome) {
        private static final TerminationOutcomes LOCAL_CLOSE =
                new TerminationOutcomes(ConnectionOutcome.LOCAL_CLOSE, StreamOutcome.CANCELLED);
        private static final TerminationOutcomes REMOTE_CLOSE =
                new TerminationOutcomes(ConnectionOutcome.REMOTE_CLOSE, StreamOutcome.CANCELLED);
        private static final TerminationOutcomes ERROR =
                new TerminationOutcomes(ConnectionOutcome.ERROR, StreamOutcome.ERROR);
    }

    private record ObservedConnection(ConnectionObservation observation,
                                      AtomicReference<ConnectionOutcome> terminalOutcome) {
        private ObservedConnection(ConnectionObservation observation) {
            this(observation, new AtomicReference<>());
        }
    }
}
