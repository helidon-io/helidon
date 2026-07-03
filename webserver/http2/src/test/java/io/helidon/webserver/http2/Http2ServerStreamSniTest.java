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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketContext;
import io.helidon.common.uri.UriAuthority;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
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
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.SniMatchType;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.http2.spi.SubProtocolResult;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http2ServerStreamSniTest {
    private static final int STREAM_ID = 1;
    private static final Http2ServerStream.LocallyResetStreamTracker NO_OP_RESET_TRACKER = new NoOpResetTracker();
    private static final HttpPrologue PROLOGUE = HttpPrologue.create(Http2Connection.FULL_PROTOCOL,
                                                                     Http2Connection.PROTOCOL,
                                                                     Http2Connection.PROTOCOL_VERSION,
                                                                     Method.GET,
                                                                     "/",
                                                                     true);

    @Test
    void missingAuthorityClosesStream() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingStreamWriter writer = new RecordingStreamWriter();
        Http2ServerStream stream = stream(streams, writer);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority(), false);

        stream.run();
        streams.doMaintenance();

        assertThat(writer.status, is(Status.BAD_REQUEST_400));
        assertThat(writer.rstStreamCount, is(1));
        assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
        assertThat(streams.get(STREAM_ID), is(nullValue()));
        assertDoesNotThrow(stream::checkDataReceivable);
    }

    @Test
    void rejectedContentLengthZeroStreamResetsOpenRemoteSide() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingStreamWriter writer = new RecordingStreamWriter();
        RecordingResetTracker resetTracker = new RecordingResetTracker();
        Http2ServerStream stream = stream(streams, writer, new ArrayList<>(), sniContext(), resetTracker);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority("0"), false);

        stream.run();

        assertThat(writer.rstStreamCount, is(1));
        assertThat(resetTracker.adds, is(1));
        assertThat(resetTracker.localCompletes, is(1));
        assertThat(resetTracker.remoteCompletes, is(0));
        assertDoesNotThrow(stream::checkDataReceivable);
        assertDoesNotThrow(() -> stream.data(Http2FrameHeader.create(0,
                                                                     Http2FrameTypes.DATA,
                                                                     Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                                     STREAM_ID),
                                                   BufferData.empty(),
                                                   true));
        assertThat(resetTracker.remoteCompletes, is(1));
    }

    @Test
    void resetStartingAfterStaleSnapshotStillCompletesRemoteSide() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingStreamWriter writer = new RecordingStreamWriter();
        RecordingResetTracker resetTracker = new RecordingResetTracker();
        Http2ServerStream stream = stream(streams, writer, new ArrayList<>(), sniContext(), resetTracker);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority("2"), false);

        stream.flowControl().inbound().decrementWindowSize(1);
        assertDoesNotThrow(() -> stream.enqueueDataAfterPrecheck(
                Http2FrameHeader.create(1,
                                        Http2FrameTypes.DATA,
                                        Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                        STREAM_ID),
                BufferData.create(new byte[1]),
                true,
                stream::run));

        assertThat(resetTracker.adds, is(1));
        assertThat(resetTracker.streamState.discardedData(), is(1L));
        assertThat(resetTracker.remoteCompletes, is(1));
    }

    @Test
    void rejectedStreamRestoresQueuedDataConnectionFlowControl() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingStreamWriter writer = new RecordingStreamWriter();
        List<WindowUpdate> windowUpdates = new ArrayList<>();
        Http2ServerStream stream = stream(streams, writer, windowUpdates);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        byte[] entity = "hello".getBytes();
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority(String.valueOf(entity.length)), false);
        stream.flowControl().inbound().decrementWindowSize(entity.length);
        stream.data(Http2FrameHeader.create(entity.length,
                                            Http2FrameTypes.DATA,
                                            Http2Flag.DataFlags.create(0),
                                            STREAM_ID),
                    BufferData.create(entity),
                    false);

        stream.run();

        assertThat(windowUpdates, is(List.of(new WindowUpdate(0, entity.length))));
    }

    @Test
    void rejectedStreamRestoresRacingDataConnectionFlowControl() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingStreamWriter writer = new RecordingStreamWriter();
        List<WindowUpdate> windowUpdates = new ArrayList<>();
        Http2ServerStream stream = stream(streams, writer, windowUpdates);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        byte[] entity = "hello".getBytes();
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority(String.valueOf(entity.length)), false);
        stream.checkDataReceivable();
        stream.flowControl().inbound().decrementWindowSize(entity.length);

        stream.run();

        stream.data(Http2FrameHeader.create(entity.length,
                                            Http2FrameTypes.DATA,
                                            Http2Flag.DataFlags.create(0),
                                            STREAM_ID),
                    BufferData.create(entity),
                    false);

        assertThat(windowUpdates, is(List.of(new WindowUpdate(0, entity.length))));
    }

    @Test
    void blockedRejectedStreamRestoresRacingDataConnectionFlowControl() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingConnectionWriter writer = new RecordingConnectionWriter();
        List<WindowUpdate> windowUpdates = new ArrayList<>();
        Http2ServerStream stream = stream(streams, writer, windowUpdates);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        byte[] entity = "hello".getBytes();
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority(String.valueOf(entity.length)), false);
        stream.checkDataReceivable();
        stream.flowControl().inbound().decrementWindowSize(entity.length);

        stream.run();

        assertThat(writer.hasPendingTerminalCallback(), is(true));
        assertDoesNotThrow(stream::checkDataReceivable);
        stream.data(Http2FrameHeader.create(entity.length,
                                            Http2FrameTypes.DATA,
                                            Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                            STREAM_ID),
                    BufferData.create(entity),
                    true);

        assertThat(windowUpdates, is(List.of(new WindowUpdate(0, entity.length))));

        writer.completeTerminalWrite();
        assertThat(writer.rstStreamCount, is(0));
    }

    @Test
    void rejectedStreamDeactivatesBeforeResetWrite() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingConnectionWriter writer = new RecordingConnectionWriter();
        writer.beforeResetWrite = () -> assertThat(streams.isActive(STREAM_ID), is(false));
        Http2ServerStream stream = stream(streams, writer);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        streams.activate(STREAM_ID);
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority("1"), false);

        stream.run();

        assertThat(writer.hasPendingTerminalCallback(), is(true));
        assertThat(streams.isActive(STREAM_ID), is(true));

        writer.completeTerminalWrite();

        assertThat(streams.isActive(STREAM_ID), is(false));
    }

    @Test
    void rejectedStreamCompletionRestoresConnectionFlowControlForPrecheckedData() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingConnectionWriter writer = new RecordingConnectionWriter();
        List<WindowUpdate> windowUpdates = new ArrayList<>();
        Http2ServerStream stream = stream(streams, writer, windowUpdates);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority("1"), false);

        try {
            stream.run();
            assertThat(writer.hasPendingTerminalCallback(), is(true));
            stream.flowControl().inbound().decrementWindowSize(1);
            stream.enqueueDataAfterPrecheck(Http2FrameHeader.create(1,
                                                                    Http2FrameTypes.DATA,
                                                                    Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                                    STREAM_ID),
                                                BufferData.create(new byte[1]),
                                                true);
            writer.completeTerminalWrite();

            assertThat(windowUpdates, is(List.of(new WindowUpdate(0, 1))));
            assertThat(writer.rstStreamCount, is(0));
        } finally {
            if (writer.hasPendingTerminalCallback()) {
                writer.completeTerminalWrite();
            }
        }
    }

    @Test
    void blockedRejectedStreamAllowsAdvertisedWindowBeforeReset() {
        int initialWindowSize = 128 * 1024;
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingConnectionWriter writer = new RecordingConnectionWriter();
        RecordingResetTracker resetTracker = new RecordingResetTracker();
        Http2ServerStream stream = stream(streams,
                                          writer,
                                          new ArrayList<>(),
                                          sniContext(),
                                          resetTracker,
                                          List.of(),
                                          initialWindowSize);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority(String.valueOf(initialWindowSize)), false);

        stream.run();

        try {
            assertThat(writer.hasPendingTerminalCallback(), is(true));
            for (int i = 0; i < 80; i++) {
                assertDoesNotThrow(() -> writeRacingData(stream, 1024));
            }
            writer.completeTerminalWrite();

            assertThat(resetTracker.streamState.discardedData(), is(80L * 1024));
            assertThat(resetTracker.streamState.discardData(48 * 1024), is(true));
            assertThat(resetTracker.streamState.discardData(1), is(false));
        } finally {
            if (writer.hasPendingTerminalCallback()) {
                writer.completeTerminalWrite();
            }
        }
    }

    @Test
    void blockedRejectedStreamBoundsRacingDataByAdvertisedWindow() {
        int initialWindowSize = 128 * 1024;
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingConnectionWriter writer = new RecordingConnectionWriter();
        Http2ServerStream stream = stream(streams,
                                          writer,
                                          new ArrayList<>(),
                                          sniContext(),
                                          NO_OP_RESET_TRACKER,
                                          List.of(),
                                          initialWindowSize);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority(String.valueOf(initialWindowSize + 1024)), false);

        stream.run();

        try {
            assertThat(writer.hasPendingTerminalCallback(), is(true));
            for (int i = 0; i < initialWindowSize / 1024; i++) {
                assertDoesNotThrow(() -> writeRacingData(stream, 1024));
            }

            Http2Exception exception = assertThrows(Http2Exception.class, () -> writeRacingData(stream, 1024));
            assertThat(exception.code(), is(Http2ErrorCode.ENHANCE_YOUR_CALM));
        } finally {
            if (writer.hasPendingTerminalCallback()) {
                writer.completeTerminalWrite();
            }
        }
    }

    @Test
    void peerResetBeforeRejectedStreamCompletionSuppressesServerReset() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingConnectionWriter writer = new RecordingConnectionWriter();
        Http2ServerStream stream = stream(streams, writer);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority("1"), false);

        stream.run();

        assertThat(writer.hasPendingTerminalCallback(), is(true));
        boolean rapidReset = stream.rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));
        assertThat(rapidReset, is(true));
        writer.completeTerminalWrite();

        assertThat(writer.rstStreamCount, is(0));
        assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
    }

    @Test
    void rejectedStreamCompletionCannotUndercountPeerReset() throws Exception {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingConnectionWriter writer = new RecordingConnectionWriter(true);
        Http2SubProtocolSelector.SubProtocolHandler handler = new Http2SubProtocolSelector.SubProtocolHandler() {
            @Override
            public void init() {
                throw RequestException.builder()
                        .status(Status.BAD_REQUEST_400)
                        .message("Rejected request")
                        .build();
            }

            @Override
            public Http2StreamState streamState() {
                return Http2StreamState.OPEN;
            }

            @Override
            public void rstStream(Http2RstStream rstStream) {
                writer.terminalCallbackMayRun.countDown();
                try {
                    if (!writer.terminalCallbackCompleted.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for terminal callback completion");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for terminal callback completion", e);
                }
            }

            @Override
            public void windowUpdate(Http2WindowUpdate update) {
            }

            @Override
            public void data(Http2FrameHeader header, BufferData data) {
            }
        };
        Http2SubProtocolSelector selector = (_, _, _, _, _, _, _, _, _, _) -> new SubProtocolResult(true, handler);
        Http2ServerStream stream = stream(streams,
                                          writer,
                                          new ArrayList<>(),
                                          sniContext(),
                                          NO_OP_RESET_TRACKER,
                                          List.of(selector));
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        stream.prologue(PROLOGUE);
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(Http2Headers.METHOD_NAME, Method.GET.text());
        headers.add(Http2Headers.PATH_NAME, "/");
        headers.add(Http2Headers.SCHEME_NAME, "https");
        headers.add(Http2Headers.AUTHORITY_NAME, "api.example.com");
        headers.add(HeaderNames.CONTENT_LENGTH, "1");
        stream.headers(Http2Headers.create(headers), false);

        CompletableFuture<Void> streamTask = CompletableFuture.runAsync(stream);
        try {
            assertThat(writer.terminalCallbackReady.await(5, TimeUnit.SECONDS), is(true));
            boolean rapidReset = stream.rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));
            assertThat(rapidReset, is(true));
            streamTask.get(5, TimeUnit.SECONDS);
        } finally {
            writer.terminalCallbackMayRun.countDown();
        }

        assertThat(writer.rstStreamCount, is(0));
        assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
    }

    @Test
    void rejectedStreamAllowsRacingDataAfterConnectionPrecheck() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingStreamWriter writer = new RecordingStreamWriter();
        List<WindowUpdate> windowUpdates = new ArrayList<>();
        Http2ServerStream stream = stream(streams, writer, windowUpdates);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        byte[] entity = "hello".getBytes();
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority(String.valueOf(entity.length)), false);

        stream.run();

        assertDoesNotThrow(stream::checkDataReceivable);
        stream.flowControl().inbound().decrementWindowSize(entity.length);
        stream.data(Http2FrameHeader.create(entity.length,
                                            Http2FrameTypes.DATA,
                                            Http2Flag.DataFlags.create(0),
                                            STREAM_ID),
                    BufferData.create(entity),
                    false);

        assertThat(windowUpdates, is(List.of(new WindowUpdate(0, entity.length))));
    }

    @Test
    void invalidAuthoritySyntaxResetsStreamWithProtocolError() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingStreamWriter writer = new RecordingStreamWriter();
        RecordingResetTracker resetTracker = new RecordingResetTracker();
        Http2ServerStream stream = stream(streams, writer, new ArrayList<>(), parsingSniContext(), resetTracker);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        stream.prologue(PROLOGUE);
        stream.headers(headersWithAuthority("bad authority"), true);

        stream.run();
        streams.doMaintenance();

        assertThat(writer.status, is(nullValue()));
        assertThat(writer.rstStreamCodes, hasItems(Http2ErrorCode.PROTOCOL));
        assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
        assertThat(streams.get(STREAM_ID), is(nullValue()));
        assertThat(resetTracker.adds, is(1));
        assertThat(resetTracker.localCompletes, is(1));
        assertThat(resetTracker.remoteCompletes, is(1));
    }

    private static Http2ServerStream stream(Http2ConnectionStreams streams, Http2StreamWriter writer) {
        return stream(streams, writer, new ArrayList<>());
    }

    private static Http2ServerStream stream(Http2ConnectionStreams streams,
                                            Http2StreamWriter writer,
                                            SniContext sniContext) {
        return stream(streams, writer, new ArrayList<>(), sniContext);
    }

    private static Http2ServerStream stream(Http2ConnectionStreams streams,
                                            Http2StreamWriter writer,
                                            List<WindowUpdate> windowUpdates) {
        return stream(streams, writer, windowUpdates, sniContext());
    }

    private static Http2ServerStream stream(Http2ConnectionStreams streams,
                                            Http2StreamWriter writer,
                                            List<WindowUpdate> windowUpdates,
                                            SniContext sniContext) {
        return stream(streams, writer, windowUpdates, sniContext, NO_OP_RESET_TRACKER);
    }

    private static Http2ServerStream stream(Http2ConnectionStreams streams,
                                            Http2StreamWriter writer,
                                            List<WindowUpdate> windowUpdates,
                                            SniContext sniContext,
                                            Http2ServerStream.LocallyResetStreamTracker resetTracker) {
        return stream(streams, writer, windowUpdates, sniContext, resetTracker, List.of());
    }

    private static Http2ServerStream stream(Http2ConnectionStreams streams,
                                            Http2StreamWriter writer,
                                            List<WindowUpdate> windowUpdates,
                                            SniContext sniContext,
                                            Http2ServerStream.LocallyResetStreamTracker resetTracker,
                                            List<Http2SubProtocolSelector> subProtocolSelectors) {
        return stream(streams,
                      writer,
                      windowUpdates,
                      sniContext,
                      resetTracker,
                      subProtocolSelectors,
                      8192);
    }

    private static Http2ServerStream stream(Http2ConnectionStreams streams,
                                            Http2StreamWriter writer,
                                            List<WindowUpdate> windowUpdates,
                                            SniContext sniContext,
                                            Http2ServerStream.LocallyResetStreamTracker resetTracker,
                                            List<Http2SubProtocolSelector> subProtocolSelectors,
                                            int initialWindowSize) {
        Http2Config config = Http2Config.builder()
                .initialWindowSize(initialWindowSize)
                .maxFrameSize(16384)
                .build();
        ConnectionFlowControl flowControl = ConnectionFlowControl.serverBuilder((streamId, windowUpdate) ->
                        windowUpdates.add(new WindowUpdate(streamId, windowUpdate.windowSizeIncrement())))
                .initialWindowSize(config.initialWindowSize())
                .maxFrameSize(config.maxFrameSize())
                .build();
        return new Http2ServerStream(connectionContext(sniContext),
                                     streams,
                                     resetTracker,
                                     HttpRouting.empty(),
                                     config,
                                     subProtocolSelectors,
                                     STREAM_ID,
                                     Http2Settings.builder().build(),
                                     Http2Settings.builder().build(),
                                     writer,
                                     flowControl,
                                     new Http2ConnectionChecks(config, mock(Http2Connection.class)));
    }

    private static ConnectionContext connectionContext() {
        return connectionContext(sniContext());
    }

    private static ConnectionContext connectionContext(SniContext sniContext) {
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.config()).thenReturn(ListenerConfig.create());
        when(listenerContext.directHandlers()).thenReturn(DirectHandlers.create());

        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.router()).thenReturn(Router.empty());
        when(ctx.listenerContext()).thenReturn(listenerContext);
        when(ctx.sniContext()).thenReturn(Optional.of(sniContext));
        return ctx;
    }

    private static SniContext sniContext() {
        return new SniContext() {
            @Override
            public Optional<String> presentedHost() {
                return Optional.of("api.example.com");
            }

            @Override
            public Optional<String> matchedHost() {
                return Optional.of("api.example.com");
            }

            @Override
            public SniMatchType matchType() {
                return SniMatchType.EXACT;
            }

            @Override
            public AuthorityCheck checkAuthority(String authority) {
                return AuthorityCheck.ALLOWED;
            }
        };
    }

    private static SniContext parsingSniContext() {
        return new SniContext() {
            @Override
            public Optional<String> presentedHost() {
                return Optional.of("api.example.com");
            }

            @Override
            public Optional<String> matchedHost() {
                return Optional.of("api.example.com");
            }

            @Override
            public SniMatchType matchType() {
                return SniMatchType.EXACT;
            }

            @Override
            public AuthorityCheck checkAuthority(String authority) {
                UriAuthority.create(authority).host();
                return AuthorityCheck.ALLOWED;
            }
        };
    }

    private static Http2Headers headersWithoutAuthority() {
        return headersWithoutAuthority("1");
    }

    private static Http2Headers headersWithoutAuthority(String contentLength) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(Http2Headers.METHOD_NAME, Method.GET.text());
        headers.add(Http2Headers.PATH_NAME, "/");
        headers.add(Http2Headers.SCHEME_NAME, "https");
        headers.add(HeaderNames.CONTENT_LENGTH, contentLength);
        return Http2Headers.create(headers);
    }

    private static Http2Headers headersWithAuthority(String authority) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(Http2Headers.METHOD_NAME, Method.GET.text());
        headers.add(Http2Headers.PATH_NAME, "/");
        headers.add(Http2Headers.SCHEME_NAME, "https");
        headers.add(Http2Headers.AUTHORITY_NAME, authority);
        return Http2Headers.create(headers);
    }

    private static void writeRacingData(Http2ServerStream stream, int length) {
        byte[] entity = new byte[length];
        stream.checkDataReceivable();
        stream.flowControl().inbound().decrementWindowSize(entity.length);
        stream.data(Http2FrameHeader.create(entity.length,
                                            Http2FrameTypes.DATA,
                                            Http2Flag.DataFlags.create(0),
                                            STREAM_ID),
                    BufferData.create(entity),
                    false);
    }

    private record WindowUpdate(int streamId, int increment) {
    }

    private static final class NoOpResetTracker implements Http2ServerStream.LocallyResetStreamTracker {
        @Override
        public void add(int streamId, Http2ServerStream.LocallyResetStreamState streamState) {
        }

        @Override
        public void localComplete(int streamId) {
        }

        @Override
        public void remoteComplete(int streamId) {
        }
    }

    private static final class RecordingResetTracker implements Http2ServerStream.LocallyResetStreamTracker {
        private int adds;
        private int localCompletes;
        private int remoteCompletes;
        private Http2ServerStream.LocallyResetStreamState streamState;

        @Override
        public void add(int streamId, Http2ServerStream.LocallyResetStreamState streamState) {
            adds++;
            this.streamState = streamState;
        }

        @Override
        public void localComplete(int streamId) {
            localCompletes++;
        }

        @Override
        public void remoteComplete(int streamId) {
            remoteCompletes++;
        }
    }

    private static final class RecordingStreamWriter implements Http2StreamWriter {
        private Status status;
        private int rstStreamCount;
        private final List<Http2ErrorCode> rstStreamCodes = new ArrayList<>();

        @Override
        public void write(Http2FrameData frame) {
            if (frame.header().type() == Http2FrameTypes.RST_STREAM.type()) {
                rstStreamCount++;
                rstStreamCodes.add(Http2RstStream.create(frame.data()).errorCode());
            }
        }

        @Override
        public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
        }

        @Override
        public int writeHeaders(Http2Headers headers,
                                int streamId,
                                Http2Flag.HeaderFlags flags,
                                FlowControl.Outbound flowControl) {
            this.status = headers.status();
            return 0;
        }

        @Override
        public int writeHeaders(Http2Headers headers,
                                int streamId,
                                Http2Flag.HeaderFlags flags,
                                Http2FrameData dataFrame,
                                FlowControl.Outbound flowControl) {
            this.status = headers.status();
            return 0;
        }
    }

    private static final class RecordingConnectionWriter extends Http2ConnectionWriter {
        private final boolean blockTerminalCallback;
        private final CountDownLatch terminalCallbackReady = new CountDownLatch(1);
        private final CountDownLatch terminalCallbackMayRun = new CountDownLatch(1);
        private final CountDownLatch terminalCallbackCompleted = new CountDownLatch(1);
        private Runnable beforeResetWrite = () -> { };
        private Runnable terminalCallback;
        private int rstStreamCount;

        private RecordingConnectionWriter() {
            this(false);
        }

        private RecordingConnectionWriter(boolean blockTerminalCallback) {
            super(mock(SocketContext.class), mock(DataWriter.class), List.of());
            this.blockTerminalCallback = blockTerminalCallback;
        }

        @Override
        public void write(Http2FrameData frame) {
            if (frame.header().type() == Http2FrameTypes.RST_STREAM.type()) {
                beforeResetWrite.run();
                rstStreamCount++;
            }
        }

        @Override
        public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
        }

        @Override
        public int writeData(Http2FrameData frame,
                             FlowControl.Outbound flowControl,
                             Runnable onEndStreamFrameWritten) {
            captureTerminalCallback(onEndStreamFrameWritten);
            return 0;
        }

        @Override
        public int writeHeaders(Http2Headers headers,
                                int streamId,
                                Http2Flag.HeaderFlags flags,
                                FlowControl.Outbound flowControl) {
            return 0;
        }

        @Override
        public int writeHeaders(Http2Headers headers,
                                int streamId,
                                Http2Flag.HeaderFlags flags,
                                FlowControl.Outbound flowControl,
                                Runnable onEndStreamFrameWritten) {
            if (flags.endOfStream()) {
                captureTerminalCallback(onEndStreamFrameWritten);
            }
            return 0;
        }

        @Override
        public int writeHeaders(Http2Headers headers,
                                int streamId,
                                Http2Flag.HeaderFlags flags,
                                Http2FrameData dataFrame,
                                FlowControl.Outbound flowControl) {
            return 0;
        }

        @Override
        public int writeHeaders(Http2Headers headers,
                                int streamId,
                                Http2Flag.HeaderFlags flags,
                                Http2FrameData dataFrame,
                                FlowControl.Outbound flowControl,
                                Runnable onEndStreamFrameWritten) {
            captureTerminalCallback(onEndStreamFrameWritten);
            return 0;
        }

        private void captureTerminalCallback(Runnable callback) {
            terminalCallback = callback;
            if (!blockTerminalCallback) {
                return;
            }
            terminalCallbackReady.countDown();
            try {
                if (!terminalCallbackMayRun.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to run terminal callback");
                }
                completeTerminalWrite();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to run terminal callback", e);
            } finally {
                terminalCallbackCompleted.countDown();
            }
        }

        private void completeTerminalWrite() {
            assertThat(terminalCallback, is(org.hamcrest.Matchers.notNullValue()));
            terminalCallback.run();
            terminalCallback = null;
        }

        private boolean hasPendingTerminalCallback() {
            return terminalCallback != null;
        }
    }
}
