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

package io.helidon.webserver.http2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Method;
import io.helidon.http.RequestException;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.ConnectionFlowControl;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ConnectionWriter;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.http2.spi.SubProtocolResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http2ServerStreamObservationTest {
    private static final int STREAM_ID = 1;

    @Test
    void responseWaitsForEmptyRequestEnd() {
        var fixture = fixture();
        fixture.stream().headers(requestHeaders(), false);

        fixture.stream().writeHeaders(responseHeaders(), true);
        assertThat(fixture.outcomes(), is(List.of()));

        fixture.stream().data(dataHeader(0, true), BufferData.empty(), true);
        assertThat(fixture.outcomes(), is(List.of(StreamOutcome.COMPLETED)));
    }

    @Test
    void responseWaitsForNonemptyRequestEnd() {
        var fixture = fixture();
        fixture.stream().headers(requestHeaders(), false);

        fixture.stream().writeHeaders(responseHeaders(), true);
        assertThat(fixture.outcomes(), is(List.of()));

        fixture.stream().data(dataHeader(1, true), BufferData.create(new byte[1]), true);
        assertThat(fixture.outcomes(), is(List.of(StreamOutcome.COMPLETED)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void terminalResponseWaitsForSuccessfulWriteCallback(boolean initiallyRemoteEnded) {
        Http2ConnectionWriter writer = mock(Http2ConnectionWriter.class);
        AtomicReference<Runnable> terminalWrite = new AtomicReference<>();
        when(writer.writeHeaders(any(), anyInt(), any(), any(FlowControl.Outbound.class), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    terminalWrite.set(invocation.getArgument(4));
                    return 0;
                });
        var fixture = fixture(writer, List.of(), initiallyRemoteEnded);
        fixture.stream().headers(requestHeaders(), true);

        fixture.stream().writeHeaders(responseHeaders(), true);
        assertThat(fixture.outcomes(), is(List.of()));

        terminalWrite.get().run();
        assertThat(fixture.outcomes(), is(List.of(StreamOutcome.COMPLETED)));
    }

    @Test
    void initialRequestEndStillValidatesContentLength() {
        var fixture = fixture(mock(Http2StreamWriter.class), List.of(), true);
        Http2Headers headers = Http2Headers.create(WritableHeaders.create().add(HeaderNames.CONTENT_LENGTH, "1"));

        fixture.stream().headers(headers, true);

        assertThat(fixture.outcomes(), is(List.of(StreamOutcome.REJECTED)));
        assertThat(fixture.stream().streamState(), is(Http2StreamState.CLOSED));
    }

    @Test
    void asynchronousSubProtocolCompletesOnlyAfterResponseWrite() {
        var fixture = fixture(mock(Http2StreamWriter.class), List.of(selector(completedHandler())));
        fixture.stream().headers(requestHeaders(), true);

        fixture.stream().run();
        assertThat(fixture.outcomes(), is(List.of()));

        fixture.stream().writeHeaders(responseHeaders(),
                                      STREAM_ID,
                                      Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                      fixture.stream().flowControl().outbound());
        assertThat(fixture.outcomes(), is(List.of(StreamOutcome.COMPLETED)));
    }

    @Test
    void remoteResetBeforeApplicationStartsIsReset() {
        var fixture = fixture();
        fixture.stream().headers(requestHeaders(), false);

        fixture.stream().rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));
        fixture.stream().rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));

        assertThat(fixture.outcomes(), is(List.of(StreamOutcome.RESET)));
    }

    @Test
    void localResetBeforeApplicationStartsIsRejected() {
        var fixture = fixture();
        fixture.stream().headers(requestHeaders(), false);

        fixture.stream().write(resetFrame());

        assertThat(fixture.outcomes(), is(List.of(StreamOutcome.REJECTED)));
    }

    @Test
    void localResetAfterApplicationStartsIsReset() {
        var fixture = fixture(mock(Http2StreamWriter.class), List.of(selector(completedHandler())));
        fixture.stream().headers(requestHeaders(), true);
        fixture.stream().run();

        fixture.stream().write(resetFrame());

        assertThat(fixture.outcomes(), is(List.of(StreamOutcome.RESET)));
    }

    @Test
    void streamExceptionAfterApplicationStartsIsReset() {
        var handler = completedHandler();
        doThrow(new Http2Exception(Http2ErrorCode.FLOW_CONTROL, "Flow control failed")).when(handler).init();
        var fixture = fixture(mock(Http2StreamWriter.class), List.of(selector(handler)));
        fixture.stream().headers(requestHeaders(), true);

        fixture.stream().run();

        assertThat(fixture.outcomes(), is(List.of(StreamOutcome.RESET)));
    }

    @Test
    void unhandledApplicationFailureIsError() {
        var handler = completedHandler();
        var failure = new IllegalStateException("Application failed");
        doThrow(failure).when(handler).init();
        var fixture = fixture(mock(Http2StreamWriter.class), List.of(selector(handler)));
        fixture.stream().headers(requestHeaders(), true);

        assertThat(assertThrows(IllegalStateException.class, fixture.stream()::run), sameInstance(failure));
        assertThat(fixture.outcomes(), is(List.of(StreamOutcome.ERROR)));
    }

    @Test
    void directResponseBeforeApplicationStartsIsRejected() {
        var fixture = fixture();
        fixture.stream().prologue(prologue(Method.CONNECT));
        fixture.stream().headers(requestHeaders(), true);

        fixture.stream().run();

        assertThat(fixture.outcomes(), is(List.of(StreamOutcome.REJECTED)));
    }

    @Test
    void requestFailureAfterApplicationStartsIsError() {
        var handler = completedHandler();
        doThrow(RequestException.builder().status(Status.BAD_REQUEST_400).message("Invalid request").build())
                .when(handler).init();
        var fixture = fixture(mock(Http2StreamWriter.class), List.of(selector(handler)));
        fixture.stream().headers(requestHeaders(), true);

        fixture.stream().run();

        assertThat(fixture.outcomes(), is(List.of(StreamOutcome.ERROR)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedTerminalDataWriteCannotCompleteDuringCleanup(boolean initiallyRemoteEnded) {
        Http2StreamWriter writer = mock(Http2StreamWriter.class);
        doThrow(new UncheckedIOException(new IOException("Write failed")))
                .when(writer).writeData(any(), any());
        var fixture = fixture(writer, List.of(), initiallyRemoteEnded);
        fixture.stream().headers(requestHeaders(), true);

        assertThrows(ServerConnectionException.class,
                     () -> fixture.stream().writeHeadersWithData(responseHeaders(), 1, BufferData.create(new byte[1]), true));

        assertThat(fixture.outcomes(), is(List.of(StreamOutcome.ERROR)));
    }

    @Test
    void asynchronousSubProtocolWriteFailureIsError() {
        Http2StreamWriter writer = mock(Http2StreamWriter.class);
        var failure = new UncheckedIOException(new IOException("Write failed"));
        doThrow(failure).when(writer).writeData(any(), any());
        var fixture = fixture(writer, List.of(selector(completedHandler())));
        fixture.stream().headers(requestHeaders(), true);
        fixture.stream().run();

        assertThat(assertThrows(UncheckedIOException.class,
                                () -> fixture.stream().writeData(new Http2FrameData(dataHeader(1, false),
                                                                                    BufferData.create(new byte[1])),
                                                                  fixture.stream().flowControl().outbound())),
                   sameInstance(failure));

        assertThat(fixture.outcomes(), is(List.of(StreamOutcome.ERROR)));
    }

    @Test
    void connectionAbortLeavesPendingObservationToConnectionOwner() {
        var fixture = fixture();
        fixture.stream().headers(requestHeaders(), false);

        fixture.stream().abortConnection();
        fixture.stream().run();

        assertThat(fixture.outcomes(), is(List.of()));
    }

    private static Fixture fixture() {
        return fixture(mock(Http2StreamWriter.class), List.of());
    }

    private static Fixture fixture(Http2StreamWriter writer, List<Http2SubProtocolSelector> selectors) {
        return fixture(writer, selectors, false);
    }

    private static Fixture fixture(Http2StreamWriter writer,
                                   List<Http2SubProtocolSelector> selectors,
                                   boolean initiallyRemoteEnded) {
        var config = Http2Config.create();
        var listenerContext = mock(ListenerContext.class);
        when(listenerContext.config()).thenReturn(ListenerConfig.create());
        when(listenerContext.directHandlers()).thenReturn(DirectHandlers.create());
        var context = mock(ConnectionContext.class);
        when(context.router()).thenReturn(Router.empty());
        when(context.listenerContext()).thenReturn(listenerContext);
        when(context.sniContext()).thenReturn(Optional.empty());
        var flowControl = ConnectionFlowControl.serverBuilder((_, _) -> { })
                .initialWindowSize(config.initialWindowSize())
                .maxFrameSize(config.maxFrameSize())
                .build();
        var streams = new Http2ConnectionStreams();
        var stream = new Http2ServerStream(context,
                                           streams,
                                           new Http2StreamAdmissionGate(),
                                           mock(Http2ServerStream.LocallyResetStreamTracker.class),
                                           HttpRouting.empty(),
                                           config,
                                           selectors,
                                           STREAM_ID,
                                           Http2Settings.builder().build(),
                                           Http2Settings.builder().build(),
                                           writer,
                                           flowControl,
                                           new Http2ServerStream.InboundDataBudget(16, 65536),
                                           new Http2ConnectionChecks(config, mock(Http2Connection.class)));
        streams.put(new Http2Connection.StreamContext(STREAM_ID, config.initialWindowSize(), stream));
        List<StreamOutcome> outcomes = new ArrayList<>();
        var observation = mock(ConnectionObservation.class);
        when(observation.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE)).thenReturn(outcomes::add);
        stream.observe(new Http2TransportObservation(observation), initiallyRemoteEnded);
        stream.prologue(prologue(Method.GET));
        return new Fixture(stream, outcomes);
    }

    private static Http2SubProtocolSelector.SubProtocolHandler completedHandler() {
        var handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
        when(handler.streamState()).thenReturn(Http2StreamState.CLOSED);
        return handler;
    }

    private static Http2SubProtocolSelector selector(Http2SubProtocolSelector.SubProtocolHandler handler) {
        return (_, _, _, _, _, _, _, _, _, _) -> new SubProtocolResult(true, handler);
    }

    private static HttpPrologue prologue(Method method) {
        return HttpPrologue.create(Http2Connection.FULL_PROTOCOL,
                                   Http2Connection.PROTOCOL,
                                   Http2Connection.PROTOCOL_VERSION,
                                   method,
                                   "/",
                                   false);
    }

    private static Http2Headers requestHeaders() {
        return Http2Headers.create(WritableHeaders.create()
                                           .add(Http2Headers.METHOD_NAME, Method.GET.text())
                                           .add(Http2Headers.PATH_NAME, "/")
                                           .add(Http2Headers.SCHEME_NAME, "http")
                                           .add(Http2Headers.AUTHORITY_NAME, "localhost"));
    }

    private static Http2Headers responseHeaders() {
        return Http2Headers.create(WritableHeaders.create()).status(Status.OK_200);
    }

    private static Http2FrameHeader dataHeader(int size, boolean endOfStream) {
        return Http2FrameHeader.create(size,
                                        Http2FrameTypes.DATA,
                                        Http2Flag.DataFlags.create(endOfStream ? Http2Flag.END_OF_STREAM : 0),
                                        STREAM_ID);
    }

    private static Http2FrameData resetFrame() {
        return new Http2RstStream(Http2ErrorCode.CANCEL).toFrameData(Http2Settings.builder().build(),
                                                                  STREAM_ID,
                                                                  Http2Flag.NoFlags.create());
    }

    private record Fixture(Http2ServerStream stream, List<StreamOutcome> outcomes) {
    }
}
