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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.HandshakeOutcome;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicTermination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.http.HttpTransportObserver.Handshake.QUIC_TLS;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_3;
import static io.helidon.http.HttpTransportObserver.Role.SERVER;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_QUIC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class QuicServerObserverTest {
    private final CompletableFuture<QuicTermination> terminationFuture = new CompletableFuture<>();
    private final HttpTransportObserver transportObserver = mock(HttpTransportObserver.class);
    private final ConnectionObservation connectionObservation = mock(ConnectionObservation.class);
    private final HandshakeObservation handshakeObservation = mock(HandshakeObservation.class);
    private final QuicConnection connection = mock(QuicConnection.class);
    private final QuicTermination normalTermination = mock(QuicTermination.class);
    private final QuicServerObserver observer = new QuicServerObserver(transportObserver,
                                                                      (_, termination) -> termination == normalTermination);

    @BeforeEach
    void setUp() {
        when(transportObserver.connectionOpened(SERVER, TRANSPORT_QUIC, QUIC_TLS)).thenReturn(connectionObservation);
        when(connectionObservation.handshakeStarted()).thenReturn(handshakeObservation);
        when(connection.whenTerminated()).thenReturn(terminationFuture);
        observer.connectionCreated(connection);
    }

    @Test
    void observesHandshakeSuccessAndLocalCloseExactlyOnce() {
        when(normalTermination.kind()).thenReturn(QuicTermination.Kind.CONNECTION_CLOSE);
        when(normalTermination.origin()).thenReturn(QuicTermination.Origin.LOCAL);

        observer.handshakeSucceeded(connection);
        observer.handshakeSucceeded(connection);
        terminationFuture.complete(normalTermination);

        verify(transportObserver).connectionOpened(SERVER, TRANSPORT_QUIC, QUIC_TLS);
        verify(connectionObservation).handshakeStarted();
        verify(handshakeObservation).close(HandshakeOutcome.SUCCESS);
        verify(connectionObservation).close(ConnectionOutcome.LOCAL_CLOSE);
        verifyNoMoreInteractions(handshakeObservation, connectionObservation);
    }

    @Test
    void handsOffSequentialConnectionObservation() {
        observer.handoff(connection, (_, observation) -> {
            assertThat(observation, not(sameInstance(connectionObservation)));
            observation.protocolSelected(PROTOCOL_HTTP_3);
        });

        verify(connectionObservation).protocolSelected(PROTOCOL_HTTP_3);
    }

    @Test
    void mapsPeerNormalCloseToRemoteClose() {
        when(normalTermination.kind()).thenReturn(QuicTermination.Kind.CONNECTION_CLOSE);
        when(normalTermination.origin()).thenReturn(QuicTermination.Origin.PEER);

        terminationFuture.complete(normalTermination);

        verify(connectionObservation).close(ConnectionOutcome.REMOTE_CLOSE);
    }

    @Test
    void mapsStatelessResetToRemoteClose() {
        QuicTermination termination = mock(QuicTermination.class);
        when(termination.kind()).thenReturn(QuicTermination.Kind.STATELESS_RESET);

        terminationFuture.complete(termination);

        verify(connectionObservation).close(ConnectionOutcome.REMOTE_CLOSE);
    }

    @Test
    void mapsAbnormalTerminationToError() {
        QuicTermination termination = mock(QuicTermination.class);
        when(termination.kind()).thenReturn(QuicTermination.Kind.CONNECTION_CLOSE);

        terminationFuture.complete(termination);

        verify(connectionObservation).close(ConnectionOutcome.ERROR);
    }

    @Test
    void mapsExceptionalTerminationCleanupToError() {
        terminationFuture.completeExceptionally(new IllegalStateException("test cleanup failure"));

        verify(connectionObservation).close(ConnectionOutcome.ERROR);
    }

    @Test
    void mapsHandshakeDeadlineToTimeout() {
        QuicTermination termination = mock(QuicTermination.class);
        when(termination.kind()).thenReturn(QuicTermination.Kind.SILENT);
        when(termination.cause()).thenReturn(Optional.of(new TimeoutException("test handshake timeout")));

        terminationFuture.complete(termination);

        verify(connectionObservation).close(ConnectionOutcome.TIMEOUT);
    }

    @Test
    void closesOutstandingConnectionWithFallbackExactlyOnce() {
        when(normalTermination.kind()).thenReturn(QuicTermination.Kind.CONNECTION_CLOSE);
        when(normalTermination.origin()).thenReturn(QuicTermination.Origin.LOCAL);

        observer.closeOutstanding(ConnectionOutcome.TIMEOUT);
        terminationFuture.complete(normalTermination);

        verify(connectionObservation).handshakeStarted();
        verify(connectionObservation).close(ConnectionOutcome.TIMEOUT);
        verifyNoMoreInteractions(connectionObservation);
    }
}
