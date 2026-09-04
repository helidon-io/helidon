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

import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.QuicTransportErrors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.http.HttpTransportObserver.Handshake.QUIC_TLS;
import static io.helidon.http.HttpTransportObserver.HandshakeOutcome.CANCELLED;
import static io.helidon.http.HttpTransportObserver.HandshakeOutcome.FAILURE;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_3;
import static io.helidon.http.HttpTransportObserver.Role.CLIENT;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_QUIC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class QuicClientObserverTest {
    private final CompletableFuture<QuicTermination> terminationFuture = new CompletableFuture<>();
    private final HttpTransportObserver transportObserver = mock(HttpTransportObserver.class);
    private final ConnectionObservation connectionObservation = mock(ConnectionObservation.class);
    private final QuicConnection connection = mock(QuicConnection.class);
    private final QuicClientObserver observer = new QuicClientObserver(transportObserver);

    @BeforeEach
    void setUp() {
        when(transportObserver.connectionOpened(CLIENT, TRANSPORT_QUIC, QUIC_TLS)).thenReturn(connectionObservation);
        when(connection.whenTerminated()).thenReturn(terminationFuture);
    }

    @Test
    void observesApplicationNoErrorLocalClose() {
        QuicTermination termination = connectionClose(QuicTermination.Origin.LOCAL,
                                                       QuicTermination.Layer.APPLICATION,
                                                       Http3ErrorCode.NO_ERROR.code());

        observer.connectionCreated(connection);

        ConnectionObservation observation = observer.observation(connection);
        assertThat(observation, not(sameInstance(connectionObservation)));
        observation.protocolSelected(PROTOCOL_HTTP_3);
        terminationFuture.complete(termination);

        verify(transportObserver).connectionOpened(CLIENT, TRANSPORT_QUIC, QUIC_TLS);
        verify(connectionObservation).protocolSelected(PROTOCOL_HTTP_3);
        verify(connectionObservation).close(ConnectionOutcome.LOCAL_CLOSE);
        assertThat(QuicClientObserver.classifyTermination(termination, null).openStreamOutcome(),
                   equalTo(StreamOutcome.CANCELLED));
    }

    @Test
    void observesTransportNoErrorPeerClose() {
        QuicTermination termination = connectionClose(QuicTermination.Origin.PEER,
                                                       QuicTermination.Layer.TRANSPORT,
                                                       QuicTransportErrors.NO_ERROR.code());

        observer.connectionCreated(connection);
        terminationFuture.complete(termination);

        verify(connectionObservation).close(ConnectionOutcome.REMOTE_CLOSE);
        assertThat(QuicClientObserver.classifyTermination(termination, null).openStreamOutcome(),
                   equalTo(StreamOutcome.CANCELLED));
    }

    @Test
    void mapsApplicationCodeZeroToError() {
        QuicTermination termination = connectionClose(QuicTermination.Origin.LOCAL,
                                                       QuicTermination.Layer.APPLICATION,
                                                       QuicTransportErrors.NO_ERROR.code());

        observer.connectionCreated(connection);
        terminationFuture.complete(termination);

        verify(connectionObservation).close(ConnectionOutcome.ERROR);
    }

    @Test
    void rejectsHttp3NoErrorWithFailureCause() {
        QuicTermination termination = connectionClose(QuicTermination.Origin.LOCAL,
                                                       QuicTermination.Layer.APPLICATION,
                                                       Http3ErrorCode.NO_ERROR.code());
        when(termination.cause()).thenReturn(Optional.of(new IllegalStateException("test close failure")));

        observer.connectionCreated(connection);
        terminationFuture.complete(termination);

        verify(connectionObservation).close(ConnectionOutcome.ERROR);
    }

    @Test
    void observesStatelessResetAsRemoteClose() {
        QuicTermination termination = mock(QuicTermination.class);
        when(termination.kind()).thenReturn(QuicTermination.Kind.STATELESS_RESET);

        observer.connectionCreated(connection);
        terminationFuture.complete(termination);

        verify(connectionObservation).close(ConnectionOutcome.REMOTE_CLOSE);
    }

    @Test
    void observesSilentTerminationAsError() {
        QuicTermination termination = mock(QuicTermination.class);
        when(termination.kind()).thenReturn(QuicTermination.Kind.SILENT);

        observer.connectionCreated(connection);
        terminationFuture.complete(termination);

        verify(connectionObservation).close(ConnectionOutcome.ERROR);
    }

    @Test
    void observesExceptionalTerminationAsError() {
        observer.connectionCreated(connection);
        terminationFuture.completeExceptionally(new IllegalStateException("test cleanup failure"));

        verify(connectionObservation).close(ConnectionOutcome.ERROR);
    }

    @Test
    void delaysForcedTimeoutOutcomeUntilTransportTermination() {
        QuicTermination termination = mock(QuicTermination.class);
        when(termination.kind()).thenReturn(QuicTermination.Kind.SILENT);

        observer.connectionCreated(connection);
        observer.terminalOutcome(connection, ConnectionOutcome.TIMEOUT);

        verify(connectionObservation, never()).close(ConnectionOutcome.TIMEOUT);
        terminationFuture.complete(termination);
        verify(connectionObservation).close(ConnectionOutcome.TIMEOUT);
    }

    @Test
    void cancelsHandshakeInterruptedBySelectedLocalClose() {
        QuicTermination termination = connectionClose(QuicTermination.Origin.LOCAL,
                                                       QuicTermination.Layer.APPLICATION,
                                                       Http3ErrorCode.NO_ERROR.code());
        when(connection.termination()).thenReturn(Optional.of(termination));

        assertThat(QuicClientObserver.handshakeFailureOutcome(connection), equalTo(CANCELLED));
    }

    @Test
    void failsHandshakeWithoutSelectedLocalClose() {
        QuicTermination peerClose = connectionClose(QuicTermination.Origin.PEER,
                                                    QuicTermination.Layer.APPLICATION,
                                                    Http3ErrorCode.NO_ERROR.code());
        when(connection.termination()).thenReturn(Optional.of(peerClose));
        assertThat(QuicClientObserver.handshakeFailureOutcome(connection), equalTo(FAILURE));

        when(connection.termination()).thenReturn(Optional.empty());
        assertThat(QuicClientObserver.handshakeFailureOutcome(connection), equalTo(FAILURE));
    }

    @Test
    void closesObservationWhenTerminationRegistrationFails() {
        IllegalStateException failure = new IllegalStateException("test registration failure");
        when(connection.whenTerminated()).thenThrow(failure);

        assertThat(assertThrows(IllegalStateException.class, () -> observer.connectionCreated(connection)),
                   sameInstance(failure));

        verify(connectionObservation).close(ConnectionOutcome.ERROR);
        assertThat(observer.observation(connection), sameInstance(ConnectionObservation.noop()));
    }

    @Test
    void skipsConnectionWorkForNoopObserver() {
        QuicClientObserver noopObserver = new QuicClientObserver(HttpTransportObserver.noop());

        noopObserver.connectionCreated(connection);

        verifyNoMoreInteractions(connection);
        verify(transportObserver, never()).connectionOpened(CLIENT, TRANSPORT_QUIC, QUIC_TLS);
        assertThat(noopObserver.observation(connection), sameInstance(ConnectionObservation.noop()));
    }

    private static QuicTermination connectionClose(QuicTermination.Origin origin,
                                                   QuicTermination.Layer layer,
                                                   long errorCode) {
        QuicTermination termination = mock(QuicTermination.class);
        when(termination.kind()).thenReturn(QuicTermination.Kind.CONNECTION_CLOSE);
        when(termination.origin()).thenReturn(origin);
        when(termination.layer()).thenReturn(layer);
        when(termination.errorCode()).thenReturn(OptionalLong.of(errorCode));
        when(termination.cause()).thenReturn(Optional.empty());
        return termination;
    }
}
