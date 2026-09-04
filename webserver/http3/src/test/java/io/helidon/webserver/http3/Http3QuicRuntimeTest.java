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

package io.helidon.webserver.http3;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.SocketOptions;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicRemoteStreamRegistration;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.TransportBindingContext;
import io.helidon.webserver.spi.TransportBinding;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_3;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http3QuicRuntimeTest {

    @Test
    void reportsMinimumPeerUniStreams() {
        assertThat(runtime().minimumPeerUniStreams(), equalTo(Http3Protocol.MINIMUM_PEER_UNI_STREAMS));
    }

    @Test
    void rejectsNullGracePeriodBeforeStartingShutdown() {
        Http3QuicRuntime runtime = runtime();

        assertThrows(NullPointerException.class, () -> runtime.closeGracefully(null));

        assertThat(runtime.closeGracefully(Duration.ZERO), equalTo(TransportBinding.ShutdownResult.GRACEFUL));
    }

    @Test
    void reportsForcedWhenConnectionCleanupMissesDeadline() {
        Http3QuicRuntime runtime = runtime();
        QuicConnection connection = mock(QuicConnection.class);
        CompletableFuture<QuicTermination> termination = new CompletableFuture<>();
        when(connection.whenTerminated()).thenReturn(termination);
        when(connection.remotePeer()).thenReturn(mock(PeerInfo.class));
        when(connection.addRemoteStreamListener(any())).thenReturn(mock(QuicRemoteStreamRegistration.class));
        when(connection.openNewLocalUniStream(any())).thenReturn(new CompletableFuture<QuicSenderStream>());

        runtime.accept(connection, ConnectionObservation.noop());

        assertThat(runtime.closeGracefully(Duration.ZERO), equalTo(TransportBinding.ShutdownResult.FORCED));

        termination.completeExceptionally(new IllegalStateException("test termination"));
    }

    @Test
    void observesFacadeTerminationAndReleasesConnection() {
        Http3QuicRuntime runtime = runtime();
        QuicConnection connection = mock(QuicConnection.class);
        ConnectionObservation observation = mock(ConnectionObservation.class);
        CompletableFuture<QuicTermination> termination = new CompletableFuture<>();
        QuicRemoteStreamRegistration registration = mock(QuicRemoteStreamRegistration.class);
        when(connection.whenTerminated()).thenReturn(termination);
        when(connection.remotePeer()).thenReturn(mock(PeerInfo.class));
        when(connection.addRemoteStreamListener(any())).thenReturn(registration);
        when(connection.openNewLocalUniStream(any())).thenReturn(new CompletableFuture<QuicSenderStream>());

        runtime.accept(connection, observation);
        verify(connection, times(1)).whenTerminated();
        InOrder selectionOrder = inOrder(observation, connection);
        selectionOrder.verify(observation).protocolSelected(PROTOCOL_HTTP_3);
        selectionOrder.verify(connection).whenTerminated();
        selectionOrder.verify(connection).addRemoteStreamListener(any());

        termination.completeExceptionally(new IllegalStateException("test termination"));

        verify(registration, times(1)).close();
    }

    @Test
    void classifiesHttp3NormalTerminationCodes() {
        Http3QuicRuntime runtime = runtime();
        QuicTermination http3NoError = mock(QuicTermination.class);
        when(http3NoError.kind()).thenReturn(QuicTermination.Kind.CONNECTION_CLOSE);
        when(http3NoError.layer()).thenReturn(QuicTermination.Layer.APPLICATION);
        when(http3NoError.cause()).thenReturn(Optional.empty());
        when(http3NoError.errorCode()).thenReturn(OptionalLong.of(Http3ErrorCode.NO_ERROR.code()));

        QuicTermination applicationZero = mock(QuicTermination.class);
        when(applicationZero.kind()).thenReturn(QuicTermination.Kind.CONNECTION_CLOSE);
        when(applicationZero.layer()).thenReturn(QuicTermination.Layer.APPLICATION);
        when(applicationZero.cause()).thenReturn(Optional.empty());
        when(applicationZero.errorCode()).thenReturn(OptionalLong.of(0));

        QuicTermination transportNoError = mock(QuicTermination.class);
        when(transportNoError.kind()).thenReturn(QuicTermination.Kind.CONNECTION_CLOSE);
        when(transportNoError.layer()).thenReturn(QuicTermination.Layer.TRANSPORT);
        when(transportNoError.cause()).thenReturn(Optional.empty());
        when(transportNoError.errorCode()).thenReturn(OptionalLong.of(0));

        QuicTermination failed = mock(QuicTermination.class);
        when(failed.kind()).thenReturn(QuicTermination.Kind.CONNECTION_CLOSE);
        when(failed.layer()).thenReturn(QuicTermination.Layer.APPLICATION);
        when(failed.cause()).thenReturn(Optional.of(new IllegalStateException("test failure")));
        when(failed.errorCode()).thenReturn(OptionalLong.of(Http3ErrorCode.NO_ERROR.code()));

        assertThat(runtime.isNormalTermination(http3NoError), equalTo(true));
        assertThat(runtime.isNormalTermination(applicationZero), equalTo(false));
        assertThat(runtime.isNormalTermination(transportNoError), equalTo(true));
        assertThat(runtime.isNormalTermination(failed), equalTo(false));
    }

    @Test
    void defensivelyRejectsInvalidRuntimeSettings() {
        Http3Config invalidConfig = mock(Http3Config.class, delegatesTo(Http3Config.create()));
        when(invalidConfig.maxFieldSectionSize())
                .thenReturn(VariableLengthEncoder.MAX_ENCODED_INTEGER + 1);

        assertThrows(IllegalArgumentException.class, () -> runtime(invalidConfig));
    }

    private static Http3QuicRuntime runtime() {
        return runtime(Http3Config.create());
    }

    private static Http3QuicRuntime runtime(Http3Config config) {
        TransportBindingContext bindingContext = mock(TransportBindingContext.class);
        ListenerContext listenerContext = mock(ListenerContext.class);
        ListenerConfig listenerConfig = mock(ListenerConfig.class);
        when(bindingContext.listenerContext()).thenReturn(listenerContext);
        when(bindingContext.router()).thenReturn(mock(Router.class));
        when(bindingContext.requestLimit()).thenReturn(FixedLimit.create());
        when(listenerContext.config()).thenReturn(listenerConfig);
        when(listenerContext.executor()).thenReturn(mock(ExecutorService.class));
        when(listenerConfig.name()).thenReturn("test");
        when(listenerConfig.connectionOptions()).thenReturn(SocketOptions.create());
        return new Http3QuicRuntime(bindingContext, config);
    }

}
